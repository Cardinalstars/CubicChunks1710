/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks;

import static com.cardinalstar.cubicchunks.util.ReflectionUtil.cast;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cardinalstar.cubicchunks.api.world.storage.ICubicStorage;
import com.cardinalstar.cubicchunks.api.world.storage.StorageFormatProviderBase;
import com.cardinalstar.cubicchunks.api.worldgen.VanillaCompatibilityGeneratorProviderBase;
import com.cardinalstar.cubicchunks.event.handlers.ClientEventHandler;
import com.cardinalstar.cubicchunks.event.handlers.CommonEventHandler;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldSettings;
import com.cardinalstar.cubicchunks.mixin.early.common.IIntegratedServer;
import com.cardinalstar.cubicchunks.network.NetworkChannel;
import com.cardinalstar.cubicchunks.server.chunkio.RegionCubeStorage;
import com.cardinalstar.cubicchunks.util.CompatHandler;
import com.cardinalstar.cubicchunks.util.SideUtils;
import com.cardinalstar.cubicchunks.world.type.VanillaCubicWorldType;
import com.cardinalstar.cubicchunks.worldgen.VanillaCompatibilityGenerator;
import com.cardinalstar.cubicchunks.worldgen.WorldgenHangWatchdog;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ICrashCallable;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.NetworkModHolder;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.InvalidVersionSpecificationException;
import cpw.mods.fml.common.versioning.VersionRange;
import cpw.mods.fml.relauncher.Side;

@ParametersAreNonnullByDefault
@Mod(modid = CubicChunks.MODID, useMetadata = true)
public class CubicChunks {

    public static final int MAX_RENDER_DISTANCE = 64;
    public static final VersionRange SUPPORTED_SERVER_VERSIONS;
    public static final VersionRange SUPPORTED_CLIENT_VERSIONS;

    static {
        try {
            // Versions newer than current will be only checked on the other side
            // (I know this can be hard to actually fully understand)
            SUPPORTED_SERVER_VERSIONS = VersionRange.createFromVersionSpec("[1.12.2-0.0.887.0,)");
            SUPPORTED_CLIENT_VERSIONS = VersionRange.createFromVersionSpec("[1.12.2-0.0.887.0,)");
        } catch (InvalidVersionSpecificationException e) {
            throw new Error(e);
        }
    }

    public static final int MIN_SUPPORTED_BLOCK_Y = Integer.MIN_VALUE + 4096;
    public static final int MAX_SUPPORTED_BLOCK_Y = Integer.MAX_VALUE - 4095;

    public static final boolean DEBUG_ENABLED = System.getProperty("cubicchunks.debug", "false")
        .equalsIgnoreCase("true");
    public static final String MODID = "cubicchunks";

    @Nonnull
    public static Logger LOGGER = LogManager.getLogger("EarlyCubicChunks");// use some logger even before it's set.
                                                                           // useful for unit tests

    private static final String MOD_VERSION;

    static {
        String implementationVersion = CubicChunks.class.getPackage()
            .getImplementationVersion();
        if (implementationVersion == null) {
            LOGGER.error("No implementation version! If this is dev environment, this is normal");
            implementationVersion = "9999.9999.9999.9";
        }
        MOD_VERSION = implementationVersion;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        LOGGER = e.getModLog();

        registerVanillaCompatibilityGeneratorProvider();
        registerAnvil3dStorageFormatProvider();
        try {
            ConfigurationManager.registerConfig(CubicChunksConfig.class);
        } catch (ConfigException ex) {
            throw new RuntimeException(ex);
        }
        FMLCommonHandler.instance()
            .registerCrashCallable(new ICrashCallable() {

                @Override
                public String getLabel() {
                    return "CubicChunks WorldGen Hang Watchdog samples";
                }

                @Override
                public String call() throws Exception {
                    String message = WorldgenHangWatchdog.getCrashInfo();
                    if (message == null) {
                        return "(no data)";
                    }
                    return message;
                }
            });
        VanillaCubicWorldType.create();
        LOGGER.debug("Registered world types");

        // we have to redo the check for network compatibility because it depends on config
        // and config is done after forge does the check
        NetworkModHolder holder = NetworkRegistry.INSTANCE.registry()
            .get(
                Loader.instance()
                    .activeModContainer());
        holder.testVanillaAcceptance();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        CommonEventHandler eventHandler = new CommonEventHandler();
        MinecraftForge.EVENT_BUS.register(eventHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(eventHandler);
        SideUtils.runForClient(() -> () -> {
            ClientEventHandler clientEventHandler = new ClientEventHandler();
            MinecraftForge.EVENT_BUS.register(clientEventHandler);
            FMLCommonHandler.instance()
                .bus()
                .register(clientEventHandler);
        });
        NetworkChannel.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        CompatHandler.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent evt) {
        CubicChunksConfig.registerCommands(evt);
    }

    @Mod.EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        SideUtils.runForSide(() -> () -> {
            IIntegratedServer integratedServer = cast(event.getServer());
            ICubicWorldSettings settings = cast(integratedServer.getWorldSettings());
            event.getServer()
                .setBuildLimit(CubicChunks.MAX_SUPPORTED_BLOCK_Y);
        }, () -> () -> {
            // no-op, done by mixin
        });
    }

