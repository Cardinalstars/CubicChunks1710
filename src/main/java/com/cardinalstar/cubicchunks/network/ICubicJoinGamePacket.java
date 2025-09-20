package com.cardinalstar.cubicchunks.network;

public interface ICubicJoinGamePacket {

    int cubicChunks$getMinHeight();

    int cubicChunks$getMaxHeight();

    int cubicChunks$getMinGenerationHeight();

    int cubicChunks$getMaxGenerationHeight();

    public void InitCubicJoinGamePacket(int minHeight, int maxHeight, int minGenerationHeight, int maxGenerationHeight);
}
