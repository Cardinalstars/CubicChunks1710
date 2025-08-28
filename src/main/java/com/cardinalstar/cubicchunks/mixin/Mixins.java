package com.cardinalstar.cubicchunks.mixin;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import scala.tools.nsc.transform.Mixin;

import javax.annotation.Nonnull;

public enum Mixins implements IMixins {

    MIXIN_ANVIL_SAVE_HANDLER(new MixinBuilder("Changing the save handler to return a Cubic anvil chunk loader.")
        .addCommonMixins("common.MixinAnvilSaveHandler")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_PROVIDER(new MixinBuilder("Implementing ICubicWorldProvider.")
        .addCommonMixins("common.MixinWorldProvider")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD(new MixinBuilder("Implementing ICubicWorld.")
        .addCommonMixins("common.MixinWorld")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),

    MIXIN_INTEGRATED_SERVER_ACCESSOR(new MixinBuilder("Allows access to the worldsettings field.")
        .addCommonMixins("common.IIntegratedServer")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),

    MIXIN_WORLD_SETTINGS(new MixinBuilder("Mixin for world settings allowing cubes.")
        .addCommonMixins("common.MixinWorldSettings")
        .setApplyIf(() -> true)),

    //  =============================================================
    //                        Client Mixins
    //  =============================================================

    MIXIN_IGUI_VIDEO_SETTINGS(new MixinBuilder("Allows access to the getOptionsRowList field.")
        .addClientMixins("client.IGuiVideoSettings")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_IGUI_OPTIONS_ROW_LIST(new MixinBuilder("Allows access to the field_148184_k (getOptions) field.")
        .addClientMixins("client.IGuiOptionsRowList")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_IGUI_SCREEN(new MixinBuilder("Allows access to the buttonList field.")
        .addClientMixins("client.IGuiScreen")
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
