/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2021 OpenCubicChunks
 *  Copyright (c) 2015-2021 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package com.cardinalstar.cubicchunks.world.core;

import com.cardinalstar.cubicchunks.util.AddressTools;
import com.cardinalstar.cubicchunks.world.column.IColumn;
import com.cardinalstar.cubicchunks.world.cube.ICube;
import com.cardinalstar.cubicchunks.core.world.cube.Cube;
import net.minecraft.network.PacketBuffer;

public interface IColumnInternal extends IColumn {
    // ChunkPrimer getCompatGenerationPrimer(); TODO?

    void removeFromStagingHeightmap(ICube cube);

    void addToStagingHeightmap(ICube cube);

    /**
     * Returns Y coordinate of the top non-transparent block
     *
     * @param localX column-local X coordinate
     * @param localZ column-local Z coordinate
     * @return the Y coordinate of the top non-transparent block
     */
    int getTopYWithStaging(int localX, int localZ);

    default void writeHeightmapDataForClient(PacketBuffer out) {
        for (int i = 0; i < Cube.SIZE * Cube.SIZE; i++) {
            out.writeInt(getTopYWithStaging(AddressTools.getLocalX(i), AddressTools.getLocalZ(i)));
        }
    }

    default void loadClientHeightmapData(PacketBuffer in) {
        ((ClientHeightMap) getOpacityIndex()).loadData(in);
    }
}
