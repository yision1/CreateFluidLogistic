package com.yision.fluidlogistics.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.yision.fluidlogistics.registry.AllBlocks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ChainConveyorInteractionHandler.class, remap = false)
public class ChainConveyorInteractionHandlerMixin {

    @ModifyExpressionValue(
        method = {"isActive", "onUse"},
        at = @At(
            value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z",
            remap = false
        ),
        remap = false
    )
    private static boolean fluidlogistics$acceptCopperFrogport(boolean original) {
        Minecraft minecraft = Minecraft.getInstance();
        return original || minecraft.player != null
            && AllBlocks.COPPER_FROGPORT.isIn(minecraft.player.getMainHandItem());
    }
}
