package com.yision.fluidlogistics.util;

import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidDisplayHelper {

    private FluidDisplayHelper() {
    }

    public static boolean shouldDisplayAsFluid(ItemStack stack) {
        return CompressedTankItem.isFluidStack(stack);
    }

    public static FluidStack getDisplayFluid(ItemStack stack) {
        return shouldDisplayAsFluid(stack) ? CompressedTankItem.getFluid(stack).copy() : FluidStack.EMPTY;
    }

    public static boolean shouldDisplayAsFluidInPackage(ItemStack stack) {
        return CompressedTankItem.isFluidStack(stack);
    }

    public static FluidStack getPackageDisplayFluid(ItemStack stack) {
        return shouldDisplayAsFluidInPackage(stack) ? CompressedTankItem.getFluid(stack).copy() : FluidStack.EMPTY;
    }

    public static ItemStack getPackageDisplayStack(ItemStack stack) {
        return stack;
    }
}
