package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetFilling;
import com.yision.fluidlogistics.foundation.fluid.DepotFills;
import com.yision.fluidlogistics.foundation.fluid.FluidSourceScans;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.PASS;

class MechanicalFluidGunBeltHandler {

	private static final int BELT_KEEP_ALIVE_TICKS = 8;
	private static final int BELT_RETRY_COOLDOWN = 4;
	private static final double BELT_ITEM_RENDER_HEIGHT = 7.0 / 16.0;

	private final MechanicalFluidGunBlockEntity be;
	private int beltKeepAliveTicks;
	private int beltRetryCooldown;

	@Nullable
	private TransportedItemStack activeBeltItem;
	@Nullable
	private BlockPos activeBeltPos;

	MechanicalFluidGunBeltHandler(MechanicalFluidGunBlockEntity be) {
		this.be = be;
	}

	private boolean hasActiveBeltSession() {
		return activeBeltItem != null;
	}

	private boolean isActiveBeltItem(TransportedItemStack transported,
									 TransportedItemStackHandlerBehaviour handler) {
		return transported == activeBeltItem
			&& activeBeltPos != null
			&& handler.blockEntity != null
			&& activeBeltPos.equals(handler.blockEntity.getBlockPos());
	}

	private void bindActiveBeltSession(TransportedItemStack transported,
									  TransportedItemStackHandlerBehaviour handler, int targetIndex) {
		activeBeltItem = transported;
		activeBeltPos = handler.blockEntity.getBlockPos().immutable();
		be.setDynamicAimPoint(targetIndex, getBeltItemAimPoint(transported, handler));
	}

	private void clearActiveBeltSession() {
		activeBeltItem = null;
		activeBeltPos = null;
		be.clearDynamicAimPoint();
	}

	private boolean isImmediateDownstreamBeltPos(BlockPos currentPos, BlockPos candidatePos) {
		Level level = be.getLevel();
		if (!(level.getBlockEntity(currentPos) instanceof BeltBlockEntity currentBelt)) {
			return false;
		}
		if (!(level.getBlockEntity(candidatePos) instanceof BeltBlockEntity candidateBelt)) {
			return false;
		}
		if (!currentBelt.getController().equals(candidateBelt.getController())) {
			return false;
		}

		BeltBlockEntity controller = currentBelt.getControllerBE();
		if (controller == null) {
			return false;
		}

		int step = controller.getDirectionAwareBeltMovementSpeed() > 0 ? 1 : -1;
		return candidateBelt.index == currentBelt.index + step;
	}

	private boolean canPreemptWithNextBeltItem(TransportedItemStack candidate,
											   TransportedItemStackHandlerBehaviour handler,
											   FluidStack fillableFluid) {
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		if (!itemFilling.isFillingBelt() || activeBeltItem == null || activeBeltPos == null) {
			return false;
		}
		if (handler.blockEntity == null) {
			return false;
		}
		BlockPos candidatePos = handler.blockEntity.getBlockPos();
		if (!isImmediateDownstreamBeltPos(activeBeltPos, candidatePos)) {
			return false;
		}
		if (itemFilling.getProcessingItem().isEmpty()
			|| !ItemStack.isSameItemSameComponents(candidate.stack.copyWithCount(1),
				itemFilling.getProcessingItem())) {
			return false;
		}
		return !fillableFluid.isEmpty();
	}

