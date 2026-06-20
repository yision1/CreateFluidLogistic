package com.yision.fluidlogistics.util;

import java.math.BigDecimal;

import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

public final class InfiniteFluidSupplyRules {

	private InfiniteFluidSupplyRules() {
	}

	public static int getConfiguredThresholdBuckets() {
		return AllConfigs.server().fluids.hosePulleyBlockThreshold.get();
	}

	public static boolean isInfiniteSourceEnabled() {
		return getConfiguredThresholdBuckets() != -1;
	}

	public static int getRequiredAmount() {
		int threshold = getConfiguredThresholdBuckets();
		if (threshold == -1)
			return Integer.MAX_VALUE;
		long amount = (long) Math.max(0, threshold) * FluidType.BUCKET_VOLUME;
		return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
	}

	public static String getRequiredAmountText() {
		if (!isInfiniteSourceEnabled())
			return "∞";
		return formatBuckets(getRequiredAmount());
	}

	public static String formatBuckets(int amount) {
		return BigDecimal.valueOf(amount, 3)
			.stripTrailingZeros()
			.toPlainString() + "B";
	}

	public static boolean canSupplyInfinitely(Fluid fluid) {
		return fluid != null
			&& isInfiniteSourceEnabled()
			&& AllConfigs.server().fluids.bottomlessFluidMode.get().test(fluid);
	}

	public static boolean canEnterInfiniteTank(FluidStack stack) {
		return !stack.isEmpty()
			&& isInfiniteSourceEnabled()
			&& canSupplyInfinitely(stack.getFluid());
	}

	public static boolean isInfiniteSupply(FluidStack stack) {
		return canEnterInfiniteTank(stack)
			&& stack.getAmount() >= getRequiredAmount();
	}
}
