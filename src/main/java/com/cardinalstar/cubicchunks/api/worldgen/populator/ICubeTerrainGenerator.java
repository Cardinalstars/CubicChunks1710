package com.cardinalstar.cubicchunks.api.worldgen.populator;

import com.cardinalstar.cubicchunks.api.worldgen.ICubeGenerator;
import com.cardinalstar.cubicchunks.world.cube.Cube;

public interface ICubeTerrainGenerator<T extends ICubeGenerator> {

    /**
     * Fills a cube with various terrain features. The cube is not in the world at this time, and no world operations
     * should be performed within this method. This method is called after all vanilla generation has been performed,
     * including the bottom/top chunk filling.
     */
    void generate(T generator, Cube cube);

}
