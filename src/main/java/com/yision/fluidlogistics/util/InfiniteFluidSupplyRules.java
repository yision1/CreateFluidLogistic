package com.yision.fluidlogistics.util;

import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class InfiniteFluidSupplyRules {

	private InfiniteFluidSupplyRules() {
	}

	public static boolean isInfiniteSourceEnabled() {
		return AllConfigs.server().fluids.hosePulleyBlockThreshold.get() != -1;
	}

	public static boolean canSupplyInfinitely(Fluid fluid) {
		return fluid != null
			&& isInfiniteSourceEnabled()
			&& AllConfigs.server().fluids.bottomlessFluidMode.get().test(fluid);
	}

	public static boolean canEnterInfiniteTank(FluidStack stack) {
		return !stack.isEmpty() && canSupplyInfinitely(stack.getFluid());
	}

	public static boolean isInfiniteSupply(FluidStack stack, int requiredAmount) {
		return requiredAmount > 0
			&& canEnterInfiniteTank(stack)
			&& stack.getAmount() >= requiredAmount;
	}
}
