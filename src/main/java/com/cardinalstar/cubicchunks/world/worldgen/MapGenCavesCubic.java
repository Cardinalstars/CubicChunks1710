package com.cardinalstar.cubicchunks.world.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;

import org.joml.Vector3i;
import org.joml.Vector3ic;

import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.api.XYZMap;
import com.cardinalstar.cubicchunks.api.util.Box;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.XSTR;
import com.cardinalstar.cubicchunks.worldgen.VanillaCompatibilityGenerator;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

public class MapGenCavesCubic extends SeedBasedCubicPopulator<MapGenCavesCubic.CaveSeed, VanillaCompatibilityGenerator> {

    private final XSTR caveRandom = new XSTR(0);

    public MapGenCavesCubic() {
        super(8);
    }

    @Override
    protected void getSeeds(Random rng, int cubeX, int cubeY, int cubeZ, List<CaveSeed> seeds) {
        if (rng.nextInt(4) != 0) return;

        double offsetX = cubeX * 16 + rng.nextInt(16);
        double offsetY = cubeY * 16 + rng.nextInt(16);
        double offsetZ = cubeZ * 16 + rng.nextInt(16);

        int armCount = 1;

        if (rng.nextInt(16) == 0) {
            seeds.add(new CaveSeed(rng.nextLong(), offsetX, offsetY, offsetZ, 1.0F + rng.nextFloat() * 6.0F, 0.0F, 0.0F, -1, -1, 0.5D));

            armCount += rng.nextInt(4);
        }

        for (int armIndex = 0; armIndex < armCount; ++armIndex) {
            float marchYaw = rng.nextFloat() * (float)Math.PI * 2.0F;
            float marchPitch = (rng.nextFloat() - 0.5F) * 2.0F / 8.0F;
            float caveLength = rng.nextFloat() * 2.0F + rng.nextFloat();

            if (rng.nextInt(10) == 0) {
                caveLength *= rng.nextFloat() * rng.nextFloat() * 3.0F + 1.0F;
            }

            seeds.add(new CaveSeed(rng.nextLong(), offsetX, offsetY, offsetZ, caveLength, marchYaw, marchPitch, 0, 0, 1.0D));
        }
    }

    @Override
    protected void generate(Random rng, CaveSeed caveSeed, WorldView worldView) {
        generateCave(caveSeed, worldView);
    }

    protected void generateCave(CaveSeed caveSeed, WorldView worldView) {
        Box box = worldView.getBounds();

        if (!scanOuterBoxForWater(worldView, box.getX1(), box.getX2(), box.getY1(), box.getY2(), box.getZ1(), box.getZ2())) {
            CubePos pos = worldView.getCube();

            for (Vector3ic v : caveSeed.getDigs(pos.getX(), pos.getY(), pos.getZ())) {
                int x = v.x() + box.getX1();
                int y = v.y() + box.getY1();
                int z = v.z() + box.getZ1();

                digBlock(worldView, x, y, z);
            }
        }

        for (CaveSeed branch : caveSeed.branches) {
            generateCave(branch, worldView);
        }
    }

    private static boolean scanOuterBoxForWater(WorldView worldView, int xmin, int xmax, int zmin, int zmax, int ymax, int ymin) {
        int waterCount = 0;

        for (int x = xmin; x < xmax; ++x)
        {
            for (int z = zmin; z < zmax; ++z)
            {
                for (int y = ymax + 1; y >= ymin - 1; --y)
                {
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
                    if (y != ymin - 1 && x != xmin && x != xmax - 1 && z != zmin && z != zmax - 1)
                    {
                        y = ymin;
                    }
                }
            }
        }

        return false;
    }

    //Exception biomes to make sure we generate like vanilla
    private boolean isExceptionBiome(BiomeGenBase biome)
    {
        if (biome == BiomeGenBase.mushroomIsland) return true;
        if (biome == BiomeGenBase.beach) return true;
        return biome == BiomeGenBase.desert;
    }

    /**
     * Digs out the current block, default implementation removes stone, filler, and top block
     * Sets the block to lava if y is less then 10, and air other wise.
     * If setting to air, it also checks to see if we've broken the surface and if so
     * tries to make the floor the biome's top block
     *
     * @param worldView The world view
     * @param x global X position
     * @param y global Y position
     * @param z global Z position
     */
    protected void digBlock(WorldView worldView, int x, int y, int z)
    {
        BiomeGenBase biome = worldView.getBiomeGenForBlock(x, y, z);
        Block block = worldView.getBlock(x, y, z);

        Block top    = (isExceptionBiome(biome) ? Blocks.grass : biome.topBlock);
        Block filler = (isExceptionBiome(biome) ? Blocks.dirt  : biome.fillerBlock);

        if (block == Blocks.stone || block == Blocks.bedrock || block == filler || block == top)
        {
            if (false)
            {
                worldView.setBlock(x, y, z, Blocks.lava, 0);
            }
            else
            {
                worldView.setBlock(x, y, z, Blocks.air, 0);
            }
        }
    }

