package com.cardinalstar.cubicchunks.world.cube;


import com.cardinalstar.cubicchunks.server.chunkio.CubeInitLevel;
import net.minecraft.world.chunk.Chunk;

public class BoundaryCube extends Cube
{
    public BoundaryCube(Chunk column, int cubeY) {
        super(column, cubeY);
    }

    ///  Boundary cubes are always considered to be lit.
    public CubeInitLevel getInitLevel()
    {
        return CubeInitLevel.Lit;
    }
}
