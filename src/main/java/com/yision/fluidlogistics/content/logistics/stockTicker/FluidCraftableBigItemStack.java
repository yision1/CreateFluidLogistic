package com.yision.fluidlogistics.content.logistics.stockTicker;

import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.yision.fluidlogistics.api.packager.PackageResourceCrafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

public class FluidCraftableBigItemStack extends CraftableBigItemStack {

    public FluidCraftableBigItemStack(ItemStack stack, Recipe<?> recipe) {
        super(stack, recipe);
    }

    @Override
    public int getOutputCount(Level level) {
        return PackageResourceCrafting.get(this)
                .map(data -> data.outputCount())
                .orElseGet(() -> super.getOutputCount(level));
    }
}
