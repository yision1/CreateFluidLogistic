package com.yision.fluidlogistics.block.FluidHatch;

import com.simibubi.create.content.fluids.transfer.GenericItemFilling;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class FluidHatchItemFilling {
	private static final List<Handler> EXTRA_HANDLERS = new ArrayList<>();

	private FluidHatchItemFilling() {
	}

	public static void register(Handler handler) {
		EXTRA_HANDLERS.add(handler);
	}

	public static OptionalInt getRequiredAmountForExtraHandler(ItemStack stack, FluidStack availableFluid) {
		for (Handler handler : EXTRA_HANDLERS) {
			OptionalInt requiredAmount = handler.getRequiredAmountForItem(stack, availableFluid);
			if (requiredAmount.isPresent())
				return requiredAmount;
		}
		return OptionalInt.empty();
	}

	public static int getRequiredAmountForItem(Level level, ItemStack stack, FluidStack availableFluid) {
		OptionalInt requiredAmount = getRequiredAmountForExtraHandler(stack, availableFluid);
		if (requiredAmount.isPresent())
			return requiredAmount.getAsInt();
		return GenericItemFilling.getRequiredAmountForItem(level, stack, availableFluid);
	}

	public static Optional<ItemStack> fillItemWithExtraHandler(int requiredAmount, ItemStack stack, FluidStack availableFluid) {
		for (Handler handler : EXTRA_HANDLERS) {
			Optional<ItemStack> result = handler.fillItem(requiredAmount, stack, availableFluid);
			if (result.isPresent())
				return result;
		}
		return Optional.empty();
	}

	public static ItemStack fillItem(Level level, int requiredAmount, ItemStack stack, FluidStack availableFluid) {
		Optional<ItemStack> result = fillItemWithExtraHandler(requiredAmount, stack, availableFluid);
		if (result.isPresent())
			return result.get();
		return GenericItemFilling.fillItem(level, requiredAmount, stack, availableFluid);
	}

	public interface Handler {
		OptionalInt getRequiredAmountForItem(ItemStack stack, FluidStack availableFluid);

		Optional<ItemStack> fillItem(int requiredAmount, ItemStack stack, FluidStack availableFluid);
	}
}
