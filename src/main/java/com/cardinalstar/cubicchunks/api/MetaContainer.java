package com.cardinalstar.cubicchunks.api;

public interface MetaContainer {

    <T> T getMeta(MetaKey<T> key);

    <T> void setMeta(MetaKey<T> key, T value);
}
