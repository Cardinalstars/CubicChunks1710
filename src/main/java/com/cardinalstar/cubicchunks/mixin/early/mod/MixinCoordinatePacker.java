package com.cardinalstar.cubicchunks.mixin.early.mod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.cardinalstar.cubicchunks.util.Coords;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;

@Mixin(value = CoordinatePacker.class, remap = false)
public class MixinCoordinatePacker {

    /**
     * @author Recursive Pineapple
     * @reason Performance
     */
    @Overwrite
    public static long pack(int x, int y, int z) {
        return Coords.key(x, y, z);
    }

    /**
     * @author Recursive Pineapple
     * @reason Performance
     */
    @Overwrite
    public static int unpackX(long packed) {
        return Coords.x(packed);
    }

    /**
     * @author Recursive Pineapple
     * @reason Performance
     */
    @Overwrite
    public static int unpackY(long packed) {
        return Coords.y(packed);
    }

    /**
     * @author Recursive Pineapple
     * @reason Performance
     */
    @Overwrite
    public static int unpackZ(long packed) {
        return Coords.z(packed);
    }
}
