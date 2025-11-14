package com.cardinalstar.cubicchunks.world.worldgen.data;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.Nonnull;

import com.cardinalstar.cubicchunks.api.XYZAddressable;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.CubePos;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class FastCubePosMap<V> extends AbstractMap<CubePos, V> {

    public final Long2ObjectOpenHashMap<V> map = new Long2ObjectOpenHashMap<>();

    @Override
    public V get(Object key) {
        XYZAddressable pos = (XYZAddressable) key;

        return map.get(Coords.key(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public V remove(Object key) {
        XYZAddressable pos = (XYZAddressable) key;

        return map.remove(Coords.key(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public V put(CubePos key, V value) {
        return map.put(Coords.key(key.getX(), key.getY(), key.getZ()), value);
    }

    private class FastEntry implements Entry<CubePos, V> {

        final MutableCubePos pos = new MutableCubePos();
        Long2ObjectMap.Entry<V> entry;

        @Override
        public CubePos getKey() {
            return pos;
        }

        @Override
        public V getValue() {
            return entry.getValue();
        }

        @Override
        public V setValue(V value) {
            return entry.setValue(value);
        }
    }

    @Override
    @Nonnull
    public Set<Entry<CubePos, V>> entrySet() {
        return new AbstractSet<>() {

            @Override
            @Nonnull
            public Iterator<Entry<CubePos, V>> iterator() {
                var iter = Long2ObjectMaps.fastIterator(map);

                FastEntry entry = new FastEntry();

                return new Iterator<>() {

                    @Override
                    public boolean hasNext() {
                        return iter.hasNext();
                    }

                    @Override
                    public Entry<CubePos, V> next() {
                        var e = iter.next();

                        entry.entry = e;
                        entry.pos.x = Coords.x(e.getLongKey());
                        entry.pos.y = Coords.y(e.getLongKey());
                        entry.pos.z = Coords.z(e.getLongKey());

                        return entry;
                    }

                    @Override
                    public void remove() {
                        iter.remove();
                    }
                };
            }

            @Override
            public int size() {
                return map.size();
            }
        };
    }
}
