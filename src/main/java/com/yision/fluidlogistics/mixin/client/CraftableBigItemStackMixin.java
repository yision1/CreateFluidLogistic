package com.yision.fluidlogistics.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.yision.fluidlogistics.util.IFluidCraftableBigItemStack;

import net.minecraft.world.level.Level;

@Mixin(value = CraftableBigItemStack.class, remap = false)
public abstract class CraftableBigItemStackMixin implements IFluidCraftableBigItemStack {

    @Unique
    private int fluidlogistics$customOutputCount = -1;

    @Unique
    private int fluidlogistics$customTransferLimit = -1;

    @Unique
    private List<BigItemStack> fluidlogistics$customRequirements = List.of();

    @Override
    public void fluidlogistics$setCustomRecipeData(int outputCount, int transferLimit, List<BigItemStack> requirements) {
        fluidlogistics$customOutputCount = outputCount;
        fluidlogistics$customTransferLimit = transferLimit;
        fluidlogistics$customRequirements = requirements.stream()
            .map(requirement -> new BigItemStack(requirement.stack.copyWithCount(1), requirement.count))
            .toList();
    }

    @Override
    public boolean fluidlogistics$hasCustomRecipeData() {
        return fluidlogistics$customOutputCount > 0 && !fluidlogistics$customRequirements.isEmpty();
    }

    @Override
    public int fluidlogistics$getCustomOutputCount() {
        return fluidlogistics$customOutputCount;
    }

    @Override
    public int fluidlogistics$getCustomTransferLimit() {
        return fluidlogistics$customTransferLimit;
    }

    @Override
    public List<BigItemStack> fluidlogistics$getCustomRequirements() {
        return fluidlogistics$customRequirements;
    }

    @Inject(method = "getOutputCount", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$useCustomOutputCount(Level level, CallbackInfoReturnable<Integer> cir) {
        if (fluidlogistics$customOutputCount > 0) {
            cir.setReturnValue(fluidlogistics$customOutputCount);
        }
    }
}
