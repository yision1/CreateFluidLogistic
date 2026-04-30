package com.yision.fluidlogistics.util;

import com.yision.fluidlogistics.item.CompressedTankItem;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class VirtualFluidDisplayHelper {

    private VirtualFluidDisplayHelper() {
    }

    public static boolean shouldDisplayAsFluid(ItemStack stack) {
        return stack.getItem() instanceof CompressedTankItem
                && CompressedTankItem.isVirtual(stack)
                && !CompressedTankItem.getFluid(stack).isEmpty();
    }

    public static FluidStack getDisplayFluid(ItemStack stack) {
        return shouldDisplayAsFluid(stack) ? CompressedTankItem.getFluid(stack).copy() : FluidStack.EMPTY;
    }

    public static boolean shouldDisplayAsFluidInPackage(ItemStack stack) {
        return stack.getItem() instanceof CompressedTankItem
                && !CompressedTankItem.getFluid(stack).isEmpty();
    }

    public static FluidStack getPackageDisplayFluid(ItemStack stack) {
        return shouldDisplayAsFluidInPackage(stack) ? CompressedTankItem.getFluid(stack).copy() : FluidStack.EMPTY;
    }

    public static ItemStack getPackageDisplayStack(ItemStack stack) {
        FluidStack fluid = getPackageDisplayFluid(stack);
        if (fluid.isEmpty()) {
            return stack;
        }

        ItemStack displayStack = stack.copy();
        CompressedTankItem.setFluidVirtual(displayStack, fluid);
        return displayStack;
    }
}
