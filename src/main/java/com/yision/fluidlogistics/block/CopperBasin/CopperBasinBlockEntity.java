package com.yision.fluidlogistics.block.CopperBasin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.util.CopperBasinCapacity;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class CopperBasinBlockEntity extends BasinBlockEntity {

	private FilteringBehaviour filtering;

	public CopperBasinBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(new DirectBeltInputBehaviour(this));

		filtering = new FilteringBehaviour(this, new CopperBasinValueBox())
			.withCallback($ -> notifyChangeOfContents())
			.forRecipes();
		behaviours.add(filtering);

		inputTank = new SmartFluidTankBehaviour(
			SmartFluidTankBehaviour.INPUT,
			this,
			2,
			CopperBasinCapacity.SLOT_CAPACITY,
			true
		).whenFluidUpdates(this::notifyChangeOfContents);

		outputTank = new SmartFluidTankBehaviour(
			SmartFluidTankBehaviour.OUTPUT,
			this,
			2,
			CopperBasinCapacity.SLOT_CAPACITY,
			true
		).whenFluidUpdates(this::notifyChangeOfContents)
			.forbidInsertion();

		behaviours.add(inputTank);
		behaviours.add(outputTank);

		fluidCapability = LazyOptional.of(() -> {
			LazyOptional<? extends IFluidHandler> inputCap = inputTank.getCapability();
			LazyOptional<? extends IFluidHandler> outputCap = outputTank.getCapability();
			return new CombinedTankWrapper(outputCap.orElse(null), inputCap.orElse(null));
		});
	}

	@Override
	public FilteringBehaviour getFilter() {
		return filtering;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		int titleIndex = tooltip.size();
		boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		if (added && tooltip.size() > titleIndex) {
			replaceGoggleTitle(tooltip, titleIndex);
		}
		return added;
	}

	private static void replaceGoggleTitle(List<Component> tooltip, int titleIndex) {
		List<Component> title = new ArrayList<>();
		CreateLang.translate("gui.goggles.copper_basin_contents")
			.forGoggles(title);
		if (!title.isEmpty())
			tooltip.set(titleIndex, title.get(0));
	}

	@Override
	public boolean acceptOutputs(List<ItemStack> outputItems, List<FluidStack> outputFluids, boolean simulate) {
		if (outputFluids.isEmpty())
			return super.acceptOutputs(outputItems, outputFluids, simulate);

		outputTank.allowInsertion();
		try {
			IFluidHandler targetTank = outputTank.getCapability().orElse(null);
			if (targetTank == null)
				return false;

			if (!super.acceptOutputs(outputItems, Collections.emptyList(), true))
				return false;
			if (!acceptFluidsIntoOutputTank(outputFluids, true, targetTank))
				return false;

			if (simulate)
				return true;

			if (!super.acceptOutputs(outputItems, Collections.emptyList(), false))
				return false;
			return acceptFluidsIntoOutputTank(outputFluids, false, targetTank);
		} finally {
			outputTank.forbidInsertion();
		}
	}

	@Override
	public float getTotalFluidUnits(float partialTicks) {
		if (level != null && level.isClientSide)
			return shouldRenderCopperBasinMixedSurfaceParticles()
				? CopperBasinCapacity.RENDER_FULL_CAPACITY
				: 0;

		return super.getTotalFluidUnits(partialTicks);
	}

	private boolean shouldRenderCopperBasinMixedSurfaceParticles() {
		List<FluidStack> fullFluids = new ArrayList<>(2);

		if (!collectFullSlotFluids(inputTank, fullFluids))
			return false;
		if (!collectFullSlotFluids(outputTank, fullFluids))
			return false;

		if (fullFluids.size() != 2)
			return false;

		return !fullFluids.get(0).isFluidEqual(fullFluids.get(1));
	}

	private static boolean collectFullSlotFluids(SmartFluidTankBehaviour behaviour, List<FluidStack> fullFluids) {
		if (behaviour == null)
			return true;

		IFluidHandler handler = behaviour.getCapability().orElse(null);
		if (handler == null)
			return true;

		for (int tank = 0; tank < handler.getTanks(); tank++) {
			FluidStack fluidStack = handler.getFluidInTank(tank);
			if (fluidStack.isEmpty())
				continue;

			if (fluidStack.getAmount() != CopperBasinCapacity.SLOT_CAPACITY)
				return false;

			fullFluids.add(fluidStack);
			if (fullFluids.size() > 2)
				return false;
		}

		return true;
	}

	@Override
	public void tick() {
		if (!FeatureToggle.isEnabled(FeatureToggle.COPPER_BASIN)) {
			return;
		}

		super.tick();
		if (level == null || level.isClientSide)
			return;
		pushOutputFluidsToSpoutputTarget();
	}

	private void pushOutputFluidsToSpoutputTarget() {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof BasinBlock))
			return;

		Direction direction = blockState.getValue(BasinBlock.FACING);
		if (direction == Direction.DOWN)
			return;

		BlockPos targetPos = worldPosition.below()
			.relative(direction);
		BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
		if (targetBlockEntity == null)
			return;

		IFluidHandler sourceTank = outputTank.getCapability().orElse(null);
		if (sourceTank == null)
			return;

		IFluidHandler targetTank = targetBlockEntity
			.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite())
			.orElse(null);
		if (targetTank == null)
			return;

		for (int slot = 0; slot < sourceTank.getTanks(); slot++) {
			FluidStack available = sourceTank.getFluidInTank(slot);
			if (available.isEmpty())
				continue;
			if (movePartialFluid(sourceTank, targetTank, available.getAmount()) > 0) {
				notifyChangeOfContents();
				notifyUpdate();
				return;
			}
		}
	}

	private static int movePartialFluid(IFluidHandler sourceTank, IFluidHandler targetTank, int maxAmount) {
		FluidStack available = sourceTank.drain(maxAmount, FluidAction.SIMULATE);
		if (available.isEmpty())
			return 0;

		int accepted = fillTarget(targetTank, copyWithAmount(available, available.getAmount()), FluidAction.SIMULATE);
		if (accepted <= 0)
			return 0;

		FluidStack toMove = copyWithAmount(available, Math.min(accepted, available.getAmount()));
		int moved = fillTarget(targetTank, toMove, FluidAction.EXECUTE);
		if (moved <= 0)
			return 0;

		sourceTank.drain(copyWithAmount(available, moved), FluidAction.EXECUTE);
		return moved;
	}

	private static FluidStack copyWithAmount(FluidStack stack, int amount) {
		if (stack.isEmpty() || amount <= 0)
			return FluidStack.EMPTY;
		FluidStack copy = stack.copy();
		copy.setAmount(amount);
		return copy;
	}

	private static boolean acceptFluidsIntoOutputTank(List<FluidStack> outputFluids, boolean simulate,
		IFluidHandler targetTank) {
		for (FluidStack fluidStack : outputFluids) {
			FluidAction action = simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE;
			if (fillTarget(targetTank, fluidStack.copy(), action) != fluidStack.getAmount())
				return false;
		}
		return true;
	}

	private static int fillTarget(IFluidHandler targetTank, FluidStack fluidStack, FluidAction action) {
		return targetTank instanceof SmartFluidTankBehaviour.InternalFluidHandler internalFluidHandler
			? internalFluidHandler.forceFill(fluidStack, action)
			: targetTank.fill(fluidStack, action);
	}

	static class CopperBasinValueBox extends ValueBoxTransform.Sided {

		@Override
		protected Vec3 getSouthLocation() {
			return VecHelper.voxelSpace(8, 12, 16.05);
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis()
				.isHorizontal();
		}
	}
}
