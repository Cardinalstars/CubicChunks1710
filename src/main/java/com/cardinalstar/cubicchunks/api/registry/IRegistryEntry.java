package com.cardinalstar.cubicchunks.api.registry;

import javax.annotation.Nullable;

import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

/**
 * This class is basically a simplified recreation of the forge registry system from 1.12. I liked how it works
 * and keeps things organized, so I wanted to do it as well.
 *
 * @param <V> The base class of the registries.
 */
public interface IRegistryEntry<V> {

    V setRegistryName(UniqueIdentifier name);

    @Nullable
    UniqueIdentifier getRegistryName();

    String getLocalizedName();
    String getUnlocalizedName();
}
