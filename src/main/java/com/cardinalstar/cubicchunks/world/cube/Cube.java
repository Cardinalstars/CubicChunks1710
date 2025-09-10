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
package com.cardinalstar.cubicchunks.world.cube;

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;
import static com.cardinalstar.cubicchunks.util.Coords.blockToLocal;
import static com.cardinalstar.cubicchunks.util.Coords.cubeToMaxBlock;
import static com.cardinalstar.cubicchunks.util.Coords.cubeToMinBlock;
import static com.cardinalstar.cubicchunks.util.Coords.localToBlock;
import static net.minecraftforge.common.MinecraftForge.EVENT_BUS;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ReportedException;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.world.ChunkEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.api.worldgen.ICubeGenerator;
import com.cardinalstar.cubicchunks.event.events.CubeEvent;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.server.CubeWatcher;
import com.cardinalstar.cubicchunks.server.SpawnCubes;
import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.util.CompatHandler;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.TicketList;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.core.ICubicTicketInternal;
import com.cardinalstar.cubicchunks.world.cube.blockview.IBlockView;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;

/**
 * A cube is our extension of minecraft's chunk system to three dimensions. Each cube encloses a cubic area in the world
 * with a side length of {@link Cube#SIZE}, aligned to multiples of that length and stored within columns.
 */
@ParametersAreNonnullByDefault
public class Cube implements ICube {

    @Nullable
    protected static final ExtendedBlockStorage NULL_STORAGE = null;

    /**
     * A 16x16x16 mapping of the block biome array.
     */
    @Nullable
    private byte[] blockBiomeArray = null;

    @Nonnull
    private final TicketList tickets; // tickets prevent this Cube from being unloaded
    /**
     * Has anything within the cube changed since it was loaded from disk?
     */
    private boolean isModified = false;

    /**
     * Has the cube generator's populate() method been called for this cube?
     */
    private boolean isPopulated = false;
    /**
     * Has the cube generator's populate() method been called for every cube potentially writing to this cube during
     * population?
     */
    private boolean isFullyPopulated = false;
    /**
     * Has the initial light map been calculated?
     */
    private boolean isInitialLightingDone = false;
    /**
     * The world of this cube
     */
    @Nonnull
    private final World world;
    /**
     * Whether the cube has entities in it
     */
    public boolean hasEntities;
    /**
     * Last time the cube was saved.
     */
    public long lastSaveTime;
    /**
     * The column of this cube
     */
    @Nonnull
    private final Chunk column;
    /**
     * The position of this cube, in cube space
     */
    @Nonnull
    private final CubePos coords;
    /**
     * Blocks in this cube
     */
    @Nullable
    private ExtendedBlockStorage storage;
    /**
     * Entities in this cube
     */
    @Nonnull
    public List<Entity> entities;
    /**
     * The position of tile entities in this cube, and their corresponding tile entity
     */
    @Nonnull
    public Map<net.minecraft.world.ChunkPosition, net.minecraft.tileentity.TileEntity> cubeTileEntityMap;

    /**
     * The positions of tile entities queued for creation
     */
    @Nonnull
    private final ConcurrentLinkedQueue<BlockPos> tileEntityPosQueue;

    private final ICubeLightTrackingInfo cubeLightData;

    /**
     * Is this cube loaded and not queued for unload
     */
    private boolean isCubeLoaded;

    /**
     * Contains the current Linear Congruential Generator seed for block updates. Used with an A value of 3 and a C
     * value of 0x3c6ef35f, producing a highly planar series of values ill-suited for choosing random blocks in a
     * 16x16x16 field.
     */
    protected int updateLCG = (new Random()).nextInt();

    /**
     * True only if all the blocks have been added to server height map. Always true clientside.
     */
    private boolean isSurfaceTracked = false;
    private boolean ticked = false;

