/*
 * Minecraft Forge
 * Copyright (c) 2016.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package com.cardinalstar.cubicchunks.core.server.chunkio.async.forge;

import com.cardinalstar.cubicchunks.core.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.core.server.CubicAnvilChunkLoader;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;


/**
 * Taking from Sponge, with modifications
 */
public class QueuedCube {
    final int x;
    final int y;
    final int z;
    final CubicAnvilChunkLoader loader;
    final World world;
    final CubeProviderServer provider;

    @Nonnull
    private final CompletableFuture<Chunk> futureColumn = new CompletableFuture<>();

    net.minecraft.nbt.NBTTagCompound compound;

    public QueuedCube(int x, int y, int z, CubicAnvilChunkLoader loader, World world, CubeProviderServer provider) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.loader = loader;
        this.world = world;
        this.provider = provider;
    }

    @Override
    public int hashCode() {
        return (x * 31 + y * 37 + z * 29) ^ world.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof QueuedCube other)
        {
            return x == other.x && z == other.z && world == other.world;
        }
        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        result.append(this.getClass().getName() + " {" + NEW_LINE);
        result.append(" x: " + x + NEW_LINE);
        result.append(" y: " + y + NEW_LINE);
        result.append(" zPosition: " + z + NEW_LINE);
        result.append(" loader: " + loader + NEW_LINE );
        result.append(" world: " + world.getWorldInfo().getWorldName() + NEW_LINE);
        result.append(" dimension: " + world.provider.dimensionId + NEW_LINE);
        result.append(" provider: " + world.provider.getClass().getName() + NEW_LINE);
        result.append("}");

        return result.toString();
    }

    public void setColumn(@Nullable Chunk chunk) {
        if (futureColumn.isDone()) {
            return;
        }
        this.futureColumn.complete(chunk);
    }
}
