package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExtendedBlockStorage.class)
public class MixinExtendedBlockStorage {

    @Unique
    private Block prevBlock = Blocks.air;
    @Unique
    private int prevId = 0;

    @Redirect(
        method = "getBlockByExtId",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getBlockById(I)Lnet/minecraft/block/Block;"))
    public Block optimizeGetBlock(int id) {
        if (id == prevId) return prevBlock;

        prevId = id;
        return prevBlock = Block.getBlockById(id);
    }
}
