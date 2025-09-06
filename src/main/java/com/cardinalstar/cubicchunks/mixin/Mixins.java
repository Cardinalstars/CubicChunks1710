package com.cardinalstar.cubicchunks.mixin;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    // =============================================================
    // COMMON Mixins
    // =============================================================
    // MISC
    MIXIN_ANVIL_SAVE_HANDLER(new MixinBuilder("Changing the save handler to return a Cubic anvil chunk loader.")
        .addCommonMixins("common.MixinAnvilSaveHandler")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_GAME_REGISTRY_ACCESSOR(
        new MixinBuilder("Allows access to the generators in GameRegistry").addCommonMixins("common.IGameRegistry")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),

    // CHUNK
    MIXIN_CHUNK_COLUMN(
        new MixinBuilder("Mixin for making chunks into columns.").addCommonMixins("common.MixinChunk_Column")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_CHUNK_CUBES(new MixinBuilder("Mixin to make it so that chunk methods are redirected to cubes.")
        .addCommonMixins("common.MixinChunk_Cubes")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_CHUNK_CACHE_HEIGHT_LIMITS(new MixinBuilder("Mixin to fix height limits in ChunkCache")
        .addCommonMixins("common.MixinChunkCache_HeightLimits")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),

    // ITEM
    MIXIN_ITEM_BLOCK(
        new MixinBuilder("Mixin to to allow placing items everywhere").addCommonMixins("common.MixinItemBlock")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),

    // WORLD
    MIXIN_WORLD_SERVER(new MixinBuilder("Mixin for making world server into a ICubicWorldInternal.Server")
        .addCommonMixins("common.MixinWorldServer")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_SETTINGS(
        new MixinBuilder("Mixin for world settings allowing cubes.").addCommonMixins("common.MixinWorldSettings")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_PROVIDER(
        new MixinBuilder("Implementing ICubicWorldProvider.").addCommonMixins("common.MixinWorldProvider")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD(new MixinBuilder("Implementing ICubicWorld.").addCommonMixins("common.MixinWorld")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_INFO(new MixinBuilder("Implementing ICubicWorldInfo").addCommonMixins("common.MixinWorldInfo")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_DERIVED_WORLD_INFO(new MixinBuilder("Giving the isCubic method to DerivedWorldInfo")
        .addCommonMixins("common.MixinDerivedWorldInfo")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_BLOCK_ACCESS_MIN_MAX_FIX(new MixinBuilder("Have IBlockAccess implement IMinMaxHeight")
        .addCommonMixins("common.MixinIBlockAccess_MinMaxHeight")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_HEIGHT_LIMIT(
        new MixinBuilder("Fix a ton of height limit issues in World.").addCommonMixins("common.MixinWorld_HeightLimit")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_TICK(
        new MixinBuilder("Redirecting some things to use Y values.").addCommonMixins("common.MixinWorld_Tick")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),

    // ENTITY
    MIXIN_ENTITY_DEATH_FIX(new MixinBuilder("Replace -64 constant, to avoid killing entities below y=-64")
        .addCommonMixins("common.MixinEntity_DeathFix")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_ENTITY_TRACKER(
        new MixinBuilder("Changing the EntityTracker to work with cubes.").addCommonMixins("common.MixinEntityTracker")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_ENTITY_TRACKER_ENTRY(
        new MixinBuilder("Changing entityTrackerEntry to use cubes.").addCommonMixins("common.MixinEntityTrackerEntry")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_SPAWNER_ANIMALS(
        new MixinBuilder("Fixing spawner animals to work with cubes.").addCommonMixins("common.MixinSpawnerAnimals")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_STRUCTURE_START(
        new MixinBuilder("Giving Y position to structures.").addCommonMixins("common.MixinStructureStart")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    // SERVER
    MIXIN_INTEGRATED_SERVER_ACCESSOR(
        new MixinBuilder("Allows access to the worldsettings field.").addCommonMixins("common.IIntegratedServer")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_MINECRAFT_SERVER(new MixinBuilder("Initializes a cubic world instead of a normal world.")
        .addCommonMixins("common.MixinMinecraftServer")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_SERVER_CONFIGURATION_MANAGER(new MixinBuilder("Implements ICubicPlayerList in ServerConfigurationManager")
        .addCommonMixins("common.MixinServerConfigurationManager")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_REGION_FILE_CACHE(
        new MixinBuilder("I believe this is for compat but IDK").addCommonMixins("common.MixinRegionFileCache")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_ENTITY_LIVING_BASE(new MixinBuilder("Fix enttiy handling").addClientMixins("common.MixinEntityLivingBase")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),

    // =============================================================
    // Client Mixins
    // =============================================================
    MIXIN_I_CHUNK_PROVIDER_CLIENT(new MixinBuilder("Implements IChunkProviderClient on ChunkProviderClient")
        .addClientMixins("client.IChunkProviderClient")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_IGUI_VIDEO_SETTINGS(
        new MixinBuilder("Allows access to the getOptionsRowList field.").addClientMixins("client.IGuiVideoSettings")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_IGUI_OPTIONS_ROW_LIST(new MixinBuilder("Allows access to the field_148184_k (getOptions) field.")
        .addClientMixins("client.IGuiOptionsRowList")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_IGUI_SCREEN(new MixinBuilder("Allows access to the buttonList field.").addClientMixins("client.IGuiScreen")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_CHUNK_CUBES_CLIENT(
        new MixinBuilder("Client chunk fix to turn them to cubes.").addClientMixins("client.MixinChunk_Cubes")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_CHUNK_CACHE_HEIGHT_LIMITS_CLIENT(new MixinBuilder("Client chunk cache fixes for height limits.")
        .addClientMixins("client.MixinChunkCache_HeightLimits")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_CLIENT_PLAYER(new MixinBuilder("Fix player handling").addClientMixins("client.MixinEntityClientPlayerMP")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_EMPTY_CHUNK(new MixinBuilder("Client empty chunk fix.").addClientMixins("client.MixinEmptyChunk")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_HEIGHT_LIMITS_CLIENT(
        new MixinBuilder("Height limits fix for client world.").addClientMixins("client.MixinWorld_HeightLimits")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_WORLD_CLIENT(new MixinBuilder("Cubic world init for client.").addClientMixins("client.MixinWorldClient")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    MIXIN_WORLD_PROVIDER_CLIENT(
        new MixinBuilder("World provider fix for Y fog.").addClientMixins("client.MixinWorldProvider")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_RENDERGLOBAL(new MixinBuilder("Fix rendering.").addClientMixins("client.MixinRenderGlobal")
        .setPhase(Phase.EARLY)
        .setApplyIf(() -> true)),
    // =============================================================
    // Server Mixins
    // =============================================================

    MIXIN_DEDICATED_PLAYER_LIST(
        new MixinBuilder("Allow to set vertical view distance.").addServerMixins("server.MixinDedicatedPlayerList")
            .setPhase(Phase.EARLY)
            .setApplyIf(() -> true)),
    MIXIN_DEDICATED_SERVER_HEIGHT_LIMITS(new MixinBuilder("Fixing height limit issues in dedicated server.")
        .addServerMixins("server.MixinDedicatedServer_HeightLimits")
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