	BeltProcessingBehaviour.ProcessingResult onBeltItemReceived(TransportedItemStack transported,
																	TransportedItemStackHandlerBehaviour handler) {
		if (handler.blockEntity.isVirtual()) return PASS;
		if (be.getSpeed() == 0 || be.isRedstoneLocked()) return PASS;

		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		int targetIndex = targets.getTargetIndexFor(be.gunPos(), handler.blockEntity.getBlockPos());
		if (targetIndex == -1) return PASS;

		if (!FaucetFilling.canItemBeFilled(be.getLevel(), transported.stack)) return PASS;

		IFluidHandler source = be.sourceHandler();
		if (source == null) return PASS;

		FluidStack fillable = MechanicalFluidGunFillOperations.findFillableFluidForItem(be, source, transported.stack);
		if (fillable.isEmpty()) return PASS;

		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();

		if (itemFilling.isFillingDepot()) {
			return HOLD;
		}

		if (itemFilling.isFillingBelt()) {
			if (canPreemptWithNextBeltItem(transported, handler, fillable)) {
				cancelBeltItemFilling();
				bindActiveBeltSession(transported, handler, targetIndex);
				be.setActiveTarget(targetIndex);
				keepBeltTargetAlive();
				return HOLD;
			}
			return PASS;
		}

		bindActiveBeltSession(transported, handler, targetIndex);
		be.setActiveTarget(targetIndex);
		keepBeltTargetAlive();
		return HOLD;
	}

	BeltProcessingBehaviour.ProcessingResult whenBeltItemHeld(TransportedItemStack transported,
															  TransportedItemStackHandlerBehaviour handler) {
		if (be.getSpeed() == 0) return PASS;

		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		if (be.isRedstoneLocked() && !itemFilling.isFillingBelt() && !hasActiveBeltSession()) {
			return PASS;
		}
		int targetIndex = targets.getTargetIndexFor(be.gunPos(), handler.blockEntity.getBlockPos());

		if (targetIndex == -1) {
			if (itemFilling.isFillingBelt()) {
				cancelBeltItemFilling();
			}
			return PASS;
		}

		keepBeltTargetAlive();

		if (hasActiveBeltSession() && !isActiveBeltItem(transported, handler)) {
			return PASS;
		}

		if (itemFilling.isFillingDepot()) {
			return HOLD;
		}

		if (itemFilling.isFillingBelt()) {
			if (!hasActiveBeltSession()) {
				cancelBeltItemFilling();
				return PASS;
			}
			BlockPos beltPos = handler.blockEntity.getBlockPos();
			if (!itemFilling.isProcessingBeltPos(beltPos)) {
				return HOLD;
			}
			if (!be.aimAtTarget(targetIndex)) {
				return HOLD;
			}
			if (itemFilling.getProcessingItem().isEmpty()
				|| transported.stack.getCount() < 1
				|| !ItemStack.isSameItemSameComponents(transported.stack.copyWithCount(1), itemFilling.getProcessingItem())) {
				cancelBeltItemFilling();
				return PASS;
			}
			if (itemFilling.getProcessingTicks() > 0) {
				itemFilling.decrementTicks();
				return HOLD;
			}
			return finishBeltItemFilling(transported, handler);
		}

		if (!be.aimAtTarget(targetIndex)) {
			return HOLD;
		}

		if (beltRetryCooldown > 0) {
			beltRetryCooldown--;
			return HOLD;
		}

		if (!FaucetFilling.canItemBeFilled(be.getLevel(), transported.stack)) {
			be.endWorkCycle();
			return PASS;
		}

		IFluidHandler source = be.sourceHandler();
		if (source == null) {
			be.endWorkCycle();
			return PASS;
		}

		FluidStack fillableFluid = MechanicalFluidGunFillOperations.findFillableFluidForItem(be, source, transported.stack);
		if (fillableFluid.isEmpty()) {
			if (FluidSourceScans.hasPotentialForItem(be.getLevel(), source, be::testFilter, transported.stack, false)) {
				beltRetryCooldown = BELT_RETRY_COOLDOWN;
				return HOLD;
			}
			be.endWorkCycle();
			return PASS;
		}

		boolean started = startBeltFilling(source, transported.stack, fillableFluid,
			handler.blockEntity.getBlockPos());
		if (started) {
			be.getCycleHelper().markScheduledTarget(targetIndex);
		}
		return started ? HOLD : PASS;
	}

