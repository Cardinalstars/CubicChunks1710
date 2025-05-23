package com.cardinalstar.cubicchunks.core.mixin;

import java.util.List;
import java.util.function.Supplier;

import com.gtnewhorizon.gtnhlib.mixin.IMixins;
import com.gtnewhorizon.gtnhlib.mixin.ITargetedMod;
import com.gtnewhorizon.gtnhlib.mixin.MixinBuilder;
import com.gtnewhorizon.gtnhlib.mixin.Phase;
import com.gtnewhorizon.gtnhlib.mixin.Side;
import com.gtnewhorizon.gtnhlib.mixin.TargetedMod;

public enum Mixins implements IMixins {

    MIXIN_ANVIL_SAVE_HANDLER(new MixinBuilder("Changing the save handler to return a Cubic anvil chunk loader")
        .addTargetedMod(TargetedMod.VANILLA)
        .setSide(Side.BOTH)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)
        .addMixinClasses("MixinAnvilSaveHandler")),
    MIXIN_WORLD_PROVIDER(new MixinBuilder("Implementing ICubicWorldProvider")
        .addTargetedMod(TargetedMod.VANILLA)
        .setSide(Side.BOTH)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)
        .addMixinClasses("MixinWorldProvider")),
    MIXIN_WORLD(new MixinBuilder("Implementing ICubicWorld")
        .addTargetedMod(TargetedMod.VANILLA)
        .setSide(Side.BOTH)
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)
        .addMixinClasses("MixinWorld"));

    private final List<String> mixinClasses;
    private final Supplier<Boolean> applyIf;
    private final Phase phase;
    private final Side side;
    private final List<ITargetedMod> targetedMods;
    private final List<ITargetedMod> excludedMods;

    Mixins(MixinBuilder builder) {
        this.mixinClasses = builder.mixinClasses;
        this.applyIf = builder.applyIf;
        this.side = builder.side;
        this.targetedMods = builder.targetedMods;
        this.excludedMods = builder.excludedMods;
        this.phase = builder.phase;
        if (this.targetedMods.isEmpty()) {
            throw new RuntimeException("No targeted mods specified for " + this.name());
        }
        if (this.applyIf == null) {
            throw new RuntimeException("No ApplyIf function specified for " + this.name());
        }
    }

    public List<String> getMixinClasses() {
        return mixinClasses;
    }

    public Supplier<Boolean> getApplyIf() {
        return applyIf;
    }

    public Phase getPhase() {
        return phase;
    }

    public Side getSide() {
        return side;
    }

    public List<ITargetedMod> getTargetedMods() {
        return targetedMods;
    }

    public List<ITargetedMod> getExcludedMods() {
        return excludedMods;
    }

}
