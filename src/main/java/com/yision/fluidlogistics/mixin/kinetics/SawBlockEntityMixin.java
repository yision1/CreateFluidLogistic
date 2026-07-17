package com.yision.fluidlogistics.mixin.kinetics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.PackageResourceType.SawAction;

@Mixin(SawBlockEntity.class)
public abstract class SawBlockEntityMixin {

    @Shadow
    public ProcessingInventory inventory;

    @WrapMethod(method = "applyRecipe")
    private void fluidlogistics$destroyFluidPackagesWithoutDrops(Operation<Void> original) {
        if (!PackageResources.isBootstrapped()
                || PackageResources.sawAction(inventory.getStackInSlot(0)) != SawAction.DESTROY_WITHOUT_DROPS) {
            original.call();
            return;
        }

        inventory.clear();
    }
}