	void tickActiveBeltFillingFallback() {
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		if (!itemFilling.isFillingBelt()) {
			return;
		}

		if (!hasActiveBeltSession()
			|| activeBeltPos == null
			|| !isConfiguredValidBeltTarget(activeBeltPos)) {
			cancelBeltItemFilling();
			return;
		}

		if (activeBeltItem.stack.isEmpty()
			|| itemFilling.getProcessingItem().isEmpty()
			|| !ItemStack.isSameItemSameComponents(activeBeltItem.stack.copyWithCount(1),
				itemFilling.getProcessingItem())) {
			cancelBeltItemFilling();
		}
	}

	private boolean isConfiguredValidBeltTarget(BlockPos beltPos) {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		return targets.getTargetIndexFor(be.gunPos(), beltPos) != -1
			&& targets.isTargetValid(be.getLevel(), be.gunPos(), beltPos);
	}

	private boolean startBeltFilling(IFluidHandler sourceHandler, ItemStack item, FluidStack availableFluid,
									 BlockPos beltPos) {
		return MechanicalFluidGunItemFilling.startFilling(
			be, sourceHandler, item, availableFluid,
			MechanicalFluidGunItemFilling.ProcessingTarget.BELT, beltPos);
	}

	private Vec3 getBeltItemAimPoint(TransportedItemStack transported,
									 TransportedItemStackHandlerBehaviour handler) {
		Vec3 itemPos = handler.getWorldPositionOf(transported)
			.add(0, BELT_ITEM_RENDER_HEIGHT, 0);

		if (!(handler.blockEntity instanceof BeltBlockEntity belt)) {
			return itemPos;
		}

		double sideOffset = transported.sideOffset;
		return belt.getBlockState().getValue(BeltBlock.HORIZONTAL_FACING).getAxis() == net.minecraft.core.Direction.Axis.Z
			? itemPos.add(sideOffset, 0, 0)
			: itemPos.add(0, 0, -sideOffset);
	}

