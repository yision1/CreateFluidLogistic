package com.yision.fluidlogistics.block.WaterContainingCopperCasing;

import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public enum WaterContainingCopperCasingFluidHandler implements IFluidHandler {
    INSTANCE;

    private static final FluidStack WATER = new FluidStack(Fluids.WATER, Integer.MAX_VALUE);

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank == 0 ? WATER.copy() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? Integer.MAX_VALUE : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && !stack.isEmpty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        return resource.getAmount();
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) {
            return FluidStack.EMPTY;
        }
        return resource.copy();
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(Fluids.WATER, maxDrain);
    }
}
