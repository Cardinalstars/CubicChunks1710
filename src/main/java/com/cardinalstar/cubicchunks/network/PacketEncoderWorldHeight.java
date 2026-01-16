package com.cardinalstar.cubicchunks.network;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.network.PacketEncoderWorldHeight.PacketWorldHeight;
import com.github.bsideup.jabel.Desugar;

public class PacketEncoderWorldHeight extends CCPacketEncoder<PacketWorldHeight> {

    @Desugar
    public record PacketWorldHeight(int min, int max) implements CCPacket {

        @Override
        public byte getPacketID() {
            return CCPacketEntry.WorldHeight.id;
        }
    }

    public static PacketWorldHeight create(int min, int max) {
        return new PacketWorldHeight(min, max);
    }

    @Override
    public byte getPacketID() {
        return CCPacketEntry.WorldHeight.id;
    }

    @Override
    public void writePacket(CCPacketBuffer buffer, PacketWorldHeight packet) {
        buffer.writeInt(packet.min);
        buffer.writeInt(packet.max);
    }

    @Override
    public PacketWorldHeight readPacket(CCPacketBuffer buffer) {
        return new PacketWorldHeight(buffer.readInt(), buffer.readInt());
    }

    @Override
    public void process(World world, PacketWorldHeight packet) {
        ((ICubicWorldInternal) world).setHeightBounds(packet.min, packet.max);
    }
}
