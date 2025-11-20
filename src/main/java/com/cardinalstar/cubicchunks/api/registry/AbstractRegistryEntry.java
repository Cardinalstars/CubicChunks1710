package com.cardinalstar.cubicchunks.api.registry;

import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

@SuppressWarnings("unchecked")
public class AbstractRegistryEntry<TSelf> implements IRegistryEntry<TSelf> {

    public UniqueIdentifier registryName;
    public String unlocalizedName;

    @Override
    public TSelf setRegistryName(UniqueIdentifier name) {
        registryName = name;
        return (TSelf) this;
    }

    @Override
    public UniqueIdentifier getRegistryName() {
        return registryName;
    }

    public TSelf setUnlocalizedName(String name) {
        unlocalizedName = name;
        return (TSelf) this;
    }

    @Override
    public String getUnlocalizedName() {
        return unlocalizedName;
    }

    @Override
    public String getLocalizedName() {
        return StatCollector.translateToLocal(getUnlocalizedName());
    }
}
