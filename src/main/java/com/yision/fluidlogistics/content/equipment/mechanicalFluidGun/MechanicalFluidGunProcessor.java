package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetFilling;
import com.yision.fluidlogistics.foundation.fluid.DepotFills;

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
import org.jetbrains.annotations.Nullable;

class MechanicalFluidGunProcessor {

	static final int TRANSFER_INTERVAL = 10;
	private static final int IDLE_RECHECK_INTERVAL = 20;

	private final MechanicalFluidGunBlockEntity be;

	MechanicalFluidGunProcessor(MechanicalFluidGunBlockEntity be) {
		this.be = be;
	}

	void tickServer() {
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
			ResolvedProcess process = resolveProcess(sourceHandler, target, targetState, absTarget);
			if (process.kind() == ProcessKind.NONE) continue;

			if (!be.aimAtTarget(index)) {
				cycle.setTransferCooldown(1);
				return;
			}
			if (tryProcess(sourceHandler, target, absTarget, process)) {
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

	private ResolvedProcess resolveProcess(IFluidHandler sourceHandler, MechanicalFluidGunTargetConfig target,
										   BlockState targetState, BlockPos absTarget) {
		if (!targetState.is(MechanicalFluidGunBlock.TARGETS)) return ResolvedProcess.NONE;

		BlockEntity targetEntity = be.getLevel().getBlockEntity(absTarget);

		if (targetEntity != null && isDepot(targetEntity)) {
			ItemStack itemOnDepot = getItemOnDepot(targetEntity);
			if (itemOnDepot.isEmpty() || !FaucetFilling.canItemBeFilled(be.getLevel(), itemOnDepot)) {
				return ResolvedProcess.NONE;
			}
			FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForItem(be, sourceHandler, itemOnDepot);
			return fillableFluid.isEmpty()
				? ResolvedProcess.NONE
				: new ResolvedProcess(ProcessKind.DEPOT, targetEntity, null, itemOnDepot.copy(), fillableFluid);
		}

		if (targetEntity != null && isBelt(targetEntity)) {
			return ResolvedProcess.NONE;
		}

		if (targetState.is(Blocks.CAULDRON) || targetState.is(Blocks.WATER_CAULDRON)) {
			FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForCauldron(be, sourceHandler, targetState);
			return fillableFluid.isEmpty()
				? ResolvedProcess.NONE
				: new ResolvedProcess(ProcessKind.CAULDRON, targetEntity, null, ItemStack.EMPTY, fillableFluid);
		}

		if (targetEntity == null) return ResolvedProcess.NONE;

		IFluidHandler targetHandler = MechanicalFluidGunFillOperations.getTargetFluidHandler(
			be.getLevel(), targetEntity.getBlockPos(), target.face());
		if (targetHandler != null) {
			FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForContainer(be, sourceHandler, targetHandler, absTarget);
			if (!fillableFluid.isEmpty()) {
				return new ResolvedProcess(ProcessKind.CONTAINER, targetEntity, targetHandler, ItemStack.EMPTY, fillableFluid);
			}
		}

		return MechanicalFluidGunFillOperations.canFuel(be, sourceHandler, targetState, absTarget)
			? new ResolvedProcess(ProcessKind.FUEL, targetEntity, null, ItemStack.EMPTY, FluidStack.EMPTY)
			: ResolvedProcess.NONE;
	}

	private boolean tryProcess(IFluidHandler sourceHandler, MechanicalFluidGunTargetConfig target,
							   BlockPos absTarget, ResolvedProcess process) {
		Level level = be.getLevel();
		BlockState targetState = level.getBlockState(absTarget);

		if (!targetState.is(MechanicalFluidGunBlock.TARGETS)) return false;

		if (process.kind() == ProcessKind.DEPOT) {
			return startDepotItemFilling(sourceHandler, process.item(), process.fluid());
		}

		if (process.kind() == ProcessKind.CAULDRON) {
			MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
			return MechanicalFluidGunFillOperations.tryFillCauldron(be, visuals, absTarget, targetState, process.fluid());
		}

		if (process.kind() == ProcessKind.CONTAINER && process.targetHandler() != null) {
			MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
			return MechanicalFluidGunFillOperations.tryFillContainer(be, visuals, absTarget, sourceHandler,
				process.targetHandler(), process.fluid());
		}

		if (process.kind() == ProcessKind.FUEL) {
			MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
			return MechanicalFluidGunFillOperations.tryFuel(be, visuals, sourceHandler, targetState, absTarget);
		}

		return false;
	}

	private boolean startDepotItemFilling(IFluidHandler sourceHandler, ItemStack item, FluidStack availableFluid) {
		return MechanicalFluidGunItemFilling.startFilling(
			be, sourceHandler, item, availableFluid,
			MechanicalFluidGunItemFilling.ProcessingTarget.DEPOT, null);
	}

	private void abortFilling() {
		be.getItemFillingHelper().clear();
		be.endWorkCycle();
	}

	private void finishDepotItemFilling() {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		MechanicalFluidGunVisuals visuals = be.getVisualsHelper();
		MechanicalFluidGunCycle cycle = be.getCycleHelper();

		if (!itemFilling.isFillingDepot() || itemFilling.getProcessingItem().isEmpty() || itemFilling.getPendingFluid().isEmpty()) {
			abortFilling();
			return;
		}

		BlockPos absTarget = targets.getAbsoluteTarget(be.gunPos());
		if (absTarget == null) {
			abortFilling();
			return;
		}

		BlockEntity targetEntity = be.getLevel().getBlockEntity(absTarget);
		if (!isDepot(targetEntity)) {
			abortFilling();
			return;
		}

		ItemStack currentItem = getItemOnDepot(targetEntity);
		if (!ItemStack.isSameItemSameComponents(currentItem, itemFilling.getProcessingItem()) || currentItem.getCount() < 1) {
			abortFilling();
			return;
		}

		var transportedHandler = DepotFills.getTransportedHandler(be.getLevel(), targetEntity);
		if (transportedHandler == null) {
			abortFilling();
			return;
		}

		IFluidHandler sourceHandler = be.sourceHandler();
		if (sourceHandler == null) {
			abortFilling();
			return;
		}

		FluidStack drained = sourceHandler.drain(itemFilling.getPendingFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
		if (drained.isEmpty() || drained.getAmount() < itemFilling.getPendingFluid().getAmount()) {
			abortFilling();
			return;
		}

		boolean completed = DepotFills.fillFirstMatchingItem(transportedHandler,
			transported -> ItemStack.isSameItemSameComponents(transported.stack, itemFilling.getProcessingItem())
				&& transported.stack.getCount() >= 1,
			stack -> FaucetFilling.fillItem(be.getLevel(), itemFilling.getPendingFluid().getAmount(),
				stack, itemFilling.getPendingFluid().copy()));
		if (!completed) {
			abortFilling();
			return;
		}

		targetEntity.setChanged();
		DepotFills.notifyTargetUpdate(be.getLevel(), targetEntity);
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
			if (resolveProcess(sourceHandler, target, targetState, absTarget).kind() != ProcessKind.NONE) {
				return index;
			}
		}
		return -1;
	}

	boolean isDepot(BlockEntity entity) {
		return DepotFills.isDepot(entity);
	}

	ItemStack getItemOnDepot(BlockEntity depot) {
		return DepotFills.getItemOnDepot(depot);
	}

	private boolean isBelt(BlockEntity entity) {
		return entity instanceof BeltBlockEntity;
	}

	private record ResolvedProcess(ProcessKind kind, @Nullable BlockEntity targetEntity,
		@Nullable IFluidHandler targetHandler, ItemStack item, FluidStack fluid) {

		private static final ResolvedProcess NONE = new ResolvedProcess(ProcessKind.NONE, null, null,
			ItemStack.EMPTY, FluidStack.EMPTY);
	}

	private enum ProcessKind {
		NONE,
		DEPOT,
		CAULDRON,
		CONTAINER,
		FUEL
	}
}
