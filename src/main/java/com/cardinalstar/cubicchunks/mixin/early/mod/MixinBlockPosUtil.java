package com.cardinalstar.cubicchunks.mixin.early.mod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.cardinalstar.cubicchunks.util.Coords;
import com.falsepattern.chunk.internal.BlockPosUtil;

@Mixin(BlockPosUtil.class)
public class MixinBlockPosUtil {

    /**
     * @author Cardinalstar16
     * @reason Performance
     */
    @Overwrite(remap = false)
    public static long packToLong(int x, int y, int z) {
        return Coords.key(x, y, z);
    }

    /**
     * @author Cardinalstar16
     * @reason Performance
     */
    @Overwrite(remap = false)
    public static int getX(long packed) {
        return Coords.x(packed);
    }

    /**
     * @author Cardinalstar16
     * @reason Performance
     */
    @Overwrite(remap = false)
    public static int getY(long packed) {
        return Coords.y(packed);
    }

    /**
     * @author Cardinalstar16
     * @reason Performance
     */
    @Overwrite(remap = false)
    public static int getZ(long packed) {
        return Coords.z(packed);
    }
}
