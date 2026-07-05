package com.yision.fluidlogistics.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class MergedFluidDisplayHandler implements IFluidHandler {
	private final List<DisplayedFluid> fluids = new ArrayList<>();

	public MergedFluidDisplayHandler(IFluidHandler source) {
		this(source, fluid -> true);
	}

	public MergedFluidDisplayHandler(IFluidHandler source, Predicate<FluidStack> filter) {
		for (int tank = 0; tank < source.getTanks(); tank++) {
			FluidStack fluid = source.getFluidInTank(tank);
			if (fluid.isEmpty() || !filter.test(fluid)) {
				continue;
			}
			mergeDisplayedFluid(fluid, source.getTankCapacity(tank));
		}
	}

	private void mergeDisplayedFluid(FluidStack additionalFluid, int additionalCapacity) {
		for (DisplayedFluid entry : fluids) {
			if (FluidStack.isSameFluidSameComponents(entry.fluid, additionalFluid)) {
				entry.merge(additionalFluid, additionalCapacity);
				return;
			}
		}
		fluids.add(new DisplayedFluid(additionalFluid, additionalCapacity));
	}

	@Override
	public int getTanks() {
		return fluids.size();
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return tank >= 0 && tank < fluids.size() ? fluids.get(tank).fluid.copy() : FluidStack.EMPTY;
	}

	@Override
	public int getTankCapacity(int tank) {
		return tank >= 0 && tank < fluids.size() ? fluids.get(tank).capacity : 0;
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

	private static int saturatedAdd(int a, int b) {
		long sum = (long) a + b;
		return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
	}

	private static final class DisplayedFluid {
		final FluidStack fluid;
		int capacity;

		DisplayedFluid(FluidStack fluid, int capacity) {
			this.fluid = fluid.copy();
			this.capacity = capacity;
		}

		void merge(FluidStack additionalFluid, int additionalCapacity) {
			fluid.setAmount(saturatedAdd(fluid.getAmount(), additionalFluid.getAmount()));
			capacity = saturatedAdd(capacity, additionalCapacity);
		}
	}
}
