package com.cardinalstar.cubicchunks.world.worldgen.vanilla;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.world.gen.NoiseGeneratorOctaves;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.async.TaskPool;
import com.cardinalstar.cubicchunks.async.TaskPool.ITaskExecutor;
import com.cardinalstar.cubicchunks.async.TaskPool.ITaskFuture;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.ObjectPooler;
import com.github.bsideup.jabel.Desugar;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

public class PrecalcedVanillaOctaves extends NoiseGeneratorOctaves implements PrecalculableNoise {

    private final NoiseGeneratorOctaves base;
    private boolean initialized = false;
    private int xspan, yspan, zspan;
    private double xscale, yscale, zscale;
    private int misses, misses2, hits;
    private AtomicInteger pres = new AtomicInteger();
    private long elapsed;
    private long lastMessage = System.nanoTime();

    private final ObjectPooler<NoiseData> dataPool = new ObjectPooler<>(NoiseData::new, null, 1024);

    private final Object paramLock = new Object();

    // private final Cache<TaskKey, NoiseData> cache = CacheBuilder.newBuilder()
    // .expireAfterWrite(5, TimeUnit.MINUTES)
    // .maximumSize(8192)
    // .removalListener(notification -> releaseData((NoiseData) notification.getValue()))
    // .build();

    private final Long2ObjectLinkedOpenHashMap<NoiseData> cache = new Long2ObjectLinkedOpenHashMap<>();

    private final ITaskExecutor<Task3D, NoiseData> noiseTaskExecutor;

    public PrecalcedVanillaOctaves(NoiseGeneratorOctaves base) {
        super(null, 0);
        this.base = base;

        noiseTaskExecutor = new ITaskExecutor<>() {

            @Override
            public void execute(List<ITaskFuture<Task3D, NoiseData>> tasks) {
                for (var future : tasks) {
                    Task3D task = future.getTask();

                    NoiseData data = task.run();

                    synchronized (cache) {
                        cache.putAndMoveToFirst(Coords.key(task.key.x, task.key.y, task.key.z), data);

                        while (cache.size() > 8192) cache.removeLast();
                    }

                    pres.addAndGet(1);

                    future.finish(data);
                }
            }

            @Override
            public boolean canMerge(List<ITaskFuture<Task3D, NoiseData>> tasks, Task3D task3D) {
                return tasks.size() < 32;
            }
        };
    }

    private static boolean diff(double a, double b) {
        return Math.abs(a - b) > 0.0001;
    }

    @Override
    public double[] generateNoiseOctaves(double[] data, int blockX, int blockY, int blockZ, int sx, int sy, int sz,
        double scaleX, double scaleY, double scaleZ) {
        long now = System.nanoTime();

        if ((now - lastMessage) > 5e9) {
            lastMessage = now;
            CubicChunks.LOGGER.info(
                "Hits: {} Misses: {} Pres: {} Per call: {}ms",
                hits,
                misses2,
                pres.getAndSet(0),
                (elapsed / (double) misses2 / 1e6));
            hits = 0;
            misses2 = 0;
            elapsed = 0;
        }

        synchronized (paramLock) {
            if (!initialized) {
                initialized = true;

                xspan = sx;
                yspan = sy;
                zspan = sz;
                xscale = scaleX;
                yscale = scaleY;
                zscale = scaleZ;
            }

            // This should never happen because all vanilla noisegens are given constant params, but you never know what
            // mods could do
            if (sx != xspan || sy != yspan
                || sz != zspan
                || diff(scaleX, xscale)
                || diff(scaleY, yscale)
                || diff(scaleZ, zscale)) {
                misses++;

                if (misses > 20) {
                    CubicChunks.LOGGER.info("Parameters for noisegen changed: resetting");
                    misses = 0;

                    xspan = sx;
                    yspan = sy;
                    zspan = sz;
                    xscale = scaleX;
                    yscale = scaleY;
                    zscale = scaleZ;

                    dataPool.clear();
                }
            }
        }

        if (data == null) data = new double[sx * sy * sz];

        NoiseData cached;

        synchronized (cache) {
            cached = cache.remove(Coords.key(blockX, blockY, blockZ));
        }

        if (cached != null && cached.matches()) {
            System.arraycopy(cached.data, 0, data, 0, cached.data.length);

            hits++;
            return data;
        }

        misses2++;

        long pre = System.nanoTime();
        double[] d2 = base.generateNoiseOctaves(data, blockX, blockY, blockZ, sx, sy, sz, scaleX, scaleY, scaleZ);
        long post = System.nanoTime();
        elapsed += post - pre;

        return d2;
    }

    private NoiseData getData() {
        synchronized (dataPool) {
            return dataPool.getInstance();
        }
    }

    private void releaseData(NoiseData data) {
        synchronized (dataPool) {
            if (!data.matches()) return;

            dataPool.releaseInstance(data);
        }
    }

    @Override
    public void precalculate(int blockX, int blockY, int blockZ) {
        Task3D task;

        synchronized (paramLock) {
            if (!initialized) return;

            TaskKey key = new TaskKey(blockX, blockY, blockZ);

            task = new Task3D(key);
        }

        TaskPool.submit(noiseTaskExecutor, task);
    }

    @Desugar

    private record TaskKey(int x, int y, int z) {

    }

    private class Task3D {

        private final TaskKey key;

        private final int xspan, yspan, zspan;
        private final double xscale, yscale, zscale;

        public Task3D(TaskKey key) {
            this.key = key;
            this.xspan = PrecalcedVanillaOctaves.this.xspan;
            this.yspan = PrecalcedVanillaOctaves.this.yspan;
            this.zspan = PrecalcedVanillaOctaves.this.zspan;
            this.xscale = PrecalcedVanillaOctaves.this.xscale;
            this.yscale = PrecalcedVanillaOctaves.this.yscale;
            this.zscale = PrecalcedVanillaOctaves.this.zscale;
        }

        public NoiseData run() {
            NoiseData data = getData();

            data.xspan = this.xspan;
            data.yspan = this.yspan;
            data.zspan = this.zspan;
            data.xscale = this.xscale;
            data.yscale = this.yscale;
            data.zscale = this.zscale;

            base.generateNoiseOctaves(data.data, key.x, key.y, key.z, xspan, yspan, zspan, xscale, yscale, zscale);

            return data;
        }
    }

    private class NoiseData {

        public final double[] data;

        public int xspan, yspan, zspan;
        public double xscale, yscale, zscale;

        public NoiseData() {
            this.data = new double[PrecalcedVanillaOctaves.this.xspan * PrecalcedVanillaOctaves.this.yspan
                * PrecalcedVanillaOctaves.this.zspan];
        }

        final boolean matches() {
            if (this.xspan != PrecalcedVanillaOctaves.this.xspan) return false;
            if (this.yspan != PrecalcedVanillaOctaves.this.yspan) return false;
            if (this.zspan != PrecalcedVanillaOctaves.this.zspan) return false;
            if (diff(this.xscale, PrecalcedVanillaOctaves.this.xscale)) return false;
            if (diff(this.yscale, PrecalcedVanillaOctaves.this.yscale)) return false;
            if (diff(this.zscale, PrecalcedVanillaOctaves.this.zscale)) return false;

            return true;
        }

        final void put(int x, int y, int z, double value) {
            data[index(x, y, z)] = value;
        }

        public final double sample(int x, int y, int z) {
            return data[index(x, y, z)];
        }

        private int index(int x, int y, int z) {
            return x + (y * xspan) + (z * xspan * yspan);
        }
    }
}
