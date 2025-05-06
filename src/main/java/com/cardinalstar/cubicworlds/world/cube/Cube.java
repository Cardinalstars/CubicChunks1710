package com.cardinalstar.cubicworlds.world.cube;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: Pull some of these objects into a column, espeically the ones like the biome array
public class Cube
{
    private static final Logger logger = LogManager.getLogger();
    /** Determines if the chunk is lit or not at a light value greater than 0. */
    public static boolean isLit;
    /**
     * Used to store block IDs, block MSBs, Sky-light maps, Block-light maps, and metadata. Each entry corresponds to a
     * logical segment of 16x16x16 blocks, stacked vertically.
     */
    private ExtendedBlockStorage storageArray;
    /** Contains a 16x16 mapping on the X/Z plane of the biome ID to which each colum belongs. */
    private byte[] blockBiomeArray;
    /** A map, similar to heightMap, that tracks how far down precipitation can fall. */
    public int[] precipitationHeightMap;
    /** Which columns need their skylightMaps updated. */
    public boolean[] updateSkylightColumns;
    /** Whether or not this Chunk is currently loaded into the World */
    public boolean isCubeLoaded;
    /** Reference to the World object. */
    public World worldObj;
    public int[] heightMap;
    /** The x coordinate of the cube. */
    public final int xPosition;

    /** The y coordiante of the cube. */
    public final int yPosition;
    /** The z coordinate of the cube. */
    public final int zPosition;
    private boolean isGapLightingUpdated;
    /** A Map of ChunkPositions to TileEntities in this cube */
    public Map<ChunkPosition, TileEntity> cubeTileEntityMap;
    /** List containing the entities in this Cube.*/
    public List entityList;
    /** Boolean value indicating if the terrain is populated. */
    public boolean isTerrainPopulated;
    public boolean isLightPopulated;
    public boolean cubeTicked;
    /** Set to true if the cube has been modified and needs to be updated internally. */
    public boolean isModified;
    /** Whether this Cube has any Entities and thus requires saving on every tick */
    public boolean hasEntities;
    /** The time according to World.worldTime when this cube was last saved */
    public long lastSaveTime;
    /**
     * Updates to this cube will not be sent to clients if this is false. This field is set to true the first time the
     * cube is sent to a client, and never set to false.
     */
    public boolean sendUpdates;
    /** Lowest value in the heightmap. */
    public int heightMapMinimum;
    /** the cumulative number of ticks players have been in this cube */
    public long inhabitedTime;
    /** Contains the current round-robin relight check index, and is implied as the relight check location as well. */
    private int queuedLightChecks;
    /** A field used to keep track of things that are relevant for x, z position in a world.*/
    public Chunk column;

    public Cube(World world, Chunk column, int cubeY)
    {
        this.blockBiomeArray = new byte[256];
        this.precipitationHeightMap = new int[256];
        this.updateSkylightColumns = new boolean[256];
        this.cubeTileEntityMap = new HashMap();
        this.queuedLightChecks = 4096;
        this.entityList = new ArrayList<Entity>();
        this.worldObj = world;
        this.xPosition = column.xPosition;
        this.yPosition = cubeY;
        this.zPosition = column.zPosition;
        this.column = column;
        this.heightMap = new int[256];
    }

    public Cube(World world, Block[] blockArray, byte[] metaArray, Chunk column, int cubeY)
    {
        this(world, column, cubeY);
        boolean flag = !world.provider.hasNoSky;

        for(int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                for (int y = 0; y < 16; y++)
                {
                    int arrayLoc = x << 8 | z << 4 | y;
                    Block block = blockArray[arrayLoc];
                    if (block != null && block != Blocks.air)
                    {
                        if (this.storageArray == null)
                        {
                            this.storageArray = new ExtendedBlockStorage(yPosition * 16 + y, flag);
                        }
                        this.storageArray.func_150818_a(x, y , z, block);
                        this.storageArray.setExtBlockMetadata(x, y, z, metaArray[arrayLoc]);
                    }
                }
            }
        }
    }

    public void onCubeLoad()
    {

    }

    public void populateCube(IChunkProvider provider1, IChunkProvider provider2, int x, int y, int z)
    {

    }
}
