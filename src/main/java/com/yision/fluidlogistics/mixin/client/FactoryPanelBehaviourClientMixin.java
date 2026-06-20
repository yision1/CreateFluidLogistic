package com.yision.fluidlogistics.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.network.FactoryPanelSetFluidFilterPacket;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public class FactoryPanelBehaviourClientMixin {

    @Inject(method = "getTip", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$replaceEmptyTipForFluidContainers(CallbackInfoReturnable<MutableComponent> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        if (!self.getFilter().isEmpty()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            heldItem = player.getOffhandItem();
        }

        if (heldItem.isEmpty() || !GenericItemEmptying.canItemBeEmptied(player.level(), heldItem)) {
            return;
        }

        cir.setReturnValue(CreateLang.translateDirect("fluidlogistics.factory_panel.hold_alt_to_set_contained_fluid"));
    }

    @Inject(method = "onShortInteract", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$clientHandleAltClick(Player player, InteractionHand hand, Direction side,
        BlockHitResult hitResult, CallbackInfo ci) {
        if (!player.level().isClientSide() || !Screen.hasAltDown()) {
            return;
        }

        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.isEmpty() || !GenericItemEmptying.canItemBeEmptied(player.level(), heldItem)) {
            return;
        }

        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || localPlayer != player) {
            return;
        }

        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FluidLogisticsPackets.getChannel()
            .sendToServer(new FactoryPanelSetFluidFilterPacket(self.getPanelPosition(), hand));
        ci.cancel();
    }
}
