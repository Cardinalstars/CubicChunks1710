package com.cardinalstar.cubicchunks.biomes;

import java.util.Random;

import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.api.worldgen.decoration.ICubePopulator;
import cpw.mods.fml.common.eventhandler.Event;

public class DecorateBiome3DEvent extends Event {

    public final World world;
    public final Random rand;
    public final int cubeX;
    public final int cubeY;
    public final int cubeZ;

    public DecorateBiome3DEvent(World world, Random rand, int cubeX, int cubeY, int cubeZ) {
        this.world = world;
        this.rand = rand;
        this.cubeX = cubeX;
        this.cubeY = cubeY;
        this.cubeZ = cubeZ;
    }

    /**
     * This event is fired before a cube is decorated with a biome feature.
     */
    public static class Pre extends DecorateBiome3DEvent {

        public Pre(World world, Random rand, int cubeX, int cubeY, int cubeZ) {
            super(world, rand, cubeX, cubeY, cubeZ);
        }
    }

    /**
     * This event is fired after a cube is decorated with a biome feature.
     */
    public static class Post extends DecorateBiome3DEvent {

        public Post(World world, Random rand, int cubeX, int cubeY, int cubeZ) {
            super(world, rand, cubeX, cubeY, cubeZ);
        }
    }

    /**
     * This event is fired when a cube is decorated with a biome feature.
     *
     * You can set the result to DENY to prevent the default biome decoration.
     */
    @HasResult
    public static class Decorate extends DecorateBiome3DEvent {

        public final CCBiomeGenBase biome;
        public final ICubePopulator populator;

        public Decorate(World world, Random rand, int cubeX, int cubeY, int cubeZ, CCBiomeGenBase biome, ICubePopulator populator) {
            super(world, rand, cubeX, cubeY, cubeZ);
            this.biome = biome;
            this.populator = populator;
        }
    }
}
