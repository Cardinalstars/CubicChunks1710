package com.cardinalstar.cubicchunks.mixin.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;


@Mixin(targets = "net.minecraft.server.management.PlayerManager$PlayerInstance")
public interface IPlayerInstance {
    @Accessor("playersWatchingChunk")
    List<EntityPlayerMP> getPlayers();

    @Accessor("chunkLocation")
    ChunkCoordIntPair getPos();

    @Accessor("loaded")
    boolean isLoaded();

    @Accessor("chunk")
    Chunk getChunk();

    @Accessor("chunk")
    void setChunk(Chunk chunk);

    @Accessor("lastUpdateInhabitedTime")
    void setLastUpdateInhabitedTime(long time);

    @Accessor("sentToPlayers")
    void setSentToPlayers(boolean sent);

    @Accessor("sentToPlayers")
    boolean getSentToPlayers();

    @Accessor("loadedRunnable")
    Runnable getLoadedRunnable();

    @Invoker("increaseInhabitedTime")
    void increaseInhabitedTime(Chunk chunk);
}
