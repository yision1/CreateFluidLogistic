package com.yision.fluidlogistics.mixin.kinetics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;

@Mixin(SawBlockEntity.class)
public abstract class SawBlockEntityMixin {

    @Shadow
    public ProcessingInventory inventory;

    @WrapMethod(method = "applyRecipe")
    private void fluidlogistics$destroyFluidPackagesWithoutDrops(Operation<Void> original) {
        if (!FluidPackageItem.isFluidPackage(inventory.getStackInSlot(0))) {
            original.call();
            return;
        }

        inventory.clear();
    }
}
