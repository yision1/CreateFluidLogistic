package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.yision.fluidlogistics.block.Faucet.FaucetFilling;
import com.yision.fluidlogistics.config.FeatureToggle;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

class MechanicalFluidGunProcessor {

	static final int TRANSFER_INTERVAL = 10;
	private static final int IDLE_RECHECK_INTERVAL = 20;
	private static Field depotOutputBufferField;

	private final MechanicalFluidGunBlockEntity be;

	MechanicalFluidGunProcessor(MechanicalFluidGunBlockEntity be) {
		this.be = be;
	}

	void tickServer() {
		if (!FeatureToggle.isEnabled(FeatureToggle.MECHANICAL_FLUID_GUN)) {
			return;
		}
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();
		MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		MechanicalFluidGunBeltHandler beltHandler = be.getBeltHandlerHelper();

		if (be.getSpeed() == 0) {
			be.endWorkCycle();
			return;
		}

		if (itemFilling.isFilling()) {
			if (itemFilling.isFillingBelt()) {
				beltHandler.tickActiveBeltFillingFallback();
				return;
			}
			if (itemFilling.getProcessingTicks() <= 0) {
				finishDepotItemFilling();
				if (be.isRedstoneLocked()) {
					be.endWorkCycle();
				}
				return;
			}
			itemFilling.decrementTicks();
			return;
		}

		boolean sprayCompleted = visuals.tickTransientSpray(itemFilling.isFilling(), () -> {
			if (visuals.shouldAdvanceAfterSpray()) {
				if (be.isRedstoneLocked()) {
					be.endWorkCycle();
				} else {
					advanceToProcessableTargetOrIdle();
				}
			}
		});
		if (sprayCompleted) {
			be.notifyGunUpdate();
		}

		if (beltHandler.shouldWaitForBeltCallback()) {
			beltHandler.tickKeepAlive();
			cycle.tickCooldown();
			return;
		}

		if (cycle.tickCooldown()) {
			return;
		}

		if (be.isRedstoneLocked() && !cycle.isActive()) {
			return;
		}

		if (targets.isEmpty()) {
			return;
		}

		tryInject();
	}

	private void tryInject() {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();

		if (!targets.hasValidTarget(be.getLevel(), be.gunPos())) {
			cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(IDLE_RECHECK_INTERVAL, Math.abs(be.getSpeed())));
			cycle.resetScheduledTarget();
			be.endWorkCycle();
			return;
		}

