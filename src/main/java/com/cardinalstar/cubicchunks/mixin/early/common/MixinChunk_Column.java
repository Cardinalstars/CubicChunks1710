package com.cardinalstar.cubicchunks.mixin.early.common;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.IHeightMap;
import com.cardinalstar.cubicchunks.core.world.core.StagingHeightMap;
import com.cardinalstar.cubicchunks.api.ICube;

import javax.annotation.Nullable;
import java.util.Collection;

public class MixinChunk_Column implements IColumn
{
    private StagingHeightMap stagingHeightMap;
    public void chunk_internal$addToStagingHeightmap(ICube cube) {
        stagingHeightMap.addStagedCube(cube);
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getZ() {
        return 0;
    }

    @Override
    public int getHeight(int x, int y, int z) {
        return 0;
    }

    @Override
    public int getHeightValue(int localX, int localZ) {
        return 0;
    }

    @Override
    public int getHeightValue(int localX, int blockY, int localZ) {
        return 0;
    }

    @Override
    public boolean shouldTick() {
        return false;
    }

    @Override
    public IHeightMap getOpacityIndex() {
        return null;
    }

    @Override
    public Collection<? extends ICube> getLoadedCubes() {
        return null;
    }

    @Override
    public Iterable<? extends ICube> getLoadedCubes(int startY, int endY) {
        return null;
    }

    @Nullable
    @Override
    public ICube getLoadedCube(int cubeY) {
        return null;
    }

    @Override
    public ICube getCube(int cubeY) {
        return null;
    }

    @Override
    public void addCube(ICube cube) {

    }

    @Nullable
    @Override
    public ICube removeCube(int cubeY) {
        return null;
    }

    @Override
    public boolean hasLoadedCubes() {
        return false;
    }

    @Override
    public void preCacheCube(ICube cube) {

    }
}
