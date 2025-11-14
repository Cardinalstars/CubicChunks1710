package com.cardinalstar.cubicchunks.world.worldgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;

import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.util.XSTR;
import com.cardinalstar.cubicchunks.worldgen.VanillaWorldGenerator;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import com.gtnewhorizon.gtnhlib.util.data.LazyBlock;

public class MapGenCavesCubic extends FeatureCubicGenerator<MapGenCavesCubic.CaveSeed, VanillaWorldGenerator> {

    private final XSTR caveRandom = new XSTR(0);

    public MapGenCavesCubic() {
        super(8);
    }

    @Override
    protected void getSeedsImpl(Random rng, int cubeX, int cubeY, int cubeZ, List<CaveSeed> seeds) {
        if (rng.nextInt(4) != 0) return;

        double offsetX = cubeX * 16 + rng.nextInt(16);
        double offsetY = cubeY * 16 + rng.nextInt(16);
        double offsetZ = cubeZ * 16 + rng.nextInt(16);

        int armCount = 1;

        if (rng.nextInt(16) == 0) {
            seeds.add(
                new CaveSeed(
                    rng.nextLong(),
                    offsetX,
                    offsetY,
                    offsetZ,
                    1.0F + rng.nextFloat() * 6.0F,
                    0.0F,
                    0.0F,
                    -1,
                    -1,
                    0.5D));

            armCount += rng.nextInt(4);
        }

        for (int armIndex = 0; armIndex < armCount; ++armIndex) {
            float marchYaw = rng.nextFloat() * (float) Math.PI * 2.0F;
            float marchPitch = (rng.nextFloat() - 0.5F) * 2.0F / 8.0F;
            float caveLength = rng.nextFloat() * 2.0F + rng.nextFloat();

            if (rng.nextInt(10) == 0) {
                caveLength *= rng.nextFloat() * rng.nextFloat() * 3.0F + 1.0F;
            }

            seeds.add(
                new CaveSeed(rng.nextLong(), offsetX, offsetY, offsetZ, caveLength, marchYaw, marchPitch, 0, 0, 1.0D));
        }
    }

    @Override
    protected int getSeedX(CaveSeed caveSeed) {
        return (int) caveSeed.offsetX;
    }

    @Override
    protected int getSeedY(CaveSeed caveSeed) {
        return (int) caveSeed.offsetY;
    }

    @Override
    protected int getSeedZ(CaveSeed caveSeed) {
        return (int) caveSeed.offsetZ;
    }

    @Override
    protected Collection<CaveSeed> getSeedBranches(CaveSeed caveSeed) {
        return caveSeed.branches;
    }

    @Override
    protected boolean shouldGenerate(WorldView worldView, WorldgenFeature<CaveSeed> feature) {
        Box box = worldView.getBounds();

        return !scanOuterBoxForWater(worldView, box.getX1(), box.getX2(), box.getY1(), box.getY2(), box.getZ1(), box.getZ2());
    }

    private static boolean scanOuterBoxForWater(WorldView worldView, int xmin, int xmax, int zmin, int zmax, int ymax,
        int ymin) {
        int waterCount = 0;

        for (int x = xmin; x < xmax; ++x) {
            for (int z = zmin; z < zmax; ++z) {
                for (int y = ymax + 1; y >= ymin - 1; --y) {
                    Block block = worldView.getBlock(x, y, z);

                    // Only check for still water, since flowing water generates in caves
                    if (block == Blocks.water) {
                        waterCount++;
                    }

                    // If there are more than 4 water source blocks, we can be reasonably sure that we've hit a large
                    // body of water (aquifer, ocean, or river)
                    if (waterCount > 4) {
                        return true;
                    }

                    // If we've just scanned the top block (y == ymax + 1) and we aren't scanning the walls, then jump
                    // to the bottom (y == ymin)
                    if (y != ymin - 1 && x != xmin && x != xmax - 1 && z != zmin && z != zmax - 1) {
                        y = ymin;
                    }
                }
            }
        }

        return false;
    }

    // Exception biomes to make sure we generate like vanilla
    private boolean isExceptionBiome(BiomeGenBase biome) {
        if (biome == BiomeGenBase.mushroomIsland) return true;
        if (biome == BiomeGenBase.beach) return true;
        return biome == BiomeGenBase.desert;
    }

    @Override
    protected void place(WorldView worldView, WorldgenFeature<CaveSeed> feature, int x, int y, int z, ImmutableBlockMeta bm) {
        BiomeGenBase biome = worldView.getBiomeGenForBlock(x, y, z);
        Block block = worldView.getBlock(x, y, z);

        Block top = (isExceptionBiome(biome) ? Blocks.grass : biome.topBlock);
        Block filler = (isExceptionBiome(biome) ? Blocks.dirt : biome.fillerBlock);

        if (block == Blocks.stone || block == Blocks.bedrock || block == filler || block == top) {
            worldView.setBlock(x, y, z, bm);
        }
    }

    private static final LazyBlock AIR = new LazyBlock(Mods.Minecraft, () -> Blocks.air, 0);

