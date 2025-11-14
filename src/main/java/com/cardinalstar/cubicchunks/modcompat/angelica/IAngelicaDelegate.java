package com.cardinalstar.cubicchunks.modcompat.angelica;

public interface IAngelicaDelegate {

    void onColumnLoaded(int chunkX, int chunkZ);

    void onColumnUnloaded(int chunkX, int chunkZ);

    void onCubeLoaded(int cubeX, int cubeY, int cubeZ);

    void onCubeUnloaded(int cubeX, int cubeY, int cubeZ);

}
