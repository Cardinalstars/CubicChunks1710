package com.cardinalstar.cubicchunks.mixin.early.common;

import com.cardinalstar.cubicchunks.world.api.IMinMaxHeight;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetHandlerPlayServer.class)
public class MixinNetHandlerPlayServer {

//    @Redirect(
//        method = "processPlayerBlockPlacement",
//        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I"))
//    public int noopHeightChecks(MinecraftServer instance) {
//        return Integer.MAX_VALUE;
//    }

    @Shadow
    public EntityPlayerMP playerEntity;

    @Redirect(
        method = "processPlayerBlockPlacement",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/management/ItemInWorldManager;activateBlockOrUseItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;IIIIFFF)Z"
        )
    )
    private boolean preventLowPlacement(ItemInWorldManager manager, EntityPlayer player, World world,
                                        ItemStack stack, int x, int y, int z,
                                        int side, float hitX, float hitY, float hitZ,
                                        @Local(type = WorldServer.class) WorldServer server)
    {
        if (y < ((IMinMaxHeight)server).getMinHeight()) {
            ChatComponentTranslation chatcomponenttranslation = new ChatComponentTranslation("build.tooLow", ((IMinMaxHeight) server).getMinHeight());
            chatcomponenttranslation.getChatStyle().setColor(EnumChatFormatting.RED);
            this.playerEntity.playerNetServerHandler.sendPacket(new S02PacketChat(chatcomponenttranslation));
            return false;
        }

        return manager.activateBlockOrUseItem(player, world, stack, x, y, z, side, hitX, hitY, hitZ);
    }

    @Redirect(
        method = "processPlayerBlockPlacement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getBuildLimit()I"))
    public int noopHeightChecks(MinecraftServer instance, @Local(type = WorldServer.class) WorldServer server)
    {
        return ((IMinMaxHeight)server).getMaxHeight();
    }
}
