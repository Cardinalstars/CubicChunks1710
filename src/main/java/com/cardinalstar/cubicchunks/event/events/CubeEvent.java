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
package com.cardinalstar.cubicchunks.event.events;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.cube.Cube;

/**
 * CubicChunks equivalent of {@link net.minecraftforge.event.world.ChunkEvent}.
 */
public class CubeEvent extends WorldEvent {

    public final CubePos pos;

    protected CubeEvent(World world, CubePos pos) {
        super(world);
        this.pos = pos;
    }

    /**
     * CubicChunks equivalent of {@link net.minecraftforge.event.world.ChunkEvent.Load}.
     */
    public static class Load extends CubeEvent {

        public final Cube cube;

        public Load(World world, CubePos pos, Cube cube) {
            super(world, pos);
            this.cube = cube;
        }
    }

    /**
     * CubicChunks equivalent of {@link net.minecraftforge.event.world.ChunkEvent.Unload}.
     */
    public static class Unload extends CubeEvent {

        public final Cube cube;

        public Unload(World world, CubePos pos, Cube cube) {
            super(world, pos);
            this.cube = cube;
        }
    }

    public static class LoadNBT extends CubeEvent {

        public NBTTagCompound tag;

        public LoadNBT(World world, CubePos pos, NBTTagCompound tag) {
            super(world, pos);
            this.tag = tag;
        }
    }


    public static class SaveNBT extends CubeEvent {

        public NBTTagCompound tag;

        public SaveNBT(World world, CubePos pos, NBTTagCompound tag) {
            super(world, pos);
            this.tag = tag;
        }
    }
}