    protected void walkCave(CaveSeed cave) {
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

        if (stepCount <= 0)
        {
            int maxBlockRange = this.range * 16 - 16;
            stepCount = maxBlockRange - caveRandom.nextInt(maxBlockRange / 4);
        }

        boolean isCavern = false;

        if (stepIndex == -1)
        {
            stepIndex = stepCount / 2;
            isCavern = true;
        }

        int teePoint = caveRandom.nextInt(stepCount / 2) + stepCount / 4;

        boolean pitchModifier = caveRandom.nextInt(6) == 0;

        for (; stepIndex < stepCount; ++stepIndex)
        {
            double sizeXZChunk = 1.5D + (double)(MathHelper.sin((float)stepIndex * (float)Math.PI / (float)stepCount) * caveLengthChunk * 1.0F);
            double sizeYChunk = sizeXZChunk * height;

            {
                float multXZ = MathHelper.cos(marchPitch);
                float deltaY = MathHelper.sin(marchPitch);
                offsetX += MathHelper.cos(marchYaw) * multXZ;
                offsetY += deltaY;
                offsetZ += MathHelper.sin(marchYaw) * multXZ;
            }

            if (pitchModifier)
            {
                marchPitch *= 0.92F;
            }
            else
            {
                marchPitch *= 0.7F;
            }

            marchPitch += deltaPitch * 0.1F;
            marchYaw += deltaYaw * 0.1F;

            deltaPitch *= 0.9F;
            deltaYaw *= 0.75F;

            deltaPitch += (caveRandom.nextFloat() - caveRandom.nextFloat()) * caveRandom.nextFloat() * 2.0F;
            deltaYaw += (caveRandom.nextFloat() - caveRandom.nextFloat()) * caveRandom.nextFloat() * 4.0F;

            if (!isCavern && stepIndex == teePoint && caveLengthChunk > 1.0F && stepCount > 0)
            {
                cave.branches.add(new CaveSeed(caveRandom.nextLong(), offsetX, offsetY, offsetZ, caveRandom.nextFloat() * 0.5F + 0.5F, marchYaw - ((float)Math.PI / 2F), marchPitch / 3.0F, stepIndex, stepCount, 1.0D));
                cave.branches.add(new CaveSeed(caveRandom.nextLong(), offsetX, offsetY, offsetZ, caveRandom.nextFloat() * 0.5F + 0.5F, marchYaw + ((float)Math.PI / 2F), marchPitch / 3.0F, stepIndex, stepCount, 1.0D));

                return;
            }

            if (isCavern || caveRandom.nextInt(4) != 0)
            {
                int xmin = MathHelper.floor_double(offsetX - sizeXZChunk) - 1;
                int xmax = MathHelper.floor_double(offsetX + sizeXZChunk) + 1;
                int ymin = MathHelper.floor_double(offsetY - sizeYChunk) - 1;
                int ymax = MathHelper.floor_double(offsetY + sizeYChunk) + 1;
                int zmin = MathHelper.floor_double(offsetZ - sizeXZChunk) - 1;
                int zmax = MathHelper.floor_double(offsetZ + sizeXZChunk) + 1;

                for (int globalX = xmin; globalX < xmax; ++globalX)
                {
                    double ellipseX = ((double)globalX + 0.5D - offsetX) / sizeXZChunk;

                    for (int globalZ = zmin; globalZ < zmax; ++globalZ)
                    {
                        double ellipseZ = ((double) globalZ + 0.5D - offsetZ) / sizeXZChunk;

                        if (ellipseX * ellipseX + ellipseZ * ellipseZ < 1.0D)
                        {
                            for (int globalY = ymax - 1; globalY >= ymin; --globalY)
                            {
                                double ellipseY = ((double) globalY + 0.5D - offsetY) / sizeYChunk;

                                if (ellipseY > -0.7D && ellipseX * ellipseX + ellipseY * ellipseY + ellipseZ * ellipseZ < 1.0D)
                                {
                                    cave.dig(globalX, globalY, globalZ);
                                }
                            }
                        }
                    }
                }

                if (isCavern)
                {
                    break;
                }
            }
        }
    }

    protected class CaveSeed {
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

        private final XYZMap<DigSet> pendingDigs = new XYZMap<>();

        private final Box.Mutable aabb;

        private final List<CaveSeed> branches = new ArrayList<>();

        public CaveSeed(long seed, double offsetX, double offsetY, double offsetZ, float caveLength, float marchYaw, float marchPitch, int stepIndex, int stepCount, double height) {
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

            aabb = new Box.Mutable((int) offsetX, (int) offsetY, (int) offsetZ, (int) offsetX, (int) offsetY, (int) offsetZ);

            walkCave(this);
        }

        public void dig(int x, int y, int z) {
            aabb.expand(x, y, z);

            DigSet digs = pendingDigs.get(Coords.blockToCube(x), Coords.blockToCube(y), Coords.blockToCube(z));

            if (digs == null) {
                pendingDigs.put(digs = new DigSet(Coords.blockToCube(x), Coords.blockToCube(y), Coords.blockToCube(z)));
            }

            digs.dig(Coords.blockToLocal(x), Coords.blockToLocal(y), Coords.blockToLocal(z));
        }

        public boolean affects(int cubeX, int cubeY, int cubeZ) {
            return aabb.containsCube(cubeX, cubeY, cubeZ);
        }

        public Iterable<Vector3ic> getDigs(int cubeX, int cubeY, int cubeZ) {
            DigSet digs = pendingDigs.get(cubeX, cubeY, cubeZ);

            return digs == null ? Collections.emptyList() : digs;
        }
    }

    protected static class DigSet implements Iterable<Vector3ic>, XYZAddressable {

        private final int x, y, z;

        private final LongLinkedOpenHashSet digs = new LongLinkedOpenHashSet();

        public DigSet(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
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

        public void dig(int x, int y, int z) {
            digs.add(CoordinatePacker.pack(x, y, z));
        }

        @Override
        @Nonnull
        public Iterator<Vector3ic> iterator() {
            return new Iterator<>() {

                private final LongIterator iter = digs.iterator();
                private final Vector3i v = new Vector3i();

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Vector3ic next() {
                    long k = iter.nextLong();

                    v.x = CoordinatePacker.unpackX(k);
                    v.y = CoordinatePacker.unpackY(k);
                    v.z = CoordinatePacker.unpackZ(k);

                    return v;
                }
            };
        }
    }
}
