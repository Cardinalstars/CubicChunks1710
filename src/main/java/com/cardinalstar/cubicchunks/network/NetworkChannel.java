package com.cardinalstar.cubicchunks.network;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.CubicChunks;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.MessageToMessageCodec;

@SuppressWarnings("unused")
@ChannelHandler.Sharable
public class NetworkChannel extends MessageToMessageCodec<FMLProxyPacket, CCPacket> {

    private final EnumMap<Side, FMLEmbeddedChannel> channel;
    private final CCPacketEncoder<CCPacket>[] encoders;

    public static final NetworkChannel CHANNEL = new NetworkChannel();

    public NetworkChannel() {
        this.channel = NetworkRegistry.INSTANCE.newChannel(CubicChunks.MODID, this, new HandlerShared());

        CCPacketEncoder<?>[] packetTypes = Arrays.stream(CCPacketEntry.values())
            .map(e -> e.encoder)
            .toArray(CCPacketEncoder[]::new);

        final int maxPacketID = Arrays.stream(packetTypes)
            .mapToInt(CCPacketEncoder::getPacketID)
            .max()
            .getAsInt();

        // noinspection unchecked
        this.encoders = new CCPacketEncoder[maxPacketID + 1];

        for (CCPacketEncoder<?> packetType : packetTypes) {
            int packetID = packetType.getPacketID();
            if (this.encoders[packetID] == null) {
                // noinspection unchecked
                this.encoders[packetID] = (CCPacketEncoder<CCPacket>) packetType;
            } else {
                throw new IllegalArgumentException("Duplicate Packet ID! " + packetID);
            }
        }
    }

    @Override
    protected void encode(ChannelHandlerContext context, CCPacket packet, List<Object> output) {
        ByteBuf buffer = Unpooled.buffer().writeByte(packet.getPacketID());
        CCPacketEncoder<CCPacket> encoder = this.encoders[packet.getPacketID()];
        encoder.writePacket(new CCPacketBuffer(buffer), packet);

        output.add(
            new FMLProxyPacket(
                deflate(buffer),
                context.channel()
                    .attr(NetworkRegistry.FML_CHANNEL)
                    .get()));
    }

    private static ByteBuf deflate(ByteBuf buffer) {
        ByteBuf compressed = Unpooled.buffer();

        Deflater deflater = new Deflater();

        byte[] outputBuffer = new byte[64];

        deflater.setInput(buffer.array(), 0, buffer.readableBytes());
        deflater.finish();

        while (!deflater.finished()) {
            int written = deflater.deflate(outputBuffer);
            compressed.writeBytes(outputBuffer, 0, written);
        }

        return compressed;
    }

    private static ByteBuf inflate(ByteBuf buffer) throws DataFormatException {
        ByteBuf decompressed = Unpooled.buffer();

        Inflater inflater = new Inflater();

        try {
            byte[] outputBuffer = new byte[64];

            inflater.setInput(buffer.array(), 0, buffer.readableBytes());

            while (!inflater.finished()) {
                int written = inflater.inflate(outputBuffer);
                decompressed.writeBytes(outputBuffer, 0, written);
            }
        } finally {
            inflater.end();
        }

        return decompressed;
    }

    @Override
    protected void decode(ChannelHandlerContext context, FMLProxyPacket proxyPacket, List<Object> output) {
        ByteBuf buffer;

        try {
            buffer = inflate(proxyPacket.payload());
        } catch (DataFormatException e) {
            CubicChunks.LOGGER.error("Could not inflate packet received from server: it will be ignored", e);
            return;
        }

        CCPacketEncoder<CCPacket> encoder = this.encoders[buffer.readByte()];
        CCPacket packet = encoder.readPacket(new CCPacketBuffer(buffer));
        encoder.setINetHandler(proxyPacket.handler(), packet);
        output.add(packet);
    }

    public void sendToPlayer(CCPacket packet, EntityPlayerMP player) {
        if (packet == null) {
            CubicChunks.LOGGER.info("packet null");
            return;
        }
        if (player == null) {
            CubicChunks.LOGGER.info("player null");
            return;
        }
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGET)
            .set(FMLOutboundHandler.OutboundTarget.PLAYER);
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGETARGS)
            .set(player);
        this.channel.get(Side.SERVER)
            .writeAndFlush(packet);
    }

    public void sendToAllAround(CCPacket packet, NetworkRegistry.TargetPoint position) {
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGET)
            .set(FMLOutboundHandler.OutboundTarget.ALLAROUNDPOINT);
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGETARGS)
            .set(position);
        this.channel.get(Side.SERVER)
            .writeAndFlush(packet);
    }

    public void sendToAll(CCPacket packet) {
        this.channel.get(Side.SERVER)
            .attr(FMLOutboundHandler.FML_MESSAGETARGET)
            .set(FMLOutboundHandler.OutboundTarget.ALL);
        this.channel.get(Side.SERVER)
            .writeAndFlush(packet);
    }

    public void sendToServer(CCPacket packet) {
        this.channel.get(Side.CLIENT)
            .attr(FMLOutboundHandler.FML_MESSAGETARGET)
            .set(FMLOutboundHandler.OutboundTarget.TOSERVER);
        this.channel.get(Side.CLIENT)
            .writeAndFlush(packet);
    }

    public void sendPacketToAllPlayersInRange(World world, CCPacket packet, int x, int z) {
        if (!world.isRemote) {
            for (Object tObject : world.playerEntities) {
                if (!(tObject instanceof EntityPlayerMP tPlayer)) {
                    break;
                }
                Chunk tChunk = world.getChunkFromBlockCoords(x, z);
                if (tPlayer.getServerForPlayer()
                    .getPlayerManager()
                    .isPlayerWatchingChunk(tPlayer, tChunk.xPosition, tChunk.zPosition)) {
                    sendToPlayer(packet, tPlayer);
                }
            }
        }
    }

    @Sharable
    private class HandlerShared extends SimpleChannelInboundHandler<CCPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, CCPacket packet) {
            World world = FMLCommonHandler.instance()
                .getEffectiveSide()
                .isClient() ? getClientWorld() : null;

            encoders[packet.getPacketID()].process(world, packet);
        }

        @SideOnly(Side.CLIENT)
        private World getClientWorld() {
            return Minecraft.getMinecraft().theWorld;
        }
    }
}
