package com.cardinalstar.cubicchunks.util.biome3d;

import com.cardinalstar.cubicchunks.network.CCPacketBuffer;

/// Fast but very simple compression that assumes the data has long stretches of contiguous values. This algorithm does
/// not check if the result is actually smaller so noisy data may take more space than the original data.
/// This works by scanning the data input and squishing lengths of the same values. If the input buffer contains a
/// single repeated value, the resulting buffer will consist of a single [#OP_DATA], followed by a [#OP_DONE].
public class NaiveCompression {

    /// Some data. Has two parameters: an int for the value, followed by a var int for the length.
    private static final byte OP_DATA = 0;
    /// Indicates that the stream is done. Has no parameters.
    private static final byte OP_DONE = 1;

    public interface NaiveCompressionDataInput {

        int size();

        int get(int index);
    }

    public static void compress(NaiveCompressionDataInput data, CCPacketBuffer buffer) {
        int i = 0;

        int size = data.size();

        while (i < size) {
            int curr = data.get(i);

            int i2 = i + 1;

            while (i2 < size && data.get(i2) == curr) {
                i2++;
            }

            int len = i2 - i;

            buffer.writeByte(OP_DATA);
            buffer.writeInt(curr);
            buffer.writeVarIntToBuffer(len);

            i = i2;
        }

        buffer.writeByte(OP_DONE);
    }

    public interface NaiveCompressionDataOutput {

        int size();

        void set(int index, int value);
    }

    public static void decompress(CCPacketBuffer buffer, NaiveCompressionDataOutput data) {
        byte op;
        int i = 0;

        while ((op = buffer.readByte()) == OP_DATA) {
            int value = buffer.readInt();
            int len = buffer.readVarIntFromBuffer();

            for (int k = 0; k < len; k++) {
                data.set(i + k, value);
            }

            i += len;
        }

        if (op != OP_DONE) throw new IllegalStateException("Expected buffer to end with DONE operation");
        if (i != data.size())
            throw new IllegalStateException("Expected buffer to decompress to " + data.size() + " bytes of data");
    }
}
