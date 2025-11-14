package com.cardinalstar.cubicchunks.api.worldgen;

import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubeGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubePopulator;

public interface WorldgenRegistry {

    /// Terrain generators create the general shape of the terrain and cannot interact with other cubes. This includes
    /// any noise-based generation that doesn't require information from other cubes such as modern caves.
    DependencyRegistry<ICubeGenerator> terrain();

    /// Populators finalize cubes and put the 'window dressing' on the world - ores, trees, etc.
    DependencyRegistry<ICubePopulator> population();
}
