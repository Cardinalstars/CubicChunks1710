/*
 * Minecraft Forge
 * Copyright (c) 2016.
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package com.cardinalstar.cubicchunks.server.chunkio.async.forge;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeIO;

public class QueuedColumn {

    final int x;
    final int z;
    final ICubeIO loader;
    final World world;
    final CubeProviderServer provider;
    net.minecraft.nbt.NBTTagCompound compound;
    private final Consumer<Chunk> setProviderLoadingColumn;

    @Nullable
    public Exception exception;

    public QueuedColumn(int x, int z, ICubeIO loader, World world, CubeProviderServer provider) {
        this.x = x;
        this.z = z;
        this.loader = loader;
        this.world = world;
        this.provider = provider;
        this.setProviderLoadingColumn = col -> this.provider.currentlyLoadingColumn = col;
    }

    @Override
    public int hashCode() {
        return (x * 31 + z * 29) ^ world.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof QueuedColumn other) {
            return x == other.x && z == other.z && world == other.world;
        }

        return false;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        result.append(
            this.getClass()
                .getName() + " {"
                + NEW_LINE);
        result.append(" x: " + x + NEW_LINE);
        result.append(" zPosition: " + z + NEW_LINE);
        result.append(" loader: " + loader + NEW_LINE);
        result.append(
            " world: " + world.getWorldInfo()
                .getWorldName() + NEW_LINE);
        result.append(" dimension: " + world.provider.dimensionId + NEW_LINE);
        result.append(
            " provider: " + world.provider.getClass()
                .getName() + NEW_LINE);
        result.append("}");

        return result.toString();
    }
}
