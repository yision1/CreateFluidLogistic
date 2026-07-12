package com.yision.fluidlogistics.compat.ghost;

import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidGhostStacks {

    private FluidGhostStacks() {
    }

    public static ItemStack fromFluid(FluidStack fluid) {
        ItemStack stack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(stack, fluid.copyWithAmount(1));
        return stack;
    }

    public static boolean isFluidGhost(ItemStack stack) {
        return CompressedTankItem.isFluidStack(stack);
    }

    public static FluidStack getFluid(ItemStack stack) {
        if (!isFluidGhost(stack)) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(stack);
    }
}
