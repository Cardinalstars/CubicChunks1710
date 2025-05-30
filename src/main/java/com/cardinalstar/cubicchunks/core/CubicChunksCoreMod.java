package com.cardinalstar.cubicchunks.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cardinalstar.cubicchunks.core.mixin.Mixins;
import com.gtnewhorizon.gtnhlib.mixin.IMixins;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.7.10")
public class CubicChunksCoreMod extends DummyModContainer implements IFMLLoadingPlugin, IEarlyMixinLoader {

    public static final String[] DEFAULT_TRANSFORMERS = new String[] {
        "com.gtnewhorizon.gtnhlib.core.transformer.EventBusSubTransformer" };

    public CubicChunksCoreMod() {
        super(new ModMetadata());
        ModMetadata md = getMetadata();
        md.autogenerated = true;
        md.modId = md.name = "Cubic Worlds Core";
        md.parent = "cubicworlds";
        md.version = "0.0.1";
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {};
    }

    @Override
    public String getModContainerClass() {
        return "com.cardinalstar.cubicworlds.core.CubicWorldsCoreMod";
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        return "mixins.cubicchunks.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return IMixins.getEarlyMixins(Mixins.class, loadedCoreMods);
    }
}