		IFluidHandler sourceHandler = be.sourceHandler();
		if (sourceHandler == null) {
			cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(IDLE_RECHECK_INTERVAL, Math.abs(be.getSpeed())));
			return;
		}

		MechanicalFluidGunScheduleMode mode = be.getScheduleMode();
		List<Integer> candidateIndices = getCandidateIndicesContinuingActiveTarget(mode, targets.size());

		for (int index : candidateIndices) {
			MechanicalFluidGunTargetConfig target = targets.get(index);
			BlockPos absTarget = target.absoluteFrom(be.gunPos());
			if (!targets.isTargetValid(be.getLevel(), be.gunPos(), absTarget)) continue;
			if (be.getBeltHandlerHelper().isBeltTarget(absTarget)) continue;

			BlockState targetState = be.getLevel().getBlockState(absTarget);
			if (!canProcess(sourceHandler, target, targetState, absTarget)) continue;

			if (!be.aimAtTarget(index)) {
				cycle.setTransferCooldown(1);
				return;
			}
			if (tryProcess(sourceHandler, target, absTarget)) {
				cycle.markScheduledTarget(index);
				cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(TRANSFER_INTERVAL, Math.abs(be.getSpeed())));
				return;
			}
		}

		if (mode == MechanicalFluidGunScheduleMode.ROUND_ROBIN) {
			cycle.resetScheduledTarget();
		}
		cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(IDLE_RECHECK_INTERVAL, Math.abs(be.getSpeed())));
		be.endWorkCycle();
	}

	private List<Integer> getCandidateIndices(MechanicalFluidGunScheduleMode mode, int size) {
		if (size <= 0) return List.of();

		MechanicalFluidGunCycle cycle = be.getCycleHelper();
		int last = cycle.getLastScheduledTargetIndex();
		int start = Math.floorMod(last + 1, size);
		List<Integer> indices = new ArrayList<>();

		if (mode == MechanicalFluidGunScheduleMode.PREFER_FIRST) {
			for (int i = 0; i < size; i++) indices.add(i);
			return indices;
		}

		if (mode == MechanicalFluidGunScheduleMode.FORCED_ROUND_ROBIN) {
			indices.add(start);
			return indices;
		}

		// ROUND_ROBIN
		for (int step = 0; step < size; step++) {
			indices.add(Math.floorMod(start + step, size));
		}
		return indices;
	}

	private List<Integer> getCandidateIndicesContinuingActiveTarget(MechanicalFluidGunScheduleMode mode, int size) {
		List<Integer> indices = getCandidateIndices(mode, size);
		int activeTarget = be.getTargetsHelper().getActiveTargetIndex();
		if (activeTarget < 0 || activeTarget >= size) {
			return indices;
		}

		List<Integer> activeFirst = new ArrayList<>();
		activeFirst.add(activeTarget);
		for (int index : indices) {
			if (index != activeTarget) {
				activeFirst.add(index);
			}
		}
		return activeFirst;
	}

	private boolean canProcess(IFluidHandler sourceHandler, MechanicalFluidGunTargetConfig target,
							   BlockState targetState, BlockPos absTarget) {
		if (!targetState.is(MechanicalFluidGunBlock.TARGETS)) return false;

		BlockEntity targetEntity = be.getLevel().getBlockEntity(absTarget);

		if (targetEntity != null && isDepot(targetEntity)) {
			ItemStack itemOnDepot = getItemOnDepot(targetEntity);
			return !itemOnDepot.isEmpty()
				&& FaucetFilling.canItemBeFilled(be.getLevel(), itemOnDepot)
				&& !MechanicalFluidGunFillOperations.findFillableFluidForItem(be, sourceHandler, itemOnDepot).isEmpty();
		}

		if (targetEntity != null && isBelt(targetEntity)) {
			return false;
		}

		if (targetState.is(Blocks.CAULDRON) || targetState.is(Blocks.WATER_CAULDRON)) {
			return !MechanicalFluidGunFillOperations.findFillableFluidForCauldron(be, sourceHandler, targetState).isEmpty();
		}

		if (targetEntity == null) return false;

		IFluidHandler targetHandler = MechanicalFluidGunFillOperations.getTargetFluidHandler(
			be.getLevel(), targetEntity.getBlockPos(), target.face());
		if (targetHandler != null) {
			return !MechanicalFluidGunFillOperations.findFillableFluidForContainer(be, sourceHandler, targetHandler, absTarget).isEmpty();
		}

		return MechanicalFluidGunFillOperations.canFuel(be, sourceHandler, targetState, absTarget);
	}

	private boolean tryProcess(IFluidHandler sourceHandler, MechanicalFluidGunTargetConfig target,
							   BlockPos absTarget) {
		Level level = be.getLevel();
		BlockEntity targetEntity = level.getBlockEntity(absTarget);
		BlockState targetState = level.getBlockState(absTarget);

		if (!targetState.is(MechanicalFluidGunBlock.TARGETS)) return false;

		if (targetEntity != null && isDepot(targetEntity)) {
			ItemStack itemOnDepot = getItemOnDepot(targetEntity);
			if (!itemOnDepot.isEmpty() && FaucetFilling.canItemBeFilled(level, itemOnDepot)) {
				FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForItem(be, sourceHandler, itemOnDepot);
				if (!fillableFluid.isEmpty()) {
					return startDepotItemFilling(sourceHandler, itemOnDepot, fillableFluid);
				}
			}
			return false;
		}

		if (targetState.is(Blocks.CAULDRON) || targetState.is(Blocks.WATER_CAULDRON)) {
			FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForCauldron(be, sourceHandler, targetState);
			if (!fillableFluid.isEmpty()) {
				MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
				return MechanicalFluidGunFillOperations.tryFillCauldron(be, visuals, absTarget, targetState, fillableFluid);
			}
			return false;
		}

		if (targetEntity != null) {
			MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
			if (MechanicalFluidGunFillOperations.tryFillContainerWithActiveTarget(be, visuals, target, absTarget)) {
				return true;
			}

			return MechanicalFluidGunFillOperations.tryFuel(be, visuals, sourceHandler, targetState, absTarget);
		}

		return false;
	}

	private boolean startDepotItemFilling(IFluidHandler sourceHandler, ItemStack item, FluidStack availableFluid) {
		return MechanicalFluidGunItemFilling.startFilling(
			be, sourceHandler, item, availableFluid,
			MechanicalFluidGunItemFilling.ProcessingTarget.DEPOT, null);
	}

	private void finishDepotItemFilling() {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();

		if (!itemFilling.isFillingDepot() || itemFilling.getProcessingItem().isEmpty() || itemFilling.getPendingFluid().isEmpty()) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		BlockPos absTarget = targets.getAbsoluteTarget(be.gunPos());
		if (absTarget == null) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		BlockEntity targetEntity = be.getLevel().getBlockEntity(absTarget);
		if (!isDepot(targetEntity)) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		ItemStack currentItem = getItemOnDepot(targetEntity);
		if (!ItemStack.isSameItemSameComponents(currentItem, itemFilling.getProcessingItem()) || currentItem.getCount() < 1) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		DepotBehaviour behaviour = DepotBehaviour.get(targetEntity, DepotBehaviour.TYPE);
		if (behaviour == null) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		ItemStack result = FaucetFilling.fillItem(be.getLevel(), itemFilling.getPendingFluid().getAmount(),
			currentItem, itemFilling.getPendingFluid().copy());
		if (result.isEmpty()) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		IFluidHandler sourceHandler = be.sourceHandler();
		if (sourceHandler == null) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		FluidStack drained = sourceHandler.drain(itemFilling.getPendingFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
		if (drained.isEmpty() || drained.getAmount() < itemFilling.getPendingFluid().getAmount()) {
			itemFilling.clear();
			be.endWorkCycle();
			return;
		}

		if (currentItem.isEmpty()) {
			behaviour.setHeldItem(new TransportedItemStack(result));
		} else {
			behaviour.setHeldItem(new TransportedItemStack(currentItem.copy()));
			storeDepotOutput(behaviour, result, absTarget);
		}

		targetEntity.setChanged();
		be.getLevel().sendBlockUpdated(absTarget, targetEntity.getBlockState(), targetEntity.getBlockState(), 3);
		be.getLevel().playSound(null, absTarget, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.5f, 1.0f + be.getLevel().random.nextFloat() * 0.2f);

		MechanicalFluidGunTargetConfig activeTarget = targets.getActiveTarget();
		Vec3 aimPoint = be.getTargetAimPoint(activeTarget);
		visuals.spawnServerSprayParticles(be.getLevel(), be.gunPos(), aimPoint);

		itemFilling.clear();
		cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(TRANSFER_INTERVAL, Math.abs(be.getSpeed())));
		be.notifyGunUpdate();
	}

	boolean advanceToProcessableTargetOrIdle() {
		int processableTarget = findNextProcessableTarget();
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();

		if (processableTarget == -1) {
			targets.resetActive();
			cycle.setActive(false);
			cycle.setTargetProgress(1);
		} else {
			if (targets.getActiveTargetIndex() != processableTarget || !cycle.isActive()) {
				cycle.setTargetProgress(0);
			}
			targets.setActiveTargetIndex(processableTarget);
			cycle.setActive(true);
			cycle.setTransferCooldown(0);
		}

		be.updateVisuals();
		return processableTarget != -1;
	}

	private int findNextProcessableTarget() {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		IFluidHandler sourceHandler = be.sourceHandler();
		if (sourceHandler == null) return -1;

		int size = targets.size();
		if (size == 0) return -1;
		MechanicalFluidGunScheduleMode mode = be.getScheduleMode();

		for (int index : getCandidateIndicesContinuingActiveTarget(mode, size)) {
			MechanicalFluidGunTargetConfig target = targets.get(index);
			BlockPos absTarget = target.absoluteFrom(be.gunPos());
			if (!targets.isTargetValid(be.getLevel(), be.gunPos(), absTarget)) continue;

			BlockState targetState = be.getLevel().getBlockState(absTarget);
			if (canProcess(sourceHandler, target, targetState, absTarget)) {
				return index;
			}
		}
		return -1;
	}

	boolean isDepot(BlockEntity entity) {
		return entity != null && DepotBehaviour.get(entity, DepotBehaviour.TYPE) != null;
	}

	ItemStack getItemOnDepot(BlockEntity depot) {
		DepotBehaviour behaviour = DepotBehaviour.get(depot, DepotBehaviour.TYPE);
		return behaviour == null ? ItemStack.EMPTY : behaviour.getHeldItemStack();
	}

	private void storeDepotOutput(DepotBehaviour behaviour, ItemStack result, BlockPos targetPos) {
		try {
			ItemStackHandler outputBuffer = getDepotOutputBuffer(behaviour);
			ItemStack remainder = insertIntoOutputBuffer(outputBuffer, result);
			if (!remainder.isEmpty()) {
				dropDepotOutput(remainder, targetPos);
			}
		} catch (ReflectiveOperationException | ClassCastException exception) {
			dropDepotOutput(result, targetPos);
		}
	}

	private static ItemStackHandler getDepotOutputBuffer(DepotBehaviour behaviour) throws ReflectiveOperationException {
		if (depotOutputBufferField == null) {
			depotOutputBufferField = DepotBehaviour.class.getDeclaredField("processingOutputBuffer");
			depotOutputBufferField.setAccessible(true);
		}
		return (ItemStackHandler) depotOutputBufferField.get(behaviour);
	}

	static ItemStack insertIntoOutputBuffer(ItemStackHandler outputBuffer, ItemStack result) {
		ItemStack remainder = result.copy();
		for (int slot = 0; slot < outputBuffer.getSlots() && !remainder.isEmpty(); slot++) {
			remainder = outputBuffer.insertItem(slot, remainder, false);
		}
		return remainder;
	}

	private void dropDepotOutput(ItemStack stack, BlockPos targetPos) {
		net.minecraft.world.Containers.dropItemStack(be.getLevel(),
			targetPos.getX() + 0.5, targetPos.getY() + 0.75, targetPos.getZ() + 0.5, stack);
	}

	private boolean isBelt(BlockEntity entity) {
		return entity instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
	}
}
