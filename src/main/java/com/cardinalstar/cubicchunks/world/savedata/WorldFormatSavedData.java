package com.cardinalstar.cubicchunks.world.savedata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.world.storage.StorageFormatFactory;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public class WorldFormatSavedData extends WorldSavedData {

    private StorageFormatFactory format;

    public WorldFormatSavedData(String name) {
        super(name);
    }

    public StorageFormatFactory getFormat() {
        return format;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        format = StorageFormatFactory.REGISTRY.get(new UniqueIdentifier(tag.getString("format")));

        if (format == null) {
            throw new IllegalStateException("Could not load world: save format was not registered: " + tag.getString("format"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setString("format", format.registryName.toString());
    }

    public static WorldFormatSavedData get(World world) {
        WorldFormatSavedData data = (WorldFormatSavedData) world.mapStorage.loadData(
            WorldFormatSavedData.class,
            "cubicchunks.world_format");

        if (data == null) {
            data = new WorldFormatSavedData("cubicchunks.world_format");

            UniqueIdentifier id;

            if (CubicChunksConfig.storageFormat.isEmpty()) {
                id = StorageFormatFactory.DEFAULT;
            } else {
                id = new UniqueIdentifier(CubicChunksConfig.storageFormat);
            }

            data.format = StorageFormatFactory.REGISTRY.get(id);

            data.markDirty();
            world.mapStorage.setData(data.mapName, data);
            world.mapStorage.saveAllData();
        }

        return data;
    }
}
