/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2021 OpenCubicChunks
 *  Copyright (c) 2015-2021 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.world.cube;

import static com.cardinalstar.cubicchunks.util.Coords.blockToLocal;
import static net.minecraftforge.common.MinecraftForge.EVENT_BUS;

import com.cardinalstar.cubicchunks.core.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.core.util.TicketList;

import com.cardinalstar.cubicchunks.util.CubeCoordIntTriple;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A cube is our extension of minecraft's chunk system to three dimensions. Each cube encloses a cubic area in the world
 * with a side length of {@link Cube#SIZE}, aligned to multiples of that length and stored within columns.
 */
@ParametersAreNonnullByDefault
public class Cube implements ICube {

    @Nullable
    protected static final ExtendedBlockStorage NULL_STORAGE = null;

    @Nullable
    private byte[] blockBiomeArray = null;

    /**
     * Tickets keep this chunk loaded and ticking. See the docs of {@link TicketList} and {@link ITicket} for additional information.
     */
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
     * The column of this cube
     */
    @Nonnull
    private final Chunk column;
    /**
     * The position of this cube, in cube space
     */
    @Nonnull
    private final CubeCoordIntTriple coords;
    /**
     * Blocks in this cube
     */
    @Nullable
    private ExtendedBlockStorage storage;
    /**
     * Entities in this cube
     */
    @Nonnull
    private final EntityContainer entities;
    /**
     * The position of tile entities in this cube, and their corresponding tile entity
     */
    @Nonnull
    private final Map<BlockPos, TileEntity> tileEntityMap;
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
     * This is used as an optimization to avoid putting all the cubes to tick in a set (which turns out to be very slow because of huge amount of
     * cubes), or iterating over all loaded cubes, which can also be very expensive in case of very big render distance, where not many cubes are
     * actually ticked, but hundreds of thousands of them can be loaded.
     * <p>
     * Instead, cubes for players are added into a simple arraylist, and forced cubes are iterated separately, and double-ticking is avoided by
     * checking this lastTicked field instead of deduplication by putting them all into a Set.
     */
    private long lastTicked = Long.MIN_VALUE;

//    private final CapabilityDispatcher capabilities;

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
        this.coords = new CubeCoordIntTriple(column.xPosition, cubeY, column.zPosition);

        this.tickets = new TicketList(this);

        this.entities = new EntityContainer();
        this.tileEntityMap = new HashMap<>();
        this.tileEntityPosQueue = new ConcurrentLinkedQueue<>();

        this.cubeLightData = ((ICubicWorldInternal) world).getLightingManager().createLightData(this);

        this.storage = NULL_STORAGE;

//        AttachCapabilitiesEvent<ICube> event = new AttachCapabilitiesEvent<>(ICube.class, this);
//        MinecraftForge.EVENT_BUS.post(event);
//        this.capabilities = event.getCapabilities().size() > 0 ? new CapabilityDispatcher(event.getCapabilities(), null) : null;
    }

    /**
     * Create a new cube at the specified location by copying blocks from a cube primer.
     *
     * @param column column of this cube
     * @param cubeY  cube y position
     * @param primer primer containing the blocks for this cube
     */
    @SuppressWarnings("deprecation") // when a block is generated, does it really have any extra
    // information it could give us about its opacity by knowing its location?
    public Cube(Chunk column, int cubeY, CubePrimer primer) {
        this(column, cubeY);

        for (int y = Cube.SIZE - 1; y >= 0; y--) {
            for (int z = 0; z < Cube.SIZE; z++) {
                for (int x = 0; x < Cube.SIZE; x++) {
                    IBlockState newstate = primer.getBlockState(x, y, z);

                    if (newstate.getMaterial() != Material.AIR) {
                        if (storage == NULL_STORAGE) {
                            newStorage();
                        }
                        storage.set(x, y, z, newstate);
                    }
                }
            }
        }
        if (primer.hasBiomes()) {
            for (int biomeX = 0; biomeX < 4; biomeX++) {
                for (int biomeY = 0; biomeY < 4; biomeY++) {
                    for (int biomeZ = 0; biomeZ < 4; biomeZ++) {
                        Biome biome = primer.getBiome(biomeX, biomeY, biomeZ);
                        if (biome != null) {
                            setBiome(biomeX, biomeY, biomeZ, biome);
                        }
                    }
                }
            }
        }
        isModified = true;
    }


    /**
     * Constructor to be used from subclasses to provide all field values
     *
     * @param tickets cube ticket list
     * @param world the world instance
     * @param column the column this cube belongs to
     * @param coords position of this cube
     * @param storage block storage
     * @param entities entity container
     * @param tileEntityMap tile entity storage
     * @param tileEntityPosQueue queue for updating tile entities
     * @param cubeLightData cube light tracking data
     */
    protected Cube(TicketList tickets, World world, Chunk column, CubeCoordIntTriple coords, ExtendedBlockStorage storage,
                   EntityContainer entities, Map<BlockPos, TileEntity> tileEntityMap,
                   ConcurrentLinkedQueue<BlockPos> tileEntityPosQueue, ICubeLightTrackingInfo cubeLightData) {
        this.tickets = tickets;
        this.world = world;
        this.column = column;
        this.coords = coords;
        this.storage = storage;
        this.entities = entities;
        this.tileEntityMap = tileEntityMap;
        this.tileEntityPosQueue = tileEntityPosQueue;
        this.cubeLightData = cubeLightData;

//        AttachCapabilitiesEvent<ICube> event = new AttachCapabilitiesEvent<>(ICube.class, this);
//        MinecraftForge.EVENT_BUS.post(event);
//        this.capabilities = event.getCapabilities().size() > 0 ? new CapabilityDispatcher(event.getCapabilities(), null) : null;

    }

    //======================================
    //========Chunk vanilla methods=========
    //======================================

//    @Override
//    public IBlockState getBlockState(BlockPos pos) {
//        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
//    }
//
//    @Override
//    @Nullable
//    public IBlockState setBlockState(BlockPos pos, IBlockState newstate) {
//        return column.setBlockState(pos, newstate);
//    }
//
//    @Override
//    public IBlockState getBlockState(int blockX, int localOrBlockY, int blockZ) {
//        if (storage == NULL_STORAGE) {
//            return Blocks.AIR.getDefaultState();
//        }
//        return storage.get(blockToLocal(blockX), blockToLocal(localOrBlockY), blockToLocal(blockZ));
//    }

    @Override
    public int getLightFor(EnumSkyBlock lightType, int x, int y, int z) {
        ((ICubicWorldInternal) world).getLightingManager().onGetLight(type, pos);
        return getCachedLightFor(lightType, x, y, z);
    }

    public int getCachedLightFor(EnumSkyBlock type, int xPos, int yPos, int zPos) {
        int x = blockToLocal(xPos);
        int y = blockToLocal(yPos);
        int z = blockToLocal(zPos);
        ExtendedBlockStorage storage = this.storage;

        if (storage == null) {
            return ((IColumnInternal) column).getTopYWithStaging(x, z) > yPos ? type.defaultLightValue : 0;
        } else if (type == EnumSkyBlock.Sky) {
            return this.world.provider.hasNoSky ? 0 : storage.getExtSkylightValue(x, y, z);
        } else {
            return type == EnumSkyBlock.Block ? storage.getExtBlocklightValue(x, y, z) : type.defaultLightValue;
        }
    }

    @Override
    public void setLightFor(EnumSkyBlock lightType, int x, int y, int z, int light) {
        column.setLightValue(lightType, x, y, z, light);
    }

    /**
     * Create a tile entity at the given position if the block is able to hold one
     *
     * @param pos position where the tile entity should be placed
     * @return the created tile entity, or <code>null</code> if the block at that position does not provide tile
     * entities
     */
    @Nullable
    private TileEntity createTileEntity(int x, int y, int z) {
        IBlockState blockState = getBlockState(pos);
        Block block = blockState.getBlock();

        if (block.hasTileEntity(blockState)) {
            return block.createTileEntity((World) this.world, blockState);
        }
        return null;
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(int x, int y, int z) { // TODO WATCH
        return column.getTileEntityUnsafe(x, y, z);
    }

    // have a copy of addTileEntity in Cube because sometimes some mods will access blocks from outside of
    // the cube being loaded while loading it's tile entity, causing a StackOverflowError when the cube set at
    // the start of loading TEs in column gets changed.
    @Override
    public void addTileEntity(TileEntity tileEntityIn) {
        this.addTileEntity(tileEntityIn.xCoord, tileEntityIn.yCoord, tileEntityIn.zCoord, tileEntityIn);
        if (this.isCubeLoaded) {
            this.world.addTileEntity(tileEntityIn);
        }
    }

    private void addTileEntity(int x, int y, int z, TileEntity tileEntityIn) {
        if (tileEntityIn.getWorld() != this.world) { //Forge don't call unless it's changed, could screw up bad mods.
            tileEntityIn.setWorld(this.world);
        }
        tileEntityIn.setPos(pos);

        if (this.getBlockState(pos).getBlock().hasTileEntity(this.getBlockState(pos))) {
            if (this.tileEntityMap.containsKey(pos)) {
                this.tileEntityMap.get(pos).invalidate();
            }

            tileEntityIn.validate();
            this.tileEntityMap.put(pos, tileEntityIn);
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

            IBlockState state = this.getBlockState(blockpos);
            Block block = state.getBlock();

            if (this.getTileEntity(blockpos, Chunk.EnumCreateEntityType.CHECK) == null &&
                block.hasTileEntity(state)) {
                TileEntity tileentity = this.createTileEntity(blockpos);
                this.world.setTileEntity(blockpos, tileentity);
                this.world.markBlockRangeForRenderUpdate(blockpos, blockpos);
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
     * cube generator
     */
    @Override
    public Biome getBiome(BlockPos pos) {
        if (this.blockBiomeArray == null)
            return this.getColumn().getBiome(pos, world.getBiomeProvider());
        int biomeX = Coords.blockToLocalBiome3d(pos.getX());
        int biomeY = Coords.blockToLocalBiome3d(pos.getY());
        int biomeZ = Coords.blockToLocalBiome3d(pos.getZ());
        int biomeId = this.blockBiomeArray[AddressTools.getBiomeAddress3d(biomeX, biomeY, biomeZ)] & 255;
        return Biome.getBiome(biomeId);
    }

    @Override
    public void setBiome(int localBiomeX, int localBiomeY, int localBiomeZ, Biome biome) {
        if (this.blockBiomeArray == null)
            this.blockBiomeArray = new byte[4 * 4 * 4];

        this.blockBiomeArray[AddressTools.getBiomeAddress3d(localBiomeX, localBiomeY, localBiomeZ)] = (byte) Biome.REGISTRY.getIDForObject(biome);
    }

    @Nullable
    public byte[] getBiomeArray() {
        return this.blockBiomeArray;
    }

    public void setBiomeArray(byte[] biomeArray) {
        if (this.blockBiomeArray == null)
            this.blockBiomeArray = biomeArray;
        if (this.blockBiomeArray.length != biomeArray.length) {
            CubicChunks.LOGGER.warn("Could not set level cube biomes, array length is {} instead of {}", Integer.valueOf(biomeArray.length),
                Integer.valueOf(this.blockBiomeArray.length));
        } else {
            System.arraycopy(biomeArray, 0, this.blockBiomeArray, 0, this.blockBiomeArray.length);
        }
    }
    //=================================
    //=========Other methods===========
    //=================================

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

    @SuppressWarnings({"unchecked", "deprecation", "RedundantSuppression"})
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
    public boolean containsBlockPos(BlockPos blockPos) {
        return this.coords.getX() == blockToCube(blockPos.getX())
            && this.coords.getY() == blockToCube(blockPos.getY())
            && this.coords.getZ() == blockToCube(blockPos.getZ());
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
            ((ICubicWorldInternal) world).getLightingManager().onCreateCubeStorage(this, ebs);
        }
        return ebs;
    }

    public void setStorageFromSave(@Nullable ExtendedBlockStorage ebs) {
        this.storage = ebs;
    }

    private void newStorage() {
        storage = new ExtendedBlockStorage(cubeToMinBlock(getY()), world.provider.hasSkyLight());
    }

    @Override
    public Map<BlockPos, TileEntity> getTileEntityMap() {
        return this.tileEntityMap;
    }

    @Override
    public ClassInheritanceMultiMap<Entity> getEntitySet() {
        return this.entities.getEntitySet();
    }

    @Override
    public void addEntity(Entity entity) {
        this.entities.addEntity(entity);
    }

    @Override
    public boolean removeEntity(Entity entity) {
        return this.entities.remove(entity);
    }

    public EntityContainer getEntityContainer() {
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
    public void onLoad() {
        if (isCubeLoaded) {
            CubicChunks.LOGGER.error("Attempting to load already loaded cube at " + this.getCoords());
            return;
        }
        // tell the world about tile entities
        this.world.addTileEntities(this.tileEntityMap.values());
        this.world.loadEntities(this.entities.getEntities());
        this.isCubeLoaded = true;
        if (!isSurfaceTracked) {
            ((IColumnInternal) getColumn()).addToStagingHeightmap(this);
        }
        ((ICubicWorldInternal) world).getLightingManager().onCubeLoad(this);
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
                    IBlockState newstate = this.getBlockState(x, y, z);

                    column.setModified(true); //TODO: maybe ServerHeightMap needs its own isModified?
                    opindex.onOpacityChange(x, miny + y, z, newstate.getLightOpacity());
                }
            }
        }
        isSurfaceTracked = true;
        ((IColumnInternal) getColumn()).removeFromStagingHeightmap(this);
        ((ICubicWorldInternal) world).getLightingManager().onTrackCubeSurface(this);
    }

    /**
     * Mark this cube as no longer part of this world
     */
    public void onUnload() {
        ((ICubicWorldInternal) this.world).getLightingManager().onCubeUnload(this);

        if (!isCubeLoaded) {
            CubicChunks.LOGGER.error("Attempting to unload already unloaded cube at " + this.getCoords());
            return;
        }
        //first mark as unloaded so that entity list and tile entity map isn't modified while iterating
        //and it also preserves all entities/time entities so they can be saved
        this.isCubeLoaded = false;

        // tell the world to forget about entities
        this.world.unloadEntities(this.entities.getEntities());

        for (Entity entity : this.entities.getEntities()) {
            //CHECKED: 1.10.2-12.18.1.2092
            entity.addedToChunk = false; // World tries to remove entities from Cubes
            // if (addedToCube || Column is loaded)
            // so we need to set addedToChunk to false as a hack!
            // else World would reload this Cube!
        }

        // tell the world to forget about tile entities
        for (TileEntity blockEntity : this.tileEntityMap.values()) {
            this.world.markTileEntityForRemoval(blockEntity);
        }
        ((IColumnInternal) getColumn()).removeFromStagingHeightmap(this);
        EVENT_BUS.post(new CubeEvent.Unload(this));
    }

    @Override
    public boolean needsSaving() {
        return this.entities.needsSaving(true, this.world.getTotalWorldTime(), this.isModified) || cubeLightData.needsSaving(this);
    }

    /**
     * Mark this cube as saved to disk
     */
    public void markSaved() {
        this.entities.markSaved(this.world.getTotalWorldTime());
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
            cubeToMinBlock(this.coords.getX()), cubeToMinBlock(this.coords.getY()), cubeToMinBlock(this.coords.getZ()),
            cubeToMaxBlock(this.coords.getX()), cubeToMaxBlock(this.coords.getY()), cubeToMaxBlock(this.coords.getZ())
        );
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
     * {@link ICubeGenerator#populate(ICube)}. Check there for more information regarding
     * population.
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
     * argument to {@link ICubeGenerator#populate(ICube)}. Check there for more
     * information regarding population
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
        return ((ICubicWorldInternal) world).getLightingManager().hasPendingLightUpdates(this);
    }

    public boolean hasBeenTicked() {
        return ticked;
    }

    @Override
    @Nullable
    public CapabilityDispatcher getCapabilities() {
        return this.capabilities;
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
        if (this.tickets.anyMatch(t ->
            !(t instanceof SpawnCubes) && !(t instanceof CubeWatcher) && !(t instanceof ICubicTicketInternal))) {
            forcedLoadReasons.add(ForcedLoadReason.OTHER);
        }
        return forcedLoadReasons;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return this.capabilities != null && this.capabilities.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        return this.capabilities == null ? null : this.capabilities.getCapability(capability, facing);
    }

    public interface ICubeLightTrackingInfo {
        boolean needsSaving(ICube cube);
        void markSaved(ICube cube);
    }
}