    /**
     * This is used as an optimization to avoid putting all the cubes to tick in a set (which turns out to be very slow
     * because of huge amount of
     * cubes), or iterating over all loaded cubes, which can also be very expensive in case of very big render distance,
     * where not many cubes are
     * actually ticked, but hundreds of thousands of them can be loaded.
     * <p>
     * Instead, cubes for players are added into a simple arraylist, and forced cubes are iterated separately, and
     * double-ticking is avoided by
     * checking this lastTicked field instead of deduplication by putting them all into a Set.
     */
    private long lastTicked = Long.MIN_VALUE;

    // private final CapabilityDispatcher capabilities;

    /**
     * Create a new cube in the specified column at the specified location. The newly created cube will only contain air
     * blocks.
     *
     * @param column column of this cube
     * @param cubeY  cube y position
     */
    public Cube(Chunk column, int cubeY) {
        this.world = column.worldObj;
        this.column = column;
        this.coords = new CubePos(column.xPosition, cubeY, column.zPosition);

        this.tickets = new TicketList(this); // TODO

        this.entities = new ArrayList<Entity>();

        this.cubeTileEntityMap = new HashMap<>();
        this.tileEntityPosQueue = new ConcurrentLinkedQueue<>();

        this.cubeLightData = ((ICubicWorldInternal) world).getLightingManager()
            .createLightData(this);

        this.storage = NULL_STORAGE;
    }

    /**
     * Create a new cube at the specified location by copying blocks from a cube primer.
     *
     * @param column column of this cube
     * @param cubeY  cube y position
     * @param blocks primer containing the blocks for this cube
     */
    @SuppressWarnings("deprecation") // when a block is generated, does it really have any extra
    // information it could give us about its opacity by knowing its location?
    public Cube(Chunk column, int cubeY, Block[] blocks) {
        this(column, cubeY);

        boolean flag = !world.provider.hasNoSky;

        for (int y = Cube.SIZE - 1; y >= 0; y--) {
            for (int z = 0; z < Cube.SIZE; z++) {
                for (int x = 0; x < Cube.SIZE; x++) {
                    Block block = blocks[x << 11 | z << 7 | y];

                    if (block != null && block.getMaterial() != Material.air) {
                        if (this.storage == null) {
                            this.storage = new ExtendedBlockStorage(cubeToMinBlock(cubeY), flag);
                        }
                        this.storage.func_150818_a(x, y, z, block);
                    }
                }
            }
        }
    }

    /**
     * Constructor to be used from subclasses to provide all field values
     *
     */
    public Cube(Chunk column, int cubeY, IBlockView blockSource) {
        this(column, cubeY);

        boolean hasNoSky = !world.provider.hasNoSky;

        for (int x = 0; x < Cube.SIZE; x++) {
            for (int y = 0; y < Cube.SIZE; y++) {
                for (int z = 0; z < Cube.SIZE; z++) {
                    Block block = blockSource.getBlock(x, y, z);

                    if (block != Blocks.air) {
                        if (this.storage == null) {
                            this.storage = new ExtendedBlockStorage(cubeToMinBlock(cubeY), hasNoSky);
                        }

                        this.storage.func_150818_a(x, y, z, block);
                        this.storage.setExtBlockMetadata(x, y, z, blockSource.getBlockMetadata(x, y, z));
                    }
                }
            }
        }
    }

    /**
     * Constructor to be used from subclasses to provide all field values
     *
     * @param tickets            cube ticket list
     * @param world              the world instance
     * @param column             the column this cube belongs to
     * @param coords             position of this cube
     * @param storage            block storage
     * @param entities           entity container
     * @param tileEntityMap      tile entity storage
     * @param tileEntityPosQueue queue for updating tile entities
     * @param cubeLightData      cube light tracking data
     */
    protected Cube(TicketList tickets, World world, Chunk column, CubePos coords, ExtendedBlockStorage storage,
        List<Entity> entities,
        Map<net.minecraft.world.ChunkPosition, net.minecraft.tileentity.TileEntity> tileEntityMap,
        ConcurrentLinkedQueue<BlockPos> tileEntityPosQueue, ICubeLightTrackingInfo cubeLightData) {
        this.tickets = tickets;
        this.world = world;
        this.column = column;
        this.coords = coords;
        this.storage = storage;
        this.entities = entities;
        this.cubeTileEntityMap = tileEntityMap;
        this.tileEntityPosQueue = tileEntityPosQueue;
        this.cubeLightData = cubeLightData;
    }

