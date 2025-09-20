package com.cardinalstar.cubicchunks.network;

public enum CCPacketEntry {
    Cubes(new PacketEncoderCubes()),
    Column(new PacketEncoderColumn()),
    UnloadColumn(new PacketEncoderUnloadColumn()),
    UnloadCube(new PacketEncoderUnloadCube()),
    CubeBlockChange(new PacketEncoderCubeBlockChange()),
    CubicWorldData(new PacketEncoderCubicWorldData()),
    HeightMapUpdate(new PacketEncoderHeightMapUpdate()),
    CubeSkyLightUpdates(new PacketEncoderCubeSkyLightUpdates()),
    ;

    public final byte id = (byte) ordinal();
    public final CCPacketEncoder<?> encoder;

    CCPacketEntry(CCPacketEncoder<?> encoder) {
        this.encoder = encoder;
    }
}
