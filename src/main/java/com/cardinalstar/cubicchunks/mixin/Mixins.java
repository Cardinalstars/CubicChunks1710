package com.cardinalstar.cubicchunks.mixin;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

import javax.annotation.Nonnull;

public enum Mixins implements IMixins {

    MIXIN_ANVIL_SAVE_HANDLER(new MixinBuilder("Changing the save handler to return a Cubic anvil chunk loader.")
        .addCommonMixins("common.MixinAnvilSaveHandler")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_PROVIDER(new MixinBuilder("Implementing ICubicWorldProvider")
        .addCommonMixins("common.MixinWorldProvider")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)
        ),
    MIXIN_WORLD(new MixinBuilder("Implementing ICubicWorld")
        .addCommonMixins("common.MixinWorld")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true));


    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