    // ======================================
    // ========Chunk vanilla methods=========
    // ======================================

    /**
     * Returns the ExtendedBlockStorage array for this Chunk.
     */
    public ExtendedBlockStorage getBlockStorageArray() {
        return this.storage;
    }

    @Override
    public int getSavedLightValue(EnumSkyBlock lightType, int x, int y, int z) {
        ((ICubicWorldInternal) world).getLightingManager()
            .onGetLight(lightType, x, y, z);
        return getCachedLightFor(lightType, x, y, z);
    }

    public int getCachedLightFor(EnumSkyBlock type, int xPos, int yPos, int zPos) {
        int x = blockToLocal(xPos);
        int y = blockToLocal(yPos);
        int z = blockToLocal(zPos);
        ExtendedBlockStorage storage = this.storage;

        if (type == EnumSkyBlock.Sky) {
            if (this.world.provider.hasNoSky) {
                return 0;
            }
            if (storage == null) {
                return yPos > ((IColumnInternal) column).getTopYWithStaging(x, z) ? type.defaultLightValue : 0;
            }
            return storage.getExtSkylightValue(x, y, z);
        } else {
            if (storage == null) {
                return 0;
            }
            return storage.getExtBlocklightValue(x, y, z);
        }
    }

    @Override
    public void setLightFor(EnumSkyBlock lightType, int x, int y, int z, int light) {
        column.setLightValue(lightType, Coords.blockToLocal(x), y, Coords.blockToLocal(z), light);
    }

    /**
     * Create a tile entity at the given position if the block is able to hold one
     *
     * @param x x position where the tile entity should be placed
     * @param y y position where the tile entity should be placed
     * @param z zPosition position where the tile entity should be placed
     * @return the created tile entity, or <code>null</code> if the block at that position does not provide tile
     *         entities
     */
    @Override
    @Nullable
    public TileEntity getBlockTileEntityInChunk(int x, int y, int z) {
        Block block = this.getBlock(x, y, z);
        int meta = this.getBlockMetadata(x, y, z);

        if (block.hasTileEntity(meta)) {
            return block.createTileEntity(this.world, meta);
        }
        return null;
    }

    /**
     * Returns the block corresponding to the given coordinates inside a cube.
     */
    @Override
    public Block getBlock(int x, int y, int z) {
        Block block = Blocks.air;

        if (storage != null) {
            try {
                block = storage.getBlockByExtId(blockToLocal(x), blockToLocal(y), blockToLocal(z));
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Getting block");
                CrashReportCategory crashreportcategory = crashreport.makeCategory("Block being got");
                crashreportcategory.addCrashSectionCallable("Location", new Callable() {

                    public String call() {
                        return CrashReportCategory.getLocationInfo(x, y, z);
                    }
                });
                throw new ReportedException(crashreport);
            }
        }
        return block;
    }

