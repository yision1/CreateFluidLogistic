package com.yision.fluidlogistics.content.fluids.infiniteFluidTank;

import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank;
import com.simibubi.create.content.logistics.BigItemStack;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidTankBlockEntity.InfiniteSmartFluidTank;
import com.yision.fluidlogistics.content.fluids.waterContainingCopperCasing.WaterContainingCopperCasingFluidHandler;

import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public final class InfiniteFluidHandlerHelper {

	private InfiniteFluidHandlerHelper() {
	}

	public static int getStockAmount(IFluidHandler handler, FluidStack fluidInTank) {
		return isInfiniteSource(handler, fluidInTank) ? BigItemStack.INF : fluidInTank.getAmount();
	}

	public static boolean isInfiniteSource(IFluidHandler handler, FluidStack fluid) {
		if (fluid.isEmpty())
			return false;

		if (handler instanceof WaterContainingCopperCasingFluidHandler)
			return fluid.getFluid() == Fluids.WATER;

		if (handler instanceof CreativeSmartFluidTank)
			return hasSameContainedFluid(handler, fluid);

		if (handler instanceof InfiniteSmartFluidTank infiniteTank)
			return infiniteTank.isInfiniteMode() && hasSameContainedFluid(handler, fluid);

		return false;
	}

	public static boolean isInfiniteSink(IFluidHandler handler, FluidStack fluid) {
		if (fluid.isEmpty())
			return false;

		if (handler instanceof WaterContainingCopperCasingFluidHandler)
			return true;

		if (handler instanceof CreativeSmartFluidTank)
			return true;

		if (handler instanceof InfiniteSmartFluidTank infiniteTank)
			return infiniteTank.isInfiniteMode() && hasSameContainedFluid(handler, fluid);

		return false;
	}

	public static FluidStack drainFromInfiniteSource(IFluidHandler handler, FluidStack targetFluid, int amount) {
		if (amount <= 0 || !isInfiniteSource(handler, targetFluid))
			return FluidStack.EMPTY;

		FluidStack result = targetFluid.copy();
		result.setAmount(amount);
		return result;
	}

	public static boolean canAcceptInfinitely(IFluidHandler handler, FluidStack fluid) {
		return isInfiniteSink(handler, fluid);
	}

	private static boolean hasSameContainedFluid(IFluidHandler handler, FluidStack targetFluid) {
		for (int tank = 0; tank < handler.getTanks(); tank++) {
			FluidStack contained = handler.getFluidInTank(tank);
			if (!contained.isEmpty() && contained.isFluidEqual(targetFluid)
				&& FluidStack.areFluidStackTagsEqual(contained, targetFluid))
				return true;
		}
		return false;
	}
}
