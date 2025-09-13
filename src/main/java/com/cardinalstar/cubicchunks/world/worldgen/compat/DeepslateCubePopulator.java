package com.cardinalstar.cubicchunks.world.worldgen.compat;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.api.worldgen.populator.ICubicPopulator;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.blockview.CubeBlockView;
import com.cardinalstar.cubicchunks.world.cube.blockview.IMutableBlockView;
import com.gtnewhorizon.gtnhlib.util.data.LazyBlock;
import ganymedes01.etfuturum.api.DeepslateOreRegistry;
import ganymedes01.etfuturum.api.mappings.RegistryMapping;

public class DeepslateCubePopulator implements ICubicPopulator {

    private final LazyBlock DEEPSLATE = new LazyBlock(Mods.EtFuturumRequiem, "deepslate");

    @Override
    public void generate(World world, CubePos pos) {
        IMutableBlockView cube = new CubeBlockView((Cube) ((ICubicWorld) world).getCubeFromCubeCoords(pos));

        for (Vector3ic v : cube.getBounds()) {
            Block block = cube.getBlock(v.x(), v.y(), v.z());
            int meta = cube.getBlockMetadata(v.x(), v.y(), v.z());

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

            cube.setBlock(v.x(), v.y(), v.z(), block);
            cube.setBlockMetadata(v.x(), v.y(), v.z(), meta);
        }
    }
}
