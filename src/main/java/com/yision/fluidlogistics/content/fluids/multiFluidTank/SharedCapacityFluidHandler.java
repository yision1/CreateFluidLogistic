package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import java.util.List;

import net.neoforged.neoforge.fluids.FluidStack;

public interface SharedCapacityFluidHandler {

    boolean canFillAll(List<FluidStack> fluids);
}
