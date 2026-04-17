package com.yision.fluidlogistics.util;

import java.util.List;

import net.neoforged.neoforge.fluids.FluidStack;

public interface SharedCapacityFluidHandler {

    boolean canFillAll(List<FluidStack> fluids);
}
