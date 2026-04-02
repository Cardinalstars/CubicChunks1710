package com.cardinalstar.cubicchunks.biomes;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.biome.BiomeGenBase;

import org.jetbrains.annotations.Nullable;

import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubePopulator;
import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubicBiome;
import com.cardinalstar.cubicchunks.util.DependencyGraph;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import lombok.Getter;
import lombok.Setter;

public class CCBiomeGenBase extends BiomeGenBase implements ICubicBiome {

    public final DependencyGraph<ICubePopulator> decorators = new DependencyGraph<>();

    @Setter
    @Getter
    protected double interestingness;

    public CCBiomeGenBase(int biomeID) {
        super(biomeID);
    }

    @Override
    public BiomeDecorator createBiomeDecorator() {
        return null;
    }

    @Override
    public void decorate(World world, Random rng, int chunkX, int chunkZ) {
        for (int y = 0; y < 16; y++) {
            decorate(world, (Cube) ((ICubicWorld) world).getCubeFromCubeCoords(chunkX, y, chunkZ));
        }
    }

    @Override
    public void decorate(World world, Cube cube) {
        for (ICubePopulator decorator : decorators.sorted()) {
            decorator.populate(world, cube);
        }
    }

    @Override
    public String toString() {
        return "CCBiomeGenBase{" + biomeName + "}";
    }

    @Nullable
    public ImmutableBlockMeta getFiller(double noise) {
        return null;
    }

    @Nullable
    public ImmutableBlockMeta getSurface(double noise) {
        return null;
    }

    protected void register(BiomeTemperature[] temp, BiomeHumidity[] hum) {
        CubicBiomeRegistry.register(temp, hum, this);
    }
}
