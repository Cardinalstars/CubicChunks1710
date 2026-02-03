package com.cardinalstar.cubicchunks.world.worldgen.data;

import org.jetbrains.annotations.ApiStatus;

import com.cardinalstar.cubicchunks.util.Bits;
import com.cardinalstar.cubicchunks.util.CubePos;

/**
 * This is a hacky subclass of CubePos. It's only meant for accessing the TimedCache, and nothing else. The CubePos
 * fields aren't initialized properly, since they're all final, but the various get### methods work properly. This will
 * always return the same hash as a CubePos, and it will always equal a CubePos with the same coord. The opposite is not
 * true - a CubePos will never equal a MutableCubePos.
 */
@ApiStatus.Internal
public class MutableCubePos extends CubePos {

    public int x;
    public int y;
    public int z;

    public MutableCubePos() {
        super(0, 0, 0);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getZ() {
        return z;
    }

    public static CubePos clone(CubePos pos) {
        if (pos instanceof MutableCubePos mutable) {
            return new CubePos(mutable.x, mutable.y, mutable.z);
        } else {
            return pos.clone();
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof CubePos other)) return false;

        return other.getX() == x && other.getY() == y && other.getZ() == z;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(
            Bits.packSignedToLong(x, Y_BITS, Y_BIT_OFFSET) | Bits.packSignedToLong(y, X_BITS, X_BIT_OFFSET)
                | Bits.packSignedToLong(z, Z_BITS, Z_BIT_OFFSET));
    }
}
