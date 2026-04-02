package com.cardinalstar.cubicchunks.common.blocks.stone;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.ArrayUtils;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.common.items.stone.ItemBlockCaveStone;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lombok.Data;

public class BlockCaveStone extends Block {

    @Data
    public static class StoneVariant {
        public final float hardness;
        public final String name, side;
        public StoneVariant dropped;
    }

    public static final StoneVariant SMOOTH = new StoneVariant(1f, "smooth", null);
    public static final StoneVariant SMOOTH_SIDED = new StoneVariant(1f, "smooth_top", "smooth_side");
    public static final StoneVariant SMOOTH_MOSSY = new StoneVariant(0.8f, "smooth_mossy", null);
    public static final StoneVariant COBBLE = new StoneVariant(1.5f, "cobble", null);
    public static final StoneVariant COBBLE_MOSSY = new StoneVariant(1.3f, "cobble_mossy", null);
    public static final StoneVariant BRICKS = new StoneVariant(0.8f, "bricks", null);
    public static final StoneVariant BRICKS_MOSSY = new StoneVariant(0.6f, "bricks_mossy", null);
    public static final StoneVariant BRICKS_CRACKED = new StoneVariant(0.5f, "bricks_cracked", null);
    public static final StoneVariant BRICKS_POLISHED = new StoneVariant(1.5f, "bricks_polished", null);

    public static final StoneVariant[] NORMAL = { SMOOTH, COBBLE, COBBLE_MOSSY, BRICKS, BRICKS_MOSSY, BRICKS_CRACKED,
        BRICKS_POLISHED, };

    public static final StoneVariant[] MOSSY = { SMOOTH, SMOOTH_MOSSY, COBBLE, COBBLE_MOSSY, BRICKS, BRICKS_MOSSY,
        BRICKS_CRACKED, BRICKS_POLISHED, };

    public static final StoneVariant[] SIDED = { SMOOTH_SIDED, COBBLE, COBBLE_MOSSY, BRICKS, BRICKS_MOSSY,
        BRICKS_CRACKED, BRICKS_POLISHED, };

    static {
        SMOOTH.setDropped(COBBLE);
        SMOOTH_SIDED.setDropped(COBBLE);
        SMOOTH_MOSSY.setDropped(COBBLE_MOSSY);
    }

    public final String name;
    public final float baseHardness;

    public final StoneVariant[] variants;
    public final IIcon[] icons, sideIcons;

    public static final BlockCaveStone TRAVERTINE = new BlockCaveStone("travertine", 0.8f, MOSSY);
    public static final BlockCaveStone PHYLLITE = new BlockCaveStone("phyllite", 0.4f, NORMAL);
    public static final BlockCaveStone SCHIST = new BlockCaveStone("schist", 1f, SIDED);
    public static final BlockCaveStone GNEISS = new BlockCaveStone("gneiss", 1.5f, SIDED);

    protected BlockCaveStone(String name, float baseHardness, StoneVariant[] variants) {
        super(Material.rock);
        this.name = name;
        this.baseHardness = baseHardness;
        this.variants = variants;

        icons = new IIcon[variants.length];
        sideIcons = new IIcon[variants.length];

        setBlockName(name);
    }

    public void register() {
        GameRegistry.registerBlock(this, ItemBlockCaveStone.class, name);
    }

    @Override
    public int damageDropped(int meta) {
        if (meta < 0 || meta >= variants.length) return meta;

        StoneVariant variant = getVariant(meta);

        if (variant.dropped != null) {
            return ArrayUtils.indexOf(variants, variant.dropped);
        } else {
            return meta;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister reg) {
        for (int i = 0; i < variants.length; i++) {
            StoneVariant variant = variants[i];

            icons[i] = reg.registerIcon(CubicChunks.MODID + ":cave_stone/" + name + "/" + variant.name);

            if (variant.side != null) {
                sideIcons[i] = reg.registerIcon(CubicChunks.MODID + ":cave_stone/" + name + "/" + variant.side);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int s, int meta) {
        if (meta < 0 || meta >= variants.length) return Blocks.stone.getIcon(0, 0);

        StoneVariant variant = variants[meta];

        boolean isSide = ForgeDirection.getOrientation(s).offsetY == 0;

        return variant.side != null && isSide ? sideIcons[meta] : icons[meta];
    }

    @Override
    public float getBlockHardness(World worldIn, int x, int y, int z) {
        int meta = worldIn.getBlockMetadata(x, y, z);

        StoneVariant variant = getVariant(meta);

        return variant.hardness * baseHardness;
    }

    @Override
    public float getExplosionResistance(Entity par1Entity, World world, int x, int y, int z, double explosionX,
        double explosionY, double explosionZ) {
        int meta = world.getBlockMetadata(x, y, z);

        StoneVariant variant = getVariant(meta);

        return variant.hardness * baseHardness;
    }

    @Override
    public float getExplosionResistance(Entity exploder) {
        return baseHardness;
    }

    public StoneVariant getVariant(int meta) {
        StoneVariant variant = meta < 0 || meta >= variants.length ? null : variants[meta];

        return variant == null ? variants[0] : variant;
    }
}
