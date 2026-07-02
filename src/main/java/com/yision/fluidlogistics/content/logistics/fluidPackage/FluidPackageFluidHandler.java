package com.yision.fluidlogistics.content.logistics.fluidPackage;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public class FluidPackageFluidHandler implements IFluidHandlerItem {
    private final ItemStack container;

    public FluidPackageFluidHandler(ItemStack container) {
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
        FluidStack contained = FluidPackageContentHelper.getSingleContainedFluid(container);
        return contained.getAmount() >= FluidPackageContentHelper.PACKAGE_DRAIN_AMOUNT
            ? contained.copy()
            : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        FluidStack fluid = getFluidInTank(tank);
        return tank == 0 && !fluid.isEmpty() ? fluid.getAmount() : 0;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain < FluidPackageContentHelper.PACKAGE_DRAIN_AMOUNT) {
            return FluidStack.EMPTY;
        }
        return FluidPackageContentHelper.drainOneBucket(container, action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        FluidStack contained = FluidPackageContentHelper.getSingleContainedFluid(container);
        if (contained.isEmpty()) {
            return FluidStack.EMPTY;
        }
        if (resource.getAmount() < FluidPackageContentHelper.PACKAGE_DRAIN_AMOUNT) {
            return FluidStack.EMPTY;
        }
        if (!FluidStack.isSameFluidSameComponents(contained, resource)) {
            return FluidStack.EMPTY;
        }
        return FluidPackageContentHelper.drainOneBucket(container, action.simulate());
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0;
    }
}
