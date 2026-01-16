package com.cardinalstar.cubicchunks.network;

import net.minecraft.network.INetHandler;
import net.minecraft.world.World;

public abstract class CCPacketEncoder<Packet extends CCPacket> {

    protected CCPacketEncoder() {}

    /**
     * Unique ID of this packet.
     */
    public abstract byte getPacketID();

    /**
     * Encode the data into given byte buffer.
     */
    public void writePacket(CCPacketBuffer buffer, Packet packet) {
        throw new UnsupportedOperationException("Wrong side");
    }

    /**
     * Decode byte buffer into packet object.
     */
    public Packet readPacket(CCPacketBuffer buffer) {
        throw new UnsupportedOperationException("Wrong side");
    }

    /**
     * Process the received packet.
     *
     * @param world null if message is received on server side, the client world if message is received on client side
     */
    public void process(World world, Packet packet) {
        throw new UnsupportedOperationException("Wrong side");
    }

    /**
     * This will be called just before {@link #process(World, CCPacket)}} to inform the handler about the source and
     * type of
     * connection.
     */
    public void setINetHandler(INetHandler handler, Packet packet) {}
}
