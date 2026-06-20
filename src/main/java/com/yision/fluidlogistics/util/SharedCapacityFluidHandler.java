package com.yision.fluidlogistics.util;

import java.util.List;

import net.minecraftforge.fluids.FluidStack;

public interface SharedCapacityFluidHandler {

    boolean canFillAll(List<FluidStack> fluids);
}
