package com.yision.fluidlogistics.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class FluidInsertionHelper {

	private FluidInsertionHelper() {
	}

	private static List<FluidStack> mergeFluidsByType(List<FluidStack> fluids) {
		List<FluidStack> merged = new ArrayList<>();

		for (FluidStack fluid : fluids) {
			if (fluid.isEmpty()) {
				continue;
			}

			boolean found = false;
			for (FluidStack existing : merged) {
				if (FluidStack.isSameFluidSameComponents(existing, fluid)) {
					existing.grow(fluid.getAmount());
					found = true;
					break;
				}
			}

			if (!found) {
				merged.add(fluid.copy());
			}
		}

		return merged;
	}

	public static boolean canAcceptAll(@Nullable BlockEntity target, IFluidHandler handler, List<FluidStack> packageFluids) {
		List<FluidStack> merged = mergeFluidsByType(packageFluids);
		if (merged.isEmpty()) {
			return true;
		}

		boolean allInfinite = true;
		for (FluidStack fluid : merged) {
			if (!InfiniteFluidHandlerHelper.canAcceptInfinitely(handler, fluid)) {
				allInfinite = false;
				break;
			}
		}
		if (allInfinite) {
			return true;
		}

		if (handler instanceof SharedCapacityFluidHandler shared) {
			return shared.canFillAll(merged);
		}

		if (target != null && target.getLevel() != null) {
			return runWithTargetSnapshot(target, () -> fillAll(handler, merged, FluidAction.EXECUTE));
		}

		return canFillWithoutTargetSnapshot(handler, merged);
	}

	public static boolean insertAllOrNothing(@Nullable BlockEntity target, IFluidHandler handler, List<FluidStack> packageFluids) {
		List<FluidStack> merged = mergeFluidsByType(packageFluids);
		if (merged.isEmpty()) {
			return true;
		}

		if (!canAcceptAll(target, handler, packageFluids)) {
			return false;
		}

		if (target != null && target.getLevel() != null) {
			return runWithRollbackOnFailure(target, () -> fillAll(handler, merged, FluidAction.EXECUTE));
		}

		return fillAll(handler, merged, FluidAction.EXECUTE);
	}

	private static boolean canFillWithoutTargetSnapshot(IFluidHandler handler, List<FluidStack> fluids) {
		if (fluids.size() != 1) {
			return false;
		}

		FluidStack fluid = fluids.getFirst();
		return handler.fill(fluid.copy(), FluidAction.SIMULATE) == fluid.getAmount();
	}

	private static boolean fillAll(IFluidHandler handler, List<FluidStack> fluids, FluidAction action) {
		for (FluidStack fluid : fluids) {
			int filled = handler.fill(fluid.copy(), action);
			if (filled != fluid.getAmount()) {
				return false;
			}
		}

		return true;
	}

	private static boolean runWithTargetSnapshot(BlockEntity target, BooleanSupplier action) {
		Level level = target.getLevel();
		if (level == null) {
			return false;
		}

		HolderLookup.Provider registries = level.registryAccess();
		CompoundTag snapshot = target.saveWithFullMetadata(registries);
		try {
			return action.getAsBoolean();
		} finally {
			restoreTarget(target, snapshot, registries);
		}
	}

	private static boolean runWithRollbackOnFailure(BlockEntity target, BooleanSupplier action) {
		Level level = target.getLevel();
		if (level == null) {
			return false;
		}

		HolderLookup.Provider registries = level.registryAccess();
		CompoundTag snapshot = target.saveWithFullMetadata(registries);
		boolean success = action.getAsBoolean();
		if (!success) {
			restoreTarget(target, snapshot, registries);
		}
		return success;
	}

	private static void restoreTarget(BlockEntity target, CompoundTag snapshot, HolderLookup.Provider registries) {
		target.loadWithComponents(snapshot, registries);
		target.setChanged();

		Level level = target.getLevel();
		if (level != null) {
			level.sendBlockUpdated(target.getBlockPos(), target.getBlockState(), target.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}
}
