package com.yision.fluidlogistics.mixin.kinetics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;

@Mixin(value = SawBlockEntity.class, remap = false)
public abstract class SawBlockEntityMixin {

    @Shadow
    public ProcessingInventory inventory;

    @Inject(method = "applyRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$destroyFluidPackagesWithoutDrops(CallbackInfo ci) {
        if (!FluidPackageItem.isFluidPackage(inventory.getStackInSlot(0))) {
            return;
        }

        inventory.clear();
        ci.cancel();
    }
}
