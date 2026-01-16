package com.cardinalstar.cubicchunks.api.world;

/// Something that can queue up precalculation jobs
public interface Precalculable {

    void precalculate(int cubeX, int cubeY, int cubeZ);

}
