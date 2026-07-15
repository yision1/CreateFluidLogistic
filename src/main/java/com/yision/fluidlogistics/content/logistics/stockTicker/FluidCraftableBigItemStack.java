package com.yision.fluidlogistics.content.logistics.stockTicker;

import java.util.List;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.yision.fluidlogistics.util.IFluidCraftableBigItemStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

public class FluidCraftableBigItemStack extends CraftableBigItemStack implements IFluidCraftableBigItemStack {

    private int customOutputCount = -1;
    private int customTransferLimit = -1;
    private List<BigItemStack> customRequirements = List.of();

    public FluidCraftableBigItemStack(ItemStack stack, Recipe<?> recipe) {
        super(stack, recipe);
    }

    @Override
    public void fluidlogistics$setCustomRecipeData(int outputCount, int transferLimit,
            List<BigItemStack> requirements) {
        customOutputCount = outputCount;
        customTransferLimit = transferLimit;
        customRequirements = requirements.stream()
            .map(requirement -> new BigItemStack(requirement.stack.copyWithCount(1), requirement.count))
            .toList();
    }

    @Override
    public boolean fluidlogistics$hasCustomRecipeData() {
        return customOutputCount > 0 && !customRequirements.isEmpty();
    }

    @Override
    public int fluidlogistics$getCustomOutputCount() {
        return customOutputCount;
    }

    @Override
    public int fluidlogistics$getCustomTransferLimit() {
        return customTransferLimit;
    }

    @Override
    public List<BigItemStack> fluidlogistics$getCustomRequirements() {
        return customRequirements;
    }

    @Override
    public int getOutputCount(Level level) {
        return customOutputCount > 0 ? customOutputCount : super.getOutputCount(level);
    }
}
