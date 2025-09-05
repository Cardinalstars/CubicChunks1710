package com.cardinalstar.cubicchunks.api.world;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;

public interface IColumnWatcher {

    void addPlayer(final EntityPlayerMP playerMP);

    void removePlayer(EntityPlayerMP playerMP);

    boolean containsPlayer(EntityPlayerMP playerMP);

    Chunk getColumn();

    ChunkCoordIntPair getPos();

    void increaseInhabitedTime();

    void blockChanged(int x, int y, int z);

    void update();

    void sendToPlayer(EntityPlayerMP player);

    boolean sendToPlayers();
}