    @Override
    protected void generateSeed(CaveSeed cave, WorldgenFeature<CaveSeed> feature) {
        long seed = cave.seed;
        double offsetX = cave.offsetX;
        double offsetY = cave.offsetY;
        double offsetZ = cave.offsetZ;
        float caveLengthChunk = cave.caveLength;
        float marchYaw = cave.marchYaw;
        float marchPitch = cave.marchPitch;
        int stepIndex = cave.stepIndex;
        int stepCount = cave.stepCount;
        double height = cave.height;

        float deltaYaw = 0.0F;
        float deltaPitch = 0.0F;

        caveRandom.setSeed(seed);

        if (stepCount <= 0) {
            int maxBlockRange = this.range * 16 - 16;
            stepCount = maxBlockRange - caveRandom.nextInt(maxBlockRange / 4);
        }

        boolean isCavern = false;

        if (stepIndex == -1) {
            stepIndex = stepCount / 2;
            isCavern = true;
        }

        int teePoint = caveRandom.nextInt(stepCount / 2) + stepCount / 4;

        boolean pitchModifier = caveRandom.nextInt(6) == 0;

        for (; stepIndex < stepCount; ++stepIndex) {
            double sizeXZChunk = 1.5D
                + (double) (MathHelper.sin((float) stepIndex * (float) Math.PI / (float) stepCount) * caveLengthChunk
                    * 1.0F);
            double sizeYChunk = sizeXZChunk * height;

            {
                float multXZ = MathHelper.cos(marchPitch);
                float deltaY = MathHelper.sin(marchPitch);
                offsetX += MathHelper.cos(marchYaw) * multXZ;
                offsetY += deltaY;
                offsetZ += MathHelper.sin(marchYaw) * multXZ;
            }

            if (pitchModifier) {
                marchPitch *= 0.92F;
            } else {
                marchPitch *= 0.7F;
            }

            marchPitch += deltaPitch * 0.1F;
            marchYaw += deltaYaw * 0.1F;

            deltaPitch *= 0.9F;
            deltaYaw *= 0.75F;

            deltaPitch += (caveRandom.nextFloat() - caveRandom.nextFloat()) * caveRandom.nextFloat() * 2.0F;
            deltaYaw += (caveRandom.nextFloat() - caveRandom.nextFloat()) * caveRandom.nextFloat() * 4.0F;

            if (!isCavern && stepIndex == teePoint && caveLengthChunk > 1.0F && stepCount > 0) {
                cave.branches.add(
                    new CaveSeed(
                        caveRandom.nextLong(),
                        offsetX,
                        offsetY,
                        offsetZ,
                        caveRandom.nextFloat() * 0.5F + 0.5F,
                        marchYaw - ((float) Math.PI / 2F),
                        marchPitch / 3.0F,
                        stepIndex,
                        stepCount,
                        1.0D));
                cave.branches.add(
                    new CaveSeed(
                        caveRandom.nextLong(),
                        offsetX,
                        offsetY,
                        offsetZ,
                        caveRandom.nextFloat() * 0.5F + 0.5F,
                        marchYaw + ((float) Math.PI / 2F),
                        marchPitch / 3.0F,
                        stepIndex,
                        stepCount,
                        1.0D));

                return;
            }

            if (isCavern || caveRandom.nextInt(4) != 0) {
                int xmin = MathHelper.floor_double(offsetX - sizeXZChunk) - 1;
                int xmax = MathHelper.floor_double(offsetX + sizeXZChunk) + 1;
                int ymin = MathHelper.floor_double(offsetY - sizeYChunk) - 1;
                int ymax = MathHelper.floor_double(offsetY + sizeYChunk) + 1;
                int zmin = MathHelper.floor_double(offsetZ - sizeXZChunk) - 1;
                int zmax = MathHelper.floor_double(offsetZ + sizeXZChunk) + 1;

                for (int globalX = xmin; globalX < xmax; ++globalX) {
                    double ellipseX = ((double) globalX + 0.5D - offsetX) / sizeXZChunk;

                    for (int globalZ = zmin; globalZ < zmax; ++globalZ) {
                        double ellipseZ = ((double) globalZ + 0.5D - offsetZ) / sizeXZChunk;

                        if (ellipseX * ellipseX + ellipseZ * ellipseZ < 1.0D) {
                            for (int globalY = ymax - 1; globalY >= ymin; --globalY) {
                                double ellipseY = ((double) globalY + 0.5D - offsetY) / sizeYChunk;

                                if (ellipseY > -0.7D
                                    && ellipseX * ellipseX + ellipseY * ellipseY + ellipseZ * ellipseZ < 1.0D) {
                                    feature.setBlock(globalX, globalY, globalZ, AIR);
                                }
                            }
                        }
                    }
                }

                if (isCavern) {
                    break;
                }
            }
        }
    }

    protected static class CaveSeed {

        private final long seed;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final float caveLength;
        private final float marchYaw;
        private final float marchPitch;
        private final int stepIndex;
        private final int stepCount;
        private final double height;

        private final List<CaveSeed> branches = new ArrayList<>();

        public CaveSeed(long seed, double offsetX, double offsetY, double offsetZ, float caveLength, float marchYaw,
            float marchPitch, int stepIndex, int stepCount, double height) {
            this.seed = seed;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.caveLength = caveLength;
            this.marchYaw = marchYaw;
            this.marchPitch = marchPitch;
            this.stepIndex = stepIndex;
            this.stepCount = stepCount;
            this.height = height;
        }
    }
}
