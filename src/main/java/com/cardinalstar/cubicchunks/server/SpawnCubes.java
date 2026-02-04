/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cardinalstar.cubicchunks.server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.ITicket;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;

@ParametersAreNonnullByDefault
public class SpawnCubes implements ITicket {

    private int spawnX;
    private int spawnY;
    private int spawnZ;
    private int radiusXZGenerate = CubicChunksConfig.spawnGenerateDistanceXZ;
    private int radiusYGenerate = CubicChunksConfig.spawnGenerateDistanceY;
    private int radiusXZForce = CubicChunksConfig.spawnLoadDistanceXZ;
    private int radiusYForce = CubicChunksConfig.spawnLoadDistanceY;
    private ChunkCoordinates spawnPoint;

    public void update(World world) {
        update(
            world,
            CubicChunksConfig.spawnGenerateDistanceXZ,
            CubicChunksConfig.spawnGenerateDistanceY,
            CubicChunksConfig.spawnLoadDistanceXZ,
            CubicChunksConfig.spawnLoadDistanceY); // radius did not change
    }

    public void update(World world, int newRadiusXZGenerate, int newRadiusYGenerate, int newRadiusXZForce,
        int newRadiusYForce) {
        if (!world.getSpawnPoint()
            .equals(spawnPoint) || radiusXZGenerate != newRadiusXZGenerate
            || radiusYGenerate != newRadiusYGenerate
            || radiusXZForce != newRadiusXZForce
            || radiusYForce != newRadiusYForce) {
            removeTickets(world);
            spawnPoint = world.getSpawnPoint();
            radiusXZGenerate = newRadiusXZGenerate;
            radiusYGenerate = newRadiusYGenerate;
            radiusXZForce = newRadiusXZForce;
            radiusYForce = newRadiusYForce;
            addTickets(world); // addTickets will update the spawn location if need be
        }
    }

    private void removeTickets(World world) {
        if (radiusYForce < 0 || radiusXZForce < 0 || spawnPoint == null) {
            return; // no spawn chunks OR nothing to remove
        }

        ICubeProviderInternal serverCubeCache = (ICubeProviderInternal) world.getChunkProvider();

        int spawnCubeX = Coords.blockToCube(spawnPoint.posX);
        int spawnCubeY = Coords.blockToCube(spawnPoint.posY);
        int spawnCubeZ = Coords.blockToCube(spawnPoint.posZ);

        for (int cubeX = spawnCubeX - radiusXZForce; cubeX <= spawnCubeX + radiusXZForce; cubeX++) {
            for (int cubeZ = spawnCubeZ - radiusXZForce; cubeZ <= spawnCubeZ + radiusXZForce; cubeZ++) {
                for (int cubeY = spawnCubeY + radiusYForce; cubeY >= spawnCubeY - radiusYForce; cubeY--) {
                    serverCubeCache.getCube(cubeX, cubeY, cubeZ)
                        .getTickets()
                        .remove(this);
                }
            }
        }
    }

    private void addTickets(World world) {
        if (radiusXZGenerate < 0 || radiusYGenerate < 0) {
            return; // no spawn cubes
        }

        CubeProviderServer serverCubeCache = (CubeProviderServer) world.getChunkProvider();

        // load the cubes around the spawn point
        CubicChunks.LOGGER.info("Loading cubes for spawn...");
        int spawnCubeX = Coords.blockToCube(spawnPoint.posX);
        int spawnCubeY = Coords.blockToCube(spawnPoint.posY);
        int spawnCubeZ = Coords.blockToCube(spawnPoint.posZ);

        AtomicLong lastTime = new AtomicLong(System.currentTimeMillis());
        final int progressReportInterval = 1000;// ms
        int totalToGenerate = (radiusXZGenerate * 2 + 1) * (radiusXZGenerate * 2 + 1) * (radiusYGenerate * 2 + 1);
        AtomicInteger generated = new AtomicInteger();

        int r = Math.max(radiusXZGenerate, radiusXZForce);
        int ry = Math.max(radiusYGenerate, radiusYForce);

        forEachCube(
            spawnCubeX,
            spawnCubeY,
            spawnCubeZ,
            r,
            ry,
            (x, y, z) -> { serverCubeCache.loadCubeEagerly(x, y, z, ICubeProviderServer.Requirement.LIGHT); });

        forEachCube(spawnCubeX, spawnCubeY, spawnCubeZ, r, ry, (cubeX, cubeY, cubeZ) -> {
            ICubeProviderServer.Requirement req;

            int dx = Math.abs(cubeX - spawnCubeX);
            int dy = Math.abs(cubeY - spawnCubeY);
            int dz = Math.abs(cubeZ - spawnCubeZ);

            // is edge?
            if (dx >= radiusXZGenerate || dz >= radiusXZGenerate || dy >= radiusYGenerate) {
                req = ICubeProviderServer.Requirement.GENERATE;
            } else {
                req = ICubeProviderServer.Requirement.LIGHT;
            }

            Cube cube = serverCubeCache.getCube(cubeX, cubeY, cubeZ, req);
            assert cube != null;

            if (dx <= radiusXZForce && dz <= radiusXZForce) {
                cube.getTickets()
                    .add(this);
            }
            generated.incrementAndGet();
            if (System.currentTimeMillis() >= lastTime.get() + progressReportInterval) {
                lastTime.set(System.currentTimeMillis());
                CubicChunks.LOGGER.info("Preparing spawn area: {}%", generated.get() * 100 / totalToGenerate);
            }
        });
        CubicChunks.LOGGER.info("Preparing spawn area: 100%");
    }

    private void forEachCube(int spawnCubeX, int spawnCubeY, int spawnCubeZ, int r, int ry, XYZConsumer action) {
        for (int cubeX = spawnCubeX - r; cubeX <= spawnCubeX + r; cubeX++) {
            for (int cubeZ = spawnCubeZ - r; cubeZ <= spawnCubeZ + r; cubeZ++) {
                for (int cubeY = spawnCubeY + ry; cubeY >= spawnCubeY - ry; cubeY--) {
                    action.accept(cubeX, cubeY, cubeZ);
                }
            }
        }
    }

    @Override
    public boolean shouldTick() {
        return false;
    }

    @FunctionalInterface
    private interface XYZConsumer {

        void accept(int x, int y, int z);
    }
}
