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

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProviderInternal;

public class CubicAnvilChunkLoader extends AnvilChunkLoader {

    private final Supplier<ICubeProviderInternal.Server> cubeProvider;

    // cubeIO needs to be supplier and lazy initialized because of the cubic chunks initialization order
    // AnvilChunkLoader is constructed in CubeProviderServer constructor, which is before CubeIO
    // which is inside the CubeProviderServer exists.
    public CubicAnvilChunkLoader(File chunkSaveLocationIn, Supplier<ICubeProviderInternal.Server> cubeProvider) {
        super(chunkSaveLocationIn);
        this.cubeProvider = cubeProvider;
    }

    private ICubeLoader getCubeLoader() {
        return cubeProvider.get().getCubeLoader();
    }

    @Override
    @Nullable
    public Chunk loadChunk(World worldIn, int x, int z) {
        return getCubeLoader().getColumn(x, z, ICubeProviderServer.Requirement.GENERATE);
    }

    @Override
    @Nullable
    public Object[] loadChunk__Async(World worldIn, int x, int z) throws IOException {
        return null;
    }

    @Override
    public boolean chunkExists(World world, int x, int z) {
        return getCubeLoader().columnExists(x, z);
    }

    @Override
    @Nullable
    protected Chunk checkedReadChunkFromNBT(World worldIn, int x, int z, NBTTagCompound compound) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Nullable
    protected Object[] checkedReadChunkFromNBT__Async(World worldIn, int x, int z, NBTTagCompound compound) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveChunk(World worldIn, Chunk chunkIn) {
        getCubeLoader().saveColumn(chunkIn);

        for (ICube cube : ((IColumn) chunkIn).getLoadedCubes()) {
            if (cube instanceof Cube realCube) {
                getCubeLoader().saveCube(realCube);
            }
        }
    }

    @Override
    protected void addChunkToPending(ChunkCoordIntPair pos, NBTTagCompound compound) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean writeNextIO() {
        return false;
    }

    @Override
    public void saveExtraChunkData(World worldIn, Chunk chunkIn) {}

    @Override
    public void chunkTick() {}

    /**
     * Flushes all pending chunks fully back to disk
     */
    @Override
    public void saveExtraData() {
        try {
            getCubeLoader().flush();
        } catch (IOException e) {
            CubicChunks.LOGGER.catching(e);
        }
    }

    @Override
    public void loadEntities(World worldIn, NBTTagCompound compound, Chunk chunk) {
        throw new UnsupportedOperationException();
    }
}