    /**
     * Return the metadata corresponding to the given coordinates inside a cube.
     */
    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return storage != null ? storage.getExtBlockMetadata(blockToLocal(x), blockToLocal(y), blockToLocal(z)) : 0;
    }

    @Override
    @Nullable
    public TileEntity getTileEntityUnsafe(int x, int y, int z) { // TODO WATCH
        return column.getTileEntityUnsafe(x, y, z);
    }

    // have a copy of addTileEntity in Cube because sometimes some mods will access blocks from outside of
    // the cube being loaded while loading it's tile entity, causing a StackOverflowError when the cube set at
    // the start of loading TEs in column gets changed.
    @Override
    public void addTileEntity(TileEntity tileEntityIn) {
        int i = tileEntityIn.xCoord - this.coords.getX() * 16;
        int j = tileEntityIn.yCoord - this.coords.getY() * 16;
        int k = tileEntityIn.zCoord - this.coords.getZ() * 16;
        this.setBlockTileEntityInChunk(i, j, k, tileEntityIn);
        if (this.isCubeLoaded) {
            this.world.addTileEntity(tileEntityIn);
        }
    }

    public void setBlockTileEntityInChunk(int x, int y, int z, TileEntity tileEntityIn) {
        tileEntityIn.setWorldObj(this.world);
        tileEntityIn.xCoord = this.coords.getX() * 16 + x;
        tileEntityIn.yCoord = this.coords.getY() * 16 + y;
        tileEntityIn.zCoord = this.coords.getZ() * 16 + z;

        int metadata = getBlockMetadata(x, y, z);
        if (this.getBlock(x, y, z)
            .hasTileEntity(metadata)) {
            ChunkPosition chunkposition = new ChunkPosition(x, tileEntityIn.yCoord , z);

            TileEntity existing = this.cubeTileEntityMap.remove(chunkposition);

            if (existing != null) existing.invalidate();

            tileEntityIn.validate();
            this.cubeTileEntityMap.put(chunkposition, tileEntityIn);
        }
    }

    /**
     * Update light and tile entities of cube
     *
     * @param tryToTickFaster Whether costly calculations should be skipped in order to catch up with ticks
     */
    public void tickCubeCommon(BooleanSupplier tryToTickFaster) {
        this.ticked = true;
        while (!this.tileEntityPosQueue.isEmpty()) {
            BlockPos blockpos = this.tileEntityPosQueue.poll();
            int x = blockpos.getX();
            int y = blockpos.getY();
            int z = blockpos.getZ();
            Block block = this.getBlock(x, y, z);
            int meta = this.getBlockMetadata(x, y, z);

            if (this.getTileEntityUnsafe(x, y, z) == null && block.hasTileEntity(meta)) {
                TileEntity tileentity = this.getBlockTileEntityInChunk(x, y, z);
                this.world.setTileEntity(x, y, z, tileentity);
                this.world.markBlockRangeForRenderUpdate(x, y, z, x, y, z);
            }
        }
    }

    /**
     * Tick this cube on server side. Block tick updates launched here.
     *
     * @param tryToTickFaster - returns true when running out of reserved tick time
     * @param rand            - World specific Random
     */
    public void tickCubeServer(BooleanSupplier tryToTickFaster, Random rand) {
        if (!isFullyPopulated) {
            return;
        }

        tickCubeCommon(tryToTickFaster);
    }

    /**
     * @return biome or null if {@link #blockBiomeArray} is not generated by
     *         cube generator. Input coordinates are in cube coordinate space.
     */
    @Override
    public BiomeGenBase getBiome(int biomeX, int biomeY, int biomeZ) {
        if (this.blockBiomeArray == null) return this.getColumn()
            .getBiomeGenForWorldCoords(biomeX, biomeY, world.provider.worldChunkMgr); // TODO
        int biomeId = this.blockBiomeArray[AddressTools.getBiomeAddress3d(biomeX, biomeY, biomeZ)] & 255;
        return BiomeGenBase.getBiome(biomeId);
    }

    @Override
    public void setBiome(int localBiomeX, int localBiomeY, int localBiomeZ, BiomeGenBase biome) {
        if (this.blockBiomeArray == null) this.blockBiomeArray = new byte[16 * 16 * 16];

        this.blockBiomeArray[AddressTools
            .getBiomeAddress3d(localBiomeX, localBiomeY, localBiomeZ)] = (byte) biome.biomeID;
    }

    @Nullable
    public byte[] getBiomeArray() {
        return this.blockBiomeArray;
    }

    public void setBiomeArray(byte[] biomeArray) {
        if (this.blockBiomeArray == null) this.blockBiomeArray = biomeArray;
        if (this.blockBiomeArray.length != biomeArray.length) {
            CubicChunks.LOGGER.warn(
                "Could not set level cube biomes, array length is {} instead of {}",
                Integer.valueOf(biomeArray.length),
                Integer.valueOf(this.blockBiomeArray.length));
        } else {
            System.arraycopy(biomeArray, 0, this.blockBiomeArray, 0, this.blockBiomeArray.length);
        }
    }
    // =================================
    // =========Other methods===========
    // =================================

    @Override
    public boolean isEmpty() {
        return storage == null || this.storage.isEmpty();
    }

    @Override
    public BlockPos localAddressToBlockPos(int localAddress) {
        int x = localToBlock(this.coords.getX(), AddressTools.getLocalX(localAddress));
        int y = localToBlock(this.coords.getY(), AddressTools.getLocalY(localAddress));
        int z = localToBlock(this.coords.getZ(), AddressTools.getLocalZ(localAddress));
        return new BlockPos(x, y, z);
    }

    @SuppressWarnings("unchecked")
    public <T extends World & ICubicWorld> T getWorld() {
        return (T) this.world;
    }

    @SuppressWarnings({ "unchecked", "deprecation", "RedundantSuppression" })
    @Override
    public <T extends Chunk & IColumn> T getColumn() {
        return (T) this.column;
    }

    @Override
    public int getX() {
        return this.coords.getX();
    }

    @Override
    public int getY() {
        return this.coords.getY();
    }

    @Override
    public int getZ() {
        return this.coords.getZ();
    }

    @Override
    public CubePos getCoords() {
        return this.coords;
    }

    @Override
    public boolean containsCoordinate(int x, int y, int z) {
        return this.coords.getX() == blockToCube(x) && this.coords.getY() == blockToCube(y)
            && this.coords.getZ() == blockToCube(z);
    }

    @Override
    @Nullable
    public ExtendedBlockStorage getStorage() {
        return this.storage;
    }

    @Nullable
    public ExtendedBlockStorage setStorage(@Nullable ExtendedBlockStorage ebs) {
        this.isModified = true;
        this.storage = ebs;
        if (ebs != null) {
            ((ICubicWorldInternal) world).getLightingManager()
                .onCreateCubeStorage(this, ebs);
        }

        putEBSInChunk();

        return ebs;
    }

    public ExtendedBlockStorage getOrCreateStorage() {
        if (this.storage == null) {
            setStorage(new ExtendedBlockStorage(getY(), !column.worldObj.provider.hasNoSky));
        }

        return storage;
    }

    public void setStorageFromSave(@Nullable ExtendedBlockStorage ebs) {
        this.storage = ebs;

        putEBSInChunk();
    }

    public void putEBSInChunk() {
        ExtendedBlockStorage[] chunkEBS = column.getBlockStorageArray();

        // Store the EBS in the chunk's EBS array for compat with mods that access it directly
        if (getY() >= 0 && getY() < chunkEBS.length) {
            chunkEBS[getY()] = this.storage;
        }
    }

    @Override
    public Map<ChunkPosition, TileEntity> getTileEntityMap() {
        return this.cubeTileEntityMap;
    }

    @Override
    public List<Entity> getEntitySet() {
        return this.entities;
    }

    @Override
    public void addEntity(Entity entity) {
        this.hasEntities = true;
        this.entities.add(entity);
    }

    @Override
    public boolean removeEntity(Entity entity) {
        return this.entities.remove(entity);
    }

    public List<Entity> getEntityContainer() {
        return this.entities;
    }

    /**
     * Returns true if the cube still needs to be ticked this tick.
     *
     * @param totalTime world time
     * @return true if cube still needs to be ticked at this world time
     */
    public boolean checkAndUpdateTick(long totalTime) {
        boolean ret = totalTime != this.lastTicked;
        this.lastTicked = totalTime;
        return ret;
    }

    /**
     * Finish the cube loading process
     */
    public void onCubeLoad() {
        if (isCubeLoaded) {
            CubicChunks.LOGGER.error("Attempting to load already loaded cube at " + this.getCoords());
            return;
        }
        isCubeLoaded = true;

        // tell the world about tile entities
        this.world.func_147448_a(this.cubeTileEntityMap.values());
        this.world.addLoadedEntities(this.entities);

        if (!isSurfaceTracked) {
            ((IColumnInternal) getColumn()).addToStagingHeightmap(this);
        }
        ((ICubicWorldInternal) world).getLightingManager()
            .onCubeLoad(this);
        CompatHandler.onCubeLoad(new ChunkEvent.Load(getColumn()));
        EVENT_BUS.post(new CubeEvent.Load(this));
    }

    @SuppressWarnings("deprecation")
    public void trackSurface() {
        IHeightMap opindex = ((IColumn) column).getOpacityIndex();
        int miny = getCoords().getMinBlockY();

        for (int x = 0; x < Cube.SIZE; x++) {
            for (int z = 0; z < Cube.SIZE; z++) {

                for (int y = Cube.SIZE - 1; y >= 0; y--) {
                    Block newBlock = this.getBlock(x, y, z);

                    column.setChunkModified(); // TODO: maybe ServerHeightMap needs its own isModified?
                    opindex.onOpacityChange(x, miny + y, z, newBlock.getLightOpacity());
                }
            }
        }
        isSurfaceTracked = true;
        ((IColumnInternal) getColumn()).removeFromStagingHeightmap(this);
        ((ICubicWorldInternal) world).getLightingManager()
            .onTrackCubeSurface(this);
    }

    /**
     * Mark this cube as no longer part of this world
     */
    public void onCubeUnload() {
        ((ICubicWorldInternal) this.world).getLightingManager()
            .onCubeUnload(this);

        if (!isCubeLoaded) {
            CubicChunks.LOGGER.error("Attempting to unload already unloaded cube at " + this.getCoords());
            return;
        }
        // first mark as unloaded so that entity list and tile entity map isn't modified while iterating
        // and it also preserves all entities/time entities so they can be saved
        this.isCubeLoaded = false;

        // tell the world to forget about entities
        this.world.unloadEntities(this.entities);

        for (Entity entity : this.entities) {
            // CHECKED: 1.10.2-12.18.1.2092
            entity.addedToChunk = false; // World tries to remove entities from Cubes
            // if (addedToCube || Column is loaded)
            // so we need to set addedToChunk to false as a hack!
            // else World would reload this Cube!
        }

        // tell the world to forget about tile entities
        for (TileEntity tileEntity : this.cubeTileEntityMap.values()) {
            this.world.func_147457_a(tileEntity);
        }
        ((IColumnInternal) getColumn()).removeFromStagingHeightmap(this);
        EVENT_BUS.post(new CubeEvent.Unload(this));
    }

    @Override
    public boolean needsSaving(boolean saveAll) {
        if (saveAll) {
            if (this.hasEntities && this.world.getTotalWorldTime() != this.lastSaveTime || this.isModified) {
                return true;
            }
        } else if (this.hasEntities && this.world.getTotalWorldTime() >= this.lastSaveTime + 600L) {
            return true;
        }

        return this.isModified || cubeLightData.needsSaving(this);
    }

    // TODO Check that is this is updated correctly in the CubeProviderServer
     /**
     * Mark this cube as saved to disk
     */
     public void markSaved() {
         this.isModified = false;
         this.cubeLightData.markSaved(this);
     }

    /**
     * Mark this cube as one, who need to be saved to disk
     */
    public void markDirty() {
        this.isModified = true;
    }

    /**
     * Retrieve a list of tickets currently holding this cube loaded
     *
     * @return the list of tickets
     */
    public TicketList getTickets() {
        return tickets;
    }

    public void markForRenderUpdate() {
        this.world.markBlockRangeForRenderUpdate(
            cubeToMinBlock(this.coords.getX()),
            cubeToMinBlock(this.coords.getY()),
            cubeToMinBlock(this.coords.getZ()),
            cubeToMaxBlock(this.coords.getX()),
            cubeToMaxBlock(this.coords.getY()),
            cubeToMaxBlock(this.coords.getZ()));
    }

    public ICubeLightTrackingInfo getCubeLightData() {
        return this.cubeLightData;
    }

    /**
     * Mark this cube as a client side cube. Less work is done in this case, as we expect to receive updates from the
     * server
     */
    public void setClientCube() {
        this.isPopulated = true;
        this.isFullyPopulated = true;
        this.isInitialLightingDone = true;
        this.isSurfaceTracked = true;
        this.ticked = true;
    }

    @Override
    public boolean isPopulated() {
        return isPopulated;
    }

    /**
     * Mark this cube as populated. This means that this cube was passed as argument to
     * {@link ICubeGenerator#populate(Cube)}. Check there for more information regarding population.
     *
     * @param populated whether this cube was populated
     */
    public void setPopulated(boolean populated) {
        this.isPopulated = populated;
        this.isModified = true;
    }

    @Override
    public boolean isFullyPopulated() {
        return this.isFullyPopulated;
    }

    /**
     * Mark this cube as fully populated. This means that any cube potentially writing to this cube was passed as an
     * argument to {@link ICubeGenerator#populate(Cube)}. Check there for more information regarding
     * population.
     *
     * @param populated whether this cube was fully populated
     */
    public void setFullyPopulated(boolean populated) {
        this.isFullyPopulated = populated;
        this.isModified = true;
    }

    /**
     * Sets internal isSurfaceTracked value. Intended to be used only for deserialization.
     *
     * @param value true if surface is already tracked
     */
    public void setSurfaceTracked(boolean value) {
        this.isSurfaceTracked = value;
    }

    @Override
    public boolean isSurfaceTracked() {
        return this.isSurfaceTracked;
    }

    @Override
    public boolean isInitialLightingDone() {
        return isInitialLightingDone;
    }

    /**
     * Notify this cube that it's initial diffuse skylight has been calculated
     *
     * @param initialLightingDone true if initial lighting is done
     */
    public void setInitialLightingDone(boolean initialLightingDone) {
        this.isInitialLightingDone = initialLightingDone;
        this.isModified = true;
    }

    public void setCubeLoaded() {
        this.isCubeLoaded = true;
    }

    @Override
    public boolean isCubeLoaded() {
        return this.isCubeLoaded;
    }

    @Override
    public boolean hasLightUpdates() {
        return ((ICubicWorldInternal) world).getLightingManager()
            .hasPendingLightUpdates(this);
    }

    public boolean hasBeenTicked() {
        return ticked;
    }

    @Override
    public EnumSet<ForcedLoadReason> getForceLoadStatus() {
        EnumSet<ForcedLoadReason> forcedLoadReasons = EnumSet.noneOf(ForcedLoadReason.class);
        if (this.tickets.canUnload()) {
            return forcedLoadReasons;
        }
        if (this.tickets.anyMatch(t -> t instanceof SpawnCubes)) {
            forcedLoadReasons.add(ForcedLoadReason.SPAWN_AREA);
        }
        if (this.tickets.anyMatch(t -> t instanceof CubeWatcher)) {
            forcedLoadReasons.add(ForcedLoadReason.PLAYER);
        }
        if (this.tickets.anyMatch(t -> t instanceof ICubicTicketInternal)) {
            forcedLoadReasons.add(ForcedLoadReason.MOD_TICKET);
        }
        if (this.tickets.anyMatch(
            t -> !(t instanceof SpawnCubes) && !(t instanceof CubeWatcher) && !(t instanceof ICubicTicketInternal))) {
            forcedLoadReasons.add(ForcedLoadReason.OTHER);
        }
        return forcedLoadReasons;
    }

    public interface ICubeLightTrackingInfo {

        boolean needsSaving(ICube cube);

        void markSaved(ICube cube);
    }
}
