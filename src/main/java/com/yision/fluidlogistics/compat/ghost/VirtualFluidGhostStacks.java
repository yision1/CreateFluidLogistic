package com.yision.fluidlogistics.compat.ghost;

import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class VirtualFluidGhostStacks {

    private VirtualFluidGhostStacks() {
    }

    public static ItemStack fromFluid(FluidStack fluid) {
        ItemStack stack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluidVirtual(stack, fluid.copyWithAmount(1));
        return stack;
    }

    public static boolean isVirtualFluidGhost(ItemStack stack) {
        return stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack);
    }

    public static FluidStack getFluid(ItemStack stack) {
        if (!isVirtualFluidGhost(stack)) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(stack);
    }
}
