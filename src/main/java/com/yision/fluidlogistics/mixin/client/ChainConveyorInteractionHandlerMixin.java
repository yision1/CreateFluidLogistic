package com.yision.fluidlogistics.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperChainConveyorFrogportTarget;
import com.yision.fluidlogistics.registry.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChainConveyorInteractionHandler.class)
public class ChainConveyorInteractionHandlerMixin {

    @ModifyExpressionValue(
        method = "isActive",
        at = @At(
            value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"
        )
    )
    private static boolean fluidlogistics$activateForCopperFrogport(boolean original,
                                                                    @Local ItemStack mainHandItem) {
        return original || AllBlocks.COPPER_FROGPORT.isIn(mainHandItem);
    }

    @ModifyExpressionValue(
        method = "onUse",
        at = @At(
            value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"
        )
    )
    private static boolean fluidlogistics$selectForCopperFrogport(boolean original,
                                                                  @Local ItemStack mainHandItem) {
        return original || AllBlocks.COPPER_FROGPORT.isIn(mainHandItem);
    }

    @Inject(method = "onUse", at = @At("RETURN"))
    private static void fluidlogistics$useCopperFrogportTarget(CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Boolean.TRUE.equals(cir.getReturnValue())
            || minecraft.player == null
            || !AllBlocks.COPPER_FROGPORT.isIn(minecraft.player.getMainHandItem())) {
            return;
        }

        if (PackagePortTargetSelectionHandler.activePackageTarget
            instanceof ChainConveyorFrogportTarget target
            && !(target instanceof CopperChainConveyorFrogportTarget)) {
            PackagePortTargetSelectionHandler.activePackageTarget =
                new CopperChainConveyorFrogportTarget(
                    target.relativePos,
                    target.chainPos,
                    target.connection,
                    target.flipped
                );
        }
    }
}
