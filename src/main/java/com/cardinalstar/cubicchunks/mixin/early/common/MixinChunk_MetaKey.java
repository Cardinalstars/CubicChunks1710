package com.cardinalstar.cubicchunks.mixin.early.common;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.cardinalstar.cubicchunks.api.MetaContainer;
import com.cardinalstar.cubicchunks.api.MetaKey;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceArrayMap;

@Mixin(Chunk.class)
public class MixinChunk_MetaKey implements MetaContainer {

    @Unique
    private final Reference2ReferenceArrayMap<MetaKey<?>, Object> meta = new Reference2ReferenceArrayMap<>();

    @Override
    public <T> T getMeta(MetaKey<T> key) {
        // noinspection unchecked
        return (T) meta.get(key);
    }

    @Override
    public <T> void setMeta(MetaKey<T> key, T value) {
        meta.put(key, value);
    }

}