    @SubscribeEvent
    public static void registerVanillaCompatibilityGeneratorProvider() {
        VanillaCompatibilityGeneratorProviderBase.REGISTRY.register(
            VanillaCompatibilityGeneratorProviderBase.DEFAULT,
            new VanillaCompatibilityGeneratorProviderBase() {

                @Override
                public VanillaCompatibilityGenerator provideGenerator(IChunkProvider vanillaChunkGenerator,
                    World world) {
                    return new VanillaCompatibilityGenerator(vanillaChunkGenerator, world);
                }
            }.setRegistryName(VanillaCompatibilityGeneratorProviderBase.DEFAULT)
                .setUnlocalizedName("cubicchunks.gui.worldmenu.cc_default"));
    }

    @SubscribeEvent
    public static void registerAnvil3dStorageFormatProvider() {
        StorageFormatProviderBase.REGISTRY.register(StorageFormatProviderBase.DEFAULT, new StorageFormatProviderBase() {

            @Override
            public ICubicStorage provideStorage(World world, Path path) throws IOException {
                return new RegionCubeStorage(path);
            }
        }.setRegistryName(StorageFormatProviderBase.DEFAULT)
            .setUnlocalizedName("cubicchunks.gui.storagefmt.anvil3d"));
    }

    @NetworkCheckHandler
    public static boolean checkCanConnectWithMods(Map<String, String> modVersions, Side remoteSide) {
        String remoteFullVersion = modVersions.get(MODID);
        if (remoteFullVersion == null) {
            if (remoteSide.isClient()) {
                return CubicChunksConfig.allowVanillaClients; // don't allow client without CC to connect
            }
            return true; // allow connecting to server without CC
        }
        if (!checkVersionFormat(MOD_VERSION, remoteSide.isClient() ? Side.SERVER : Side.CLIENT)) {
            return true;
        }
        if (!checkVersionFormat(remoteFullVersion, remoteSide)) {
            return true;
        }

        ArtifactVersion version = new DefaultArtifactVersion(remoteFullVersion);
        ArtifactVersion currentVersion = new DefaultArtifactVersion(MOD_VERSION);
        if (currentVersion.compareTo(version) < 0) {
            return true; // allow connection if this version is older, let newer one decide
        }
        return (remoteSide.isClient() ? SUPPORTED_CLIENT_VERSIONS : SUPPORTED_SERVER_VERSIONS).containsVersion(version);
    }

    // returns true if version format is known. Side can be null if not logging connection attempt
    private static boolean checkVersionFormat(String version, @Nullable Side remoteSide) {
        int mcVersionSplit = version.indexOf('-');
        if (mcVersionSplit < 0) {
            LOGGER.warn(
                "Connection attempt with unexpected " + remoteSide
                    + " version string: "
                    + version
                    + ". Cannot split into MC "
                    + "version and mod version. Assuming dev environment or special/unknown version, connection will be allowed.");
            return false;
        }

        String modVersion = version.substring(mcVersionSplit + 1);

        if (modVersion.isEmpty()) {
            LOGGER.warn(
                "Connection attempt with unexpected " + remoteSide
                    + " version string: "
                    + version
                    + ". Mod version part not "
                    + "found. Assuming dev environment or special/unknown version,, connection will be allowed");
            return false;
        }

        final String versionRegex = "\\d+\\." + "\\d+\\." + "\\d+\\." + "\\d+" + "(-.+)?";// "MAJORMOD.MAJORAPI.MINOR.PATCH(-final/rcX/betaX)"

        if (!modVersion.matches(versionRegex)) {
            LOGGER.warn(
                "Connection attempt with unexpected " + remoteSide
                    + " version string: "
                    + version
                    + ". Mod version part ("
                    + modVersion
                    + ") does not match expected format ('MAJORMOD.MAJORAPI.MINOR.PATCH(-optionalText)'). Assuming dev "
                    + "environment or special/unknown version, connection will be allowed");
            return false;
        }
        return true;
    }

    // essentially a copy of FMLLog.bigWarning, with more lines of stacktrace
    public static void bigWarning(String format, Object... data) {
        StackTraceElement[] trace = Thread.currentThread()
            .getStackTrace();
        LOGGER.log(Level.WARN, "****************************************");
        LOGGER.log(Level.WARN, "* " + format, data);
        for (int i = 2; i < 10 && i < trace.length; i++) {
            LOGGER.log(Level.WARN, "*  at {}{}", trace[i].toString(), i == 9 ? "..." : "");
        }
        LOGGER.log(Level.WARN, "****************************************");
    }

    public static boolean hasOptifine() {
        return SideUtils.getForSide(
            () -> () -> FMLClientHandler.instance()
                .hasOptifine(),
            () -> () -> false);
    }
}
