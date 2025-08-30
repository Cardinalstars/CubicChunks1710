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
package com.cardinalstar.cubicchunks.asm.coremod;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraftforge.common.ForgeVersion;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IEnvironmentTokenProvider;

import com.cardinalstar.cubicchunks.mixin.Mixins;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

// this needs to be in separate package because the package with the coremod is added to transformer exclusions
// and we need mixins to still be transformed for runtime deobfuscation
@ParametersAreNonnullByDefault
// the mcVersion value is inlined at compile time, so this MC version check may still fail
@IFMLLoadingPlugin.SortingIndex(value = 5000)
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class CubicChunksCoreMod implements IFMLLoadingPlugin, IEarlyMixinLoader {

    public static final class TokenProvider implements IEnvironmentTokenProvider {

        @Override
        public int getPriority() {
            return IEnvironmentTokenProvider.DEFAULT_PRIORITY;
        }

        @Override
        public Integer getToken(String token, MixinEnvironment env) {
            if ("FORGE".equals(token)) {
                return Integer.valueOf(ForgeVersion.getBuildVersion());
            } else if ("FML".equals(token)) {
                String fmlVersion = Loader.instance()
                    .getFMLVersionString();
                int build = Integer.parseInt(fmlVersion.substring(fmlVersion.lastIndexOf('.') + 1));
                return Integer.valueOf(build);
            } else if ("MC_FORGE".equals(token)) {
                return ForgeVersion.minorVersion;
            }
            return null;
        }

    }

    public CubicChunksCoreMod() {
        // initMixin();
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.cardinalstar.cubicchunks.asm.transformer.AddEmptyConstructorToChunk" };
    }

    @Nullable
    @Override
    public String getModContainerClass() {
        return "com.cardinalstar.cubicchunks.asm.CubicChunksCoreContainer";
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Nullable
    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    // public static void initMixin() {
    // MixinBootstrap.init();
    // Mixins.addConfiguration("cubicchunks.mixins.core.json");
    // Mixins.addConfiguration("cubicchunks.mixins.core_sided.vanilla.json");
    // Mixins.addConfiguration("cubicchunks.mixins.fixes.json");
    // Mixins.addConfiguration("cubicchunks.mixins.selectable.json");
    // Mixins.addConfiguration("cubicchunks.mixins.noncritical.json");
    // MixinEnvironment.getDefaultEnvironment().registerTokenProviderClass(
    // "io.github.opencubicchunks.cubicchunks.core.asm.coremod.CubicChunksCoreMod$TokenProvider");
    // }

    @Override
    public String getMixinConfig() {
        return "mixins.cubicchunks.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return IMixins.getEarlyMixins(Mixins.class, loadedCoreMods);
    }
}