	private BeltProcessingBehaviour.ProcessingResult finishBeltItemFilling(TransportedItemStack transported,
																		  TransportedItemStackHandlerBehaviour handler) {
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		MechanicalFluidGunVisuals visuals = be.getVisualsHelper();

		if (!itemFilling.isFillingBelt()) {
			return PASS;
		}
		if (!itemFilling.canCommit(transported.stack)) {
			cancelBeltItemFilling();
			return PASS;
		}

		IFluidHandler sourceHandler = be.sourceHandler();
		if (sourceHandler == null) {
			cancelItemFilling();
			return PASS;
		}

		FluidStack drained = itemFilling.drainPendingFluid(sourceHandler);
		if (drained.isEmpty()) {
			cancelItemFilling();
			return PASS;
		}

		ItemStack resultStack = itemFilling.getPreparedResult().copy();
		transported.stack.shrink(1);
		DepotFills.completeItemFill(handler, transported, resultStack);

		MechanicalFluidGunTargetConfig activeTarget = be.getTargetsHelper().getActiveTarget();
		Vec3 aimPoint = be.getTargetAimPoint(activeTarget);
		visuals.spawnServerSprayParticles(be.getLevel(), be.gunPos(), aimPoint);
		be.getLevel().playSound(null, be.gunPos(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.5f, 1.0f + be.getLevel().random.nextFloat() * 0.2f);

		clearActiveBeltSession();
		itemFilling.clear();
		be.endWorkCycle();
		return HOLD;
	}

	void keepBeltTargetAlive() {
		beltKeepAliveTicks = BELT_KEEP_ALIVE_TICKS;
	}

	boolean shouldWaitForBeltCallback() {
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		int activeIndex = targets.getActiveTargetIndex();

		if (activeIndex < 0 || activeIndex >= targets.size()) {
			return false;
		}

		BlockPos absTarget = targets.get(activeIndex).absoluteFrom(be.gunPos());
		if (!isBeltTarget(absTarget)) {
			return false;
		}

		return beltKeepAliveTicks > 0;
	}

	void tickKeepAlive() {
		if (beltKeepAliveTicks > 0) {
			beltKeepAliveTicks--;
		}
	}

	void clearBeltState() {
		beltKeepAliveTicks = 0;
		beltRetryCooldown = 0;
		clearActiveBeltSession();
	}

	boolean resumeWaitingBeltItem() {
		if (be.getSpeed() == 0 || be.isRedstoneLocked())
			return false;
		if (be.sourceHandler() == null)
			return false;
		if (be.getItemFillingHelper().isFilling() || hasActiveBeltSession())
			return false;

		Level level = be.getLevel();
		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		IFluidHandler source = be.sourceHandler();
		if (targets.isEmpty())
			return false;

		for (int i = 0; i < targets.size(); i++) {
			MechanicalFluidGunTargetConfig target = targets.get(i);
			BlockPos absTarget = target.absoluteFrom(be.gunPos());
			if (!targets.isTargetValid(level, be.gunPos(), absTarget))
				continue;
			if (!isBeltTarget(absTarget))
				continue;

			TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(
				level, absTarget, TransportedItemStackHandlerBehaviour.TYPE);
			if (handler == null)
				continue;

			final int targetIndex = i;
			boolean[] found = {false};
			handler.handleProcessingOnAllItems(transported -> {
				if (found[0])
					return TransportedResult.doNothing();

				if (!FaucetFilling.canItemBeFilled(level, transported.stack))
					return TransportedResult.doNothing();

				FluidStack fillable = MechanicalFluidGunFillOperations
					.findFillableFluidForItem(be, source, transported.stack);
				if (fillable.isEmpty())
					return TransportedResult.doNothing();

				if (be.getItemFillingHelper().isFillingDepot())
					return TransportedResult.doNothing();

				found[0] = true;
				TransportedItemStack held = transported.copy();
				held.locked = true;
				held.lockedExternally = false;

				bindActiveBeltSession(held, handler, targetIndex);
				be.setActiveTarget(targetIndex);
				keepBeltTargetAlive();

				return TransportedResult.convertToAndLeaveHeld(List.of(), held);
			});

			if (found[0])
				return true;
		}
		return false;
	}

	boolean hasActiveBeltWorkAt(BlockPos beltPos) {
		return activeBeltPos != null && activeBeltPos.equals(beltPos)
			|| be.getItemFillingHelper().isProcessingBeltPos(beltPos);
	}

	boolean targetsBeltPos(BlockPos beltPos) {
		return be.getTargetsHelper().getTargetIndexFor(be.gunPos(), beltPos) != -1;
	}

	boolean isBeltTarget(BlockPos absTarget) {
		return be.getLevel().getBlockEntity(absTarget) instanceof BeltBlockEntity;
	}

	@Nullable
	static BeltProcessingBehaviour findProcessingAt(Level level, BlockPos beltPos) {
		List<BlockPos> gunPositions = MechanicalFluidGunTargetIndex.getGunsTargeting(level, beltPos);
		if (gunPositions.isEmpty()) {
			return null;
		}

		MechanicalFluidGunBlockEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos gunPos : gunPositions) {
			if (Math.abs(gunPos.getX() - beltPos.getX()) > MechanicalFluidGunBlockEntity.RANGE
				|| Math.abs(gunPos.getY() - beltPos.getY()) > MechanicalFluidGunBlockEntity.RANGE
				|| Math.abs(gunPos.getZ() - beltPos.getZ()) > MechanicalFluidGunBlockEntity.RANGE) {
				continue;
			}
			if (!(level.getBlockEntity(gunPos) instanceof MechanicalFluidGunBlockEntity gun)) {
				continue;
			}
			if (gun.getSpeed() == 0 || !gun.targetsBeltPos(beltPos) || gun.sourceHandler() == null) {
				continue;
			}
			if (gun.isRedstoneLocked() && !gun.getBeltHandlerHelper().hasActiveBeltWorkAt(beltPos)) {
				continue;
			}
			double distance = gunPos.distSqr(beltPos);
			if (distance < bestDistance) {
				best = gun;
				bestDistance = distance;
			}
		}

		return best == null ? null : best.beltProcessing;
	}

	private void cancelItemFilling() {
		be.getItemFillingHelper().clear();
		be.endWorkCycle();
	}

	private void cancelBeltItemFilling() {
		cancelItemFilling();
		clearBeltState();
	}
}
