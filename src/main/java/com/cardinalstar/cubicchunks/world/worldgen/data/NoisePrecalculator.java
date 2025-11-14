package com.cardinalstar.cubicchunks.world.worldgen.data;

import java.util.EnumMap;
import java.util.concurrent.TimeUnit;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.async.TaskPool;
import com.cardinalstar.cubicchunks.async.TaskPool.ITask;
import com.cardinalstar.cubicchunks.util.ObjectPooler;
import com.cardinalstar.cubicchunks.util.XSTR;
import com.cardinalstar.cubicchunks.world.worldgen.noise.NoiseSampler;
import com.github.bsideup.jabel.Desugar;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class NoisePrecalculator<TLayer extends Enum<TLayer> & SamplerFactory> {

    private final ObjectPooler<NoiseData> dataPool = new ObjectPooler<>(NoiseData::new, null, 1024);

    private final Cache<TaskKey, NoiseData> cache = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .maximumSize(1024)
        .removalListener(notification -> {
            if (notification.getCause() != RemovalCause.EXPLICIT) {
                //noinspection unchecked
                releaseData((NoiseData) notification.getValue());
            }
        })
        .build();

    private final Int2ObjectOpenHashMap<EnumMap<TLayer, NoiseSampler>> samplers = new Int2ObjectOpenHashMap<>();
    private final Class<TLayer> layers;
    private final int layerCount;
    private final int seed;

    public NoisePrecalculator(Class<TLayer> layers, int seed) {
        this.layers = layers;
        this.layerCount = layers.getEnumConstants().length;
        this.seed = seed;
    }

    private EnumMap<TLayer, NoiseSampler> createSamplers(long seed, String dimName) {
        EnumMap<TLayer, NoiseSampler> samplers = new EnumMap<>(layers);

        for (TLayer layer : layers.getEnumConstants()) {
            long hash = Fnv1a64.initialState();
            hash = Fnv1a64.hashStep(hash, seed);
            hash = Fnv1a64.hashStep(hash, dimName);
            hash = Fnv1a64.hashStep(hash, layer.name());
            hash = Fnv1a64.hashStep(hash, this.seed);

            samplers.put(layer, layer.createSampler(new XSTR(hash)));
        }

        return samplers;
    }

    private NoiseData getData() {
        synchronized (dataPool) {
            return dataPool.getInstance();
        }
    }

    public void releaseData(NoiseData data) {
        synchronized (dataPool) {
            dataPool.releaseInstance(data);
        }
    }

    private @NotNull EnumMap<TLayer, NoiseSampler> getSamplers(World world) {
        int worldId = world.provider.dimensionId;

        EnumMap<TLayer, NoiseSampler> samplers = this.samplers.get(worldId);

        if (samplers == null) {
            samplers = createSamplers(world.getSeed(), world.provider.getDimensionName());
            this.samplers.put(worldId, samplers);
        }

        return samplers;
    }

    public void submitPrecalculate(World world, int cubeX, int cubeY, int cubeZ) {
        EnumMap<TLayer, NoiseSampler> samplers = getSamplers(world);

        TaskKey key = new TaskKey(world.provider.dimensionId, cubeX, cubeY, cubeZ);

        TaskPool.submit(new Task3D(samplers, key));
    }

    public NoiseData takeSampler(World world, int cubeX, int cubeY, int cubeZ) {
        TaskKey key = new TaskKey(world.provider.dimensionId, cubeX, cubeY, cubeZ);

        NoiseData data = cache.asMap().remove(key);

        if (data == null) {
            data = getData();

            compute3d(getSamplers(world), cubeX << 4, cubeY << 4, cubeZ << 4, data);
        }

        return data;
    }

    private void compute3d(EnumMap<TLayer, NoiseSampler> samplers, int wx, int wy, int wz, NoiseData data) {
        samplers.forEach((layer, sampler) -> {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        double value = sampler.sample(wx + x, wy + y, wz + z);

                        data.put(layer, x, y, z, value);
                    }
                }
            }
        });
    }

    private class Task3D implements ITask<Void> {

        private final EnumMap<TLayer, NoiseSampler> samplers;
        private final TaskKey key;

        public Task3D(EnumMap<TLayer, NoiseSampler> samplers, TaskKey key) {
            this.samplers = samplers;
            this.key = key;
        }

        @Override
        public Void call() {
            NoiseData data = getData();

            compute3d(samplers, key.x() << 4, key.y() << 4, key.z() << 4, data);

            cache.put(key, data);

            return null;
        }
    }

    @Desugar
    private record TaskKey(int worldId, int x, int y, int z) {

    }

    public class NoiseData {

        private final double[] data = new double[16 * 16 * 16 * layerCount];

        final void put(TLayer layer, int x, int y, double value) {
            data[index(layer, x, y, 0)] = value;
        }

        final void put(TLayer layer, int x, int y, int z, double value) {
            data[index(layer, x, y, z)] = value;
        }

        public final double sample(TLayer layer, int x, int y) {
            return sample(layer, x, y, 0);
        }

        public final double sample(TLayer layer, int x, int y, int z) {
            return data[index(layer, x, y, z)];
        }

        private static <TLayer extends Enum<TLayer>> int index(TLayer layer, int x, int y, int z) {
            return x | (y << 4) | (z << 8) | (layer.ordinal() << 12);
        }
    }
}
