package com.cardinalstar.cubicchunks.world.worldgen.compat;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubePopulator;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.blockview.CubeBlockView;
import com.cardinalstar.cubicchunks.world.cube.blockview.IMutableBlockView;
import com.gtnewhorizon.gtnhlib.util.data.LazyBlock;
import ganymedes01.etfuturum.api.DeepslateOreRegistry;
import ganymedes01.etfuturum.api.mappings.RegistryMapping;

public class DeepslateCubePopulator implements ICubePopulator {

    private static final LazyBlock DEEPSLATE = new LazyBlock(Mods.EtFuturumRequiem, "deepslate");

    @Override
    public void populate(World world, Cube cube) {
        if (cube.getY() >= 0) return;

        IMutableBlockView blocks = new CubeBlockView(cube);

        for (Vector3ic v : blocks.getBounds()) {
            Block block = blocks.getBlock(v.x(), v.y(), v.z());
            int meta = blocks.getBlockMetadata(v.x(), v.y(), v.z());

            if (block == Blocks.stone) {
                block = DEEPSLATE.getBlock();
                meta = 0;
            } else {
                RegistryMapping<Block> replacement = DeepslateOreRegistry.getOre(block, meta);

                if (replacement != null) {
                    block = replacement.getObject();
                    meta = replacement.getMeta();
                }
            }

            blocks.setBlock(v.x(), v.y(), v.z(), block);
            blocks.setBlockMetadata(v.x(), v.y(), v.z(), meta);
        }
    }
}
