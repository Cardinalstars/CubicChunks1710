package com.cardinalstar.cubicchunks.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.ChunkCoordIntPair;

import com.cardinalstar.cubicchunks.util.CubePos;

import io.netty.buffer.ByteBuf;

public class CCPacketBuffer extends PacketBuffer {

    public CCPacketBuffer(ByteBuf wrapped) {
        super(wrapped);
    }

    public void writeCompoundTag(NBTTagCompound nbt) {
        writeNBTTagCompoundToBuffer(nbt);
    }

    @Override
    public void writeNBTTagCompoundToBuffer(NBTTagCompound nbt) {
        try {
            super.writeNBTTagCompoundToBuffer(nbt);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public NBTTagCompound readCompoundTag() {
        return readNBTTagCompoundFromBuffer();
    }

    public NBTTagCompound readNBTTagCompoundFromBuffer() {
        try {
            return super.readNBTTagCompoundFromBuffer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeItemStackToBuffer(ItemStack stack) {
        try {
            super.writeItemStackToBuffer(stack);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ItemStack readItemStackFromBuffer() {
        try {
            return super.readItemStackFromBuffer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String readStringFromBuffer(int maxLength) {
        try {
            return super.readStringFromBuffer(maxLength);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeStringToBuffer(String str) {
        try {
            super.writeStringToBuffer(str);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeCubePos(CubePos pos) {
        writeInt(pos.getX());
        writeInt(pos.getY());
        writeInt(pos.getZ());
    }

    public CubePos readCubePos() {
        return new CubePos(readInt(), readInt(), readInt());
    }

    public void writeChunkPos(ChunkCoordIntPair pos) {
        writeInt(pos.chunkXPos);
        writeInt(pos.chunkZPos);
    }

    public ChunkCoordIntPair readChunkPos() {
        return new ChunkCoordIntPair(readInt(), readInt());
    }

    public interface Encoder<T> {

        void encode(CCPacketBuffer buffer, T value);
    }

    public interface Decoder<T> {

        T decode(CCPacketBuffer buffer);
    }

    public <T> void writeArray(T[] array, Encoder<T> encoder) {
        writeVarIntToBuffer(array.length);

        for (T value : array) {
            encoder.encode(this, value);
        }
    }

    public <T> T[] readArray(T[] zeroLength, Decoder<T> decoder) {
        T[] out = Arrays.copyOf(zeroLength, readVarIntFromBuffer());

        for (int i = 0; i < out.length; i++) {
            out[i] = decoder.decode(this);
        }

        return out;
    }

    public void writeByteArray(byte[] array) {
        writeVarIntToBuffer(array.length);

        writeBytes(array);
    }

    public byte[] readByteArray() {
        byte[] out = new byte[readVarIntFromBuffer()];

        readBytes(out);

        return out;
    }

    public <T> void writeList(List<T> list, Encoder<T> encoder) {
        writeVarIntToBuffer(list.size());

        for (T value : list) {
            encoder.encode(this, value);
        }
    }

    public <T> ArrayList<T> readList(Decoder<T> decoder) {
        int len = readVarIntFromBuffer();

        ArrayList<T> out = new ArrayList<>(len);

        for (int i = 0; i < len; i++) {
            out.add(decoder.decode(this));
        }

        return out;
    }

    public void writeBlockID(int id) {
        writeShort(id);
    }

    public int readBlockID() {
        return readShort();
    }

    private int lastCachedBlock = -1;
    private Block cache;

    public void writeBlock(Block block) {
        if (block == cache) {
            writeBlockID(lastCachedBlock);
        } else {
            cache = block;
            lastCachedBlock = Block.getIdFromBlock(block);

            writeBlockID(lastCachedBlock);
        }
    }

    public Block readBlock() {
        int id = readBlockID();

        if (id == lastCachedBlock) return cache;

        lastCachedBlock = id;
        cache = Block.getBlockById(id);

        return cache;
    }

    public void writeBlockMeta(int meta) {
        writeByte(meta);
    }

    public int readBlockMeta() {
        return readByte();
    }
}
