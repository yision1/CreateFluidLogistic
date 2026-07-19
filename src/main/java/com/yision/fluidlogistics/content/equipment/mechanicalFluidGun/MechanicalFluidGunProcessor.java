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
	private PendingTarget pendingTarget;

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
		List<Integer> candidateIndices = getCandidateIndices(mode, targets.size());
		List<FluidStack> sourceFluids = null;
		int attemptedIndex = -1;

		if (pendingTarget != null) {
			PendingTarget pending = pendingTarget;
			pendingTarget = null;
			int index = pending.index();
			if (index >= 0 && index < targets.size()) {
				MechanicalFluidGunTargetConfig target = targets.get(index);
				BlockPos absTarget = target.absoluteFrom(be.gunPos());
				if (absTarget.equals(pending.pos())
					&& targets.isTargetValid(be.getLevel(), be.gunPos(), absTarget)
					&& !be.getBeltHandlerHelper().isBeltTarget(absTarget)) {
					if (!be.aimAtTarget(index)) {
						pendingTarget = pending;
						cycle.setTransferCooldown(1);
						return;
					}
					sourceFluids = snapshotSource(sourceHandler);
					BlockState targetState = be.getLevel().getBlockState(absTarget);
					ResolvedProcess process = resolveProcess(sourceFluids, target, targetState, absTarget);
					if (process.kind() != ProcessKind.NONE
						&& tryProcess(sourceHandler, target, absTarget, process)) {
						cycle.markScheduledTarget(index);
						cycle.setTransferCooldown(MechanicalFluidGunCycle.getSpeedAdjustedInterval(TRANSFER_INTERVAL, Math.abs(be.getSpeed())));
						return;
					}
					attemptedIndex = index;
				}
			}
		}

		if (sourceFluids == null) {
			sourceFluids = snapshotSource(sourceHandler);
		}

		for (int index : candidateIndices) {
			if (index == attemptedIndex) continue;
			MechanicalFluidGunTargetConfig target = targets.get(index);
			BlockPos absTarget = target.absoluteFrom(be.gunPos());
			if (!targets.isTargetValid(be.getLevel(), be.gunPos(), absTarget)) continue;
			if (be.getBeltHandlerHelper().isBeltTarget(absTarget)) continue;

			BlockState targetState = be.getLevel().getBlockState(absTarget);
			ResolvedProcess process = resolveProcess(sourceFluids, target, targetState, absTarget);
			if (process.kind() == ProcessKind.NONE) continue;

			if (!be.aimAtTarget(index)) {
				pendingTarget = new PendingTarget(index, absTarget);
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

	void clearPendingTarget() {
		pendingTarget = null;
	}

	private List<FluidStack> snapshotSource(IFluidHandler sourceHandler) {
		List<FluidStack> sourceFluids = new ArrayList<>();
		for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
			FluidStack fluid = sourceHandler.getFluidInTank(tank);
			if (!fluid.isEmpty()) {
				sourceFluids.add(fluid.copy());
			}
		}
		return sourceFluids;
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

		for (int step = 0; step < size; step++) {
			indices.add(Math.floorMod(start + step, size));
		}
		return indices;
	}

	private ResolvedProcess resolveProcess(List<FluidStack> sourceFluids, MechanicalFluidGunTargetConfig target,
										   BlockState targetState, BlockPos absTarget) {
		if (!targetState.is(MechanicalFluidGunBlock.TARGETS)) return ResolvedProcess.NONE;

		BlockEntity targetEntity = be.getLevel().getBlockEntity(absTarget);

		if (targetEntity != null && isDepot(targetEntity)) {
			ItemStack itemOnDepot = getItemOnDepot(targetEntity);
			if (itemOnDepot.isEmpty() || !FaucetFilling.canItemBeFilled(be.getLevel(), itemOnDepot)) {
				return ResolvedProcess.NONE;
			}
			FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForItem(be, sourceFluids, itemOnDepot);
			return fillableFluid.isEmpty()
				? ResolvedProcess.NONE
				: new ResolvedProcess(ProcessKind.DEPOT, null, itemOnDepot.copy(), fillableFluid);
		}

		if (targetEntity != null && isBelt(targetEntity)) {
			return ResolvedProcess.NONE;
		}

		if (targetState.is(Blocks.CAULDRON) || targetState.is(Blocks.WATER_CAULDRON)) {
			FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForCauldron(be, sourceFluids, targetState);
			return fillableFluid.isEmpty()
				? ResolvedProcess.NONE
				: new ResolvedProcess(ProcessKind.CAULDRON, null, ItemStack.EMPTY, fillableFluid);
		}

		if (targetEntity == null) return ResolvedProcess.NONE;

		IFluidHandler targetHandler = MechanicalFluidGunFillOperations.getTargetFluidHandler(
			be.getLevel(), targetEntity.getBlockPos(), target.face());
		if (targetHandler != null) {
			FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForContainer(be, sourceFluids, targetHandler, absTarget);
			if (!fillableFluid.isEmpty()) {
				return new ResolvedProcess(ProcessKind.CONTAINER, targetHandler, ItemStack.EMPTY, fillableFluid);
			}
		}

		FluidStack fuel = MechanicalFluidGunFillOperations.findFuelFluid(be, sourceFluids, targetState, absTarget);
		return fuel.isEmpty()
			? ResolvedProcess.NONE
			: new ResolvedProcess(ProcessKind.FUEL, null, ItemStack.EMPTY, fuel);
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
			return MechanicalFluidGunFillOperations.tryFuel(be, visuals, sourceHandler, targetState, absTarget,
				process.fluid());
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

		if (!itemFilling.isFillingDepot()) {
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
		if (!itemFilling.canCommit(currentItem)) {
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

		FluidStack drained = itemFilling.drainPendingFluid(sourceHandler);
		if (drained.isEmpty()) {
			abortFilling();
			return;
		}

		boolean completed = DepotFills.fillFirstMatchingItem(transportedHandler,
			transported -> itemFilling.canCommit(transported.stack),
			stack -> {
				stack.shrink(1);
				return itemFilling.getPreparedResult().copy();
			});
		if (!completed) {
			MechanicalFluidGunFillOperations.restoreToSource(sourceHandler, drained);
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

		be.clearDynamicAimPoint();
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
			pendingTarget = new PendingTarget(processableTarget,
				targets.get(processableTarget).absoluteFrom(be.gunPos()));
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
		List<FluidStack> sourceFluids = snapshotSource(sourceHandler);

		for (int index : getCandidateIndices(mode, size)) {
			MechanicalFluidGunTargetConfig target = targets.get(index);
			BlockPos absTarget = target.absoluteFrom(be.gunPos());
			if (!targets.isTargetValid(be.getLevel(), be.gunPos(), absTarget)) continue;

			BlockState targetState = be.getLevel().getBlockState(absTarget);
			if (resolveProcess(sourceFluids, target, targetState, absTarget).kind() != ProcessKind.NONE) {
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

	private record ResolvedProcess(ProcessKind kind, @Nullable IFluidHandler targetHandler,
		ItemStack item, FluidStack fluid) {

		private static final ResolvedProcess NONE = new ResolvedProcess(ProcessKind.NONE, null,
			ItemStack.EMPTY, FluidStack.EMPTY);
	}

	private record PendingTarget(int index, BlockPos pos) {
	}

	private enum ProcessKind {
		NONE,
		DEPOT,
		CAULDRON,
		CONTAINER,
		FUEL
	}
}
