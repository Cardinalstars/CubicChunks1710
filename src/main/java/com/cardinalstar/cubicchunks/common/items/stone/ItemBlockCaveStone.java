package com.cardinalstar.cubicchunks.common.items.stone;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cardinalstar.cubicchunks.common.blocks.stone.BlockCaveStone;
import com.cardinalstar.cubicchunks.common.blocks.stone.BlockCaveStone.StoneVariant;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemBlockCaveStone extends ItemBlock {

    private final BlockCaveStone stone;

    public ItemBlockCaveStone(Block stone) {
        super(stone);
        this.stone = (BlockCaveStone) stone;
        setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int meta) {
        return meta;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        int meta = stack.getItemDamage();

        StoneVariant variant = stone.getVariant(meta);

        return StatCollector.translateToLocalFormatted("cc.blocks.stone-type." + variant.name, StatCollector.translateToLocal("cc.blocks.cave-stone." + stone.name));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void getSubItems(Item self, CreativeTabs tab, List<ItemStack> stacks) {
        for (int i = 0; i < stone.variants.length; i++) {
            if (stone.variants[i] != null) {
                stacks.add(new ItemStack(self, 1, i));
            }
        }
    }
}
