package com.yision.fluidlogistics.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public class CompressedTankFluidHandler implements IFluidHandlerItem {

    private final ItemStack container;

    public CompressedTankFluidHandler(ItemStack container) {
        this.container = container;
    }

    @Override
    public ItemStack getContainer() {
        return container;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank != 0) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(container).copy();
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank != 0) {
            return 0;
        }
        FluidStack fluid = CompressedTankItem.getFluid(container);
        if (CompressedTankItem.isVirtual(container) && !fluid.isEmpty()) {
            return Math.max(fluid.getAmount(), CompressedTankItem.getCapacity());
        }
        return CompressedTankItem.getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
