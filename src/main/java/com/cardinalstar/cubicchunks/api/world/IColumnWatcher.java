package com.cardinalstar.cubicchunks.api.world;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;

public interface IColumnWatcher {

    void addPlayer(final EntityPlayerMP playerMP);

    void removePlayer(EntityPlayerMP playerMP);

    boolean containsPlayer(EntityPlayerMP playerMP);

    Chunk getChunk();

    ChunkCoordIntPair getPos();

    void IncreaseInhabitedTime();

    void blockChanged(int x, int y, int z);

    void update();

    void sendToPlayer(EntityPlayerMP player);

    boolean sendToPlayers();
}
