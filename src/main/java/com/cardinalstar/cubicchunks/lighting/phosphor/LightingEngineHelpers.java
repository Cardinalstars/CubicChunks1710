package com.cardinalstar.cubicchunks.lighting.phosphor;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.util.Coords;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class LightingEngineHelpers {
    private static final Block DEFAULT_BLOCK = Blocks.air;

    // Avoids some additional logic in Chunk#getBlockState... 0 is always air
    static Block posToBlock(final int x, final int y, final int z, final ICube cube) {
        return posToBlock(x, y, z, cube.getStorage());
    }

    static Block posToBlock(final int x, final int y, final int z, final ExtendedBlockStorage section) {

        if (section != null) {
            Block block = section.getBlockByExtId(Coords.blockToLocal(x), Coords.blockToLocal(y), Coords.blockToLocal(z));
            if (block != null) {
                return block;
            }
        }

        return DEFAULT_BLOCK;
    }
}
