package com.cardinalstar.cubicchunks.network;

import net.minecraft.entity.player.EntityPlayerMP;

public interface CCPacket {

    byte getPacketID();

    default void sendToPlayer(EntityPlayerMP player) {
        NetworkChannel.CHANNEL.sendToPlayer(this, player);
    }
}
