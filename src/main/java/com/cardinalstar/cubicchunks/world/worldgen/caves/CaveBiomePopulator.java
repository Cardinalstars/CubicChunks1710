package com.cardinalstar.cubicchunks.world.worldgen.caves;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import net.minecraftforge.common.util.ForgeDirection;

import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubePopulator;
import com.cardinalstar.cubicchunks.biomes.BiomeHumidity;
import com.cardinalstar.cubicchunks.biomes.BiomeTemperature;
import com.cardinalstar.cubicchunks.biomes.CCBiomeGenBase;
import com.cardinalstar.cubicchunks.biomes.CCBiomes;
import com.cardinalstar.cubicchunks.biomes.CubicBiomeRegistry;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.blockview.CubeBlockView;
import com.cardinalstar.cubicchunks.world.worldgen.data.NoisePrecalculator;
import com.cardinalstar.cubicchunks.world.worldgen.data.SamplerFactory;
import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.OctavesSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.ScaledNoise;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;

public class CaveBiomePopulator implements ICubePopulator {

    private static final int BIOME_DEPTH = 80;

    private enum Layers implements SamplerFactory {
        Temperature {

            @Override
            public NoiseSampler createSampler(Random rng) {
                return new ScaledNoise(new OctavesSampler(rng, 5), 0.01);
            }
        },
        Humidity {

            @Override
            public NoiseSampler createSampler(Random rng) {
                return new ScaledNoise(new OctavesSampler(rng, 5), 0.01);
            }
        },
        BiomeDepth {

            @Override
            public NoiseSampler createSampler(Random rng) {
                return new ScaledNoise(new OctavesSampler(rng, 3), 0.1);
            }
        },
        DecorationNoise {

            @Override
            public NoiseSampler createSampler(Random rng) {
                return new ScaledNoise(new OctavesSampler(rng, 6), 0.1);
            }
        },
    }

    private final NoisePrecalculator<Layers> noise = new NoisePrecalculator<>(Layers.class, 2);

    @Override
    public void prepopulate(World world, CubePos pos) {
        noise.submitPrecalculate(world, pos.getX(), pos.getY(), pos.getZ(), data -> {
            ObjectArraySet<CCBiomeGenBase> biomes = new ObjectArraySet<>();

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BiomeTemperature temp = BiomeTemperature.get(data.sample(Layers.Temperature, x, y, z) * 0.25 + 0.5);
                        BiomeHumidity humidity = BiomeHumidity.get(data.sample(Layers.Humidity, x, y, z) * 0.25 + 0.5);

                        CCBiomeGenBase biome = CubicBiomeRegistry.get(temp, humidity);

                        if (biome == null) biome = CCBiomes.UNDERGROUND.getBiomeIfEnabled();

                        if (biome != null && biomes.add(biome)) {
                            biome.predecorate(world, pos);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void populate(World world, Cube cube) {
        var data = noise.takeSampler(world, cube.getX(), cube.getY(), cube.getZ());

        ObjectArraySet<CCBiomeGenBase> biomes = new ObjectArraySet<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int undergroundThreshold = cube.getColumn().getHeightValue(x, z);

                undergroundThreshold -= BIOME_DEPTH;
                undergroundThreshold -= (int) (data.sample(Layers.BiomeDepth, x, z) * 10);

                if (undergroundThreshold < (cube.getY() + 1) << 4) continue;

                for (int y = 0; y < 16; y++) {
                    if (undergroundThreshold < (cube.getY() << 4) + y) continue;

                    BiomeTemperature temp = BiomeTemperature.get(data.sample(Layers.Temperature, x, y, z) * 0.25 + 0.5);
                    BiomeHumidity humidity = BiomeHumidity.get(data.sample(Layers.Humidity, x, y, z) * 0.25 + 0.5);

                    CCBiomeGenBase biome = CubicBiomeRegistry.get(temp, humidity);

                    if (biome == null) biome = CCBiomes.UNDERGROUND.getBiomeIfEnabled();

                    if (biome != null) {
                        biomes.add(biome);
                        cube.setBiome(x, y, z, biome);
                    }
                }
            }
        }

        CubeBlockView view = new CubeBlockView(cube);

        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    if (view.getBlock(x, y, z) != Blocks.stone) continue;
                    if (!(cube.getBiome(x, y, z) instanceof CCBiomeGenBase biome)) continue;

                    boolean surface = false;

                    for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                        int wX = cube.getX() * 16 + x + dir.offsetX;
                        int wY = cube.getY() * 16 + y + dir.offsetY;
                        int wZ = cube.getZ() * 16 + z + dir.offsetZ;

                        if (world.isAirBlock(wX, wY, wZ)) {
                            surface = true;
                            break;
                        }
                    }

                    double noise = data.sample(Layers.DecorationNoise, x, y, z) * 0.25 + 0.5;

                    ImmutableBlockMeta replacement = surface ? biome.getSurface(noise) : biome.getFiller(noise);

                    if (replacement == null) continue;

                    view.setBlock(x, y, z, replacement);
                }
            }
        }

        for (CCBiomeGenBase def : biomes) {
            def.decorate(world, cube);
        }
    }
}
