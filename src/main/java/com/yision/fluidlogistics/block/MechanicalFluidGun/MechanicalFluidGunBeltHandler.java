package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.yision.fluidlogistics.block.Faucet.FaucetFilling;
import com.yision.fluidlogistics.config.FeatureToggle;

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

	private final MechanicalFluidGunBlockEntity be;
	private int beltKeepAliveTicks;

	MechanicalFluidGunBeltHandler(MechanicalFluidGunBlockEntity be) {
		this.be = be;
	}

	BeltProcessingBehaviour.ProcessingResult onBeltItemReceived(TransportedItemStack transported,
																TransportedItemStackHandlerBehaviour handler) {
		if (!FeatureToggle.isEnabled(FeatureToggle.MECHANICAL_FLUID_GUN)) {
			return PASS;
		}
		if (handler.blockEntity.isVirtual()) return PASS;
		if (be.getSpeed() == 0) return PASS;

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
			return HOLD;
		}

		be.setActiveTarget(targetIndex);
		keepBeltTargetAlive();
		return HOLD;
	}

	BeltProcessingBehaviour.ProcessingResult whenBeltItemHeld(TransportedItemStack transported,
															  TransportedItemStackHandlerBehaviour handler) {
		if (!FeatureToggle.isEnabled(FeatureToggle.MECHANICAL_FLUID_GUN)) {
			return PASS;
		}
		if (be.getSpeed() == 0) return PASS;

		MechanicalFluidGunTargets targets = be.getTargetsHelper();
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		int targetIndex = targets.getTargetIndexFor(be.gunPos(), handler.blockEntity.getBlockPos());

		if (targetIndex == -1) {
			if (itemFilling.isFillingBelt()) {
				cancelItemFilling();
			}
			return PASS;
		}

		keepBeltTargetAlive();

		if (itemFilling.isFillingDepot()) {
			return HOLD;
		}

		if (itemFilling.isFillingBelt()) {
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
				cancelItemFilling();
				return PASS;
			}
			if (itemFilling.getProcessingTicks() > 0) {
				itemFilling.decrementTicks();
				return HOLD;
			}
			return finishBeltItemFilling(transported, handler);
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
			be.endWorkCycle();
			return PASS;
		}

		if (!be.aimAtTarget(targetIndex)) {
			return HOLD;
		}

		boolean started = startBeltFilling(source, transported.stack, fillableFluid,
			handler.blockEntity.getBlockPos());
		if (started) {
			be.getCycleHelper().markScheduledTarget(targetIndex);
		}
		return started ? HOLD : PASS;
	}

	private boolean startBeltFilling(IFluidHandler sourceHandler, ItemStack item, FluidStack availableFluid, BlockPos beltPos) {
		return MechanicalFluidGunItemFilling.startFilling(
			be, sourceHandler, item, availableFluid,
			MechanicalFluidGunItemFilling.ProcessingTarget.BELT, beltPos);
	}

	private BeltProcessingBehaviour.ProcessingResult finishBeltItemFilling(TransportedItemStack transported,
																		  TransportedItemStackHandlerBehaviour handler) {
		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		MechanicalFluidGunVisuals visuals = be.getVisualsHelper();

		if (!itemFilling.isFillingBelt() || itemFilling.getPendingFluid().isEmpty()) {
			return PASS;
		}

		IFluidHandler sourceHandler = be.sourceHandler();
		if (sourceHandler == null) {
			cancelItemFilling();
			return PASS;
		}

		FluidStack drained = sourceHandler.drain(itemFilling.getPendingFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
		if (drained.isEmpty() || drained.getAmount() < itemFilling.getPendingFluid().getAmount()) {
			cancelItemFilling();
			return PASS;
		}

		ItemStack resultStack = FaucetFilling.fillItem(be.getLevel(), itemFilling.getPendingFluid().getAmount(),
			transported.stack, itemFilling.getPendingFluid().copy());
		if (resultStack.isEmpty()) {
			cancelItemFilling();
			return PASS;
		}

		transported.clearFanProcessingData();
		List<TransportedItemStack> outList = new ArrayList<>();
		TransportedItemStack held = null;
		TransportedItemStack result = transported.copy();
		result.stack = resultStack;
		if (!transported.stack.isEmpty()) {
			held = transported.copy();
		}
		outList.add(result);
		handler.handleProcessingOnItem(transported, TransportedResult.convertToAndLeaveHeld(outList, held));

		MechanicalFluidGunTargetConfig activeTarget = be.getTargetsHelper().getActiveTarget();
		Vec3 aimPoint = be.getTargetAimPoint(activeTarget);
		visuals.spawnServerSprayParticles(be.getLevel(), be.gunPos(), aimPoint);
		be.getLevel().playSound(null, be.gunPos(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.5f, 1.0f + be.getLevel().random.nextFloat() * 0.2f);

		itemFilling.clear();
		be.endWorkCycle();
		be.notifyGunUpdate();
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
	}

	boolean targetsBeltPos(BlockPos beltPos) {
		return be.getTargetsHelper().getTargetIndexFor(be.gunPos(), beltPos) != -1;
	}

	boolean isBeltTarget(BlockPos absTarget) {
		return be.getLevel().getBlockEntity(absTarget) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
	}

	@Nullable
	static BeltProcessingBehaviour findProcessingAt(Level level, BlockPos beltPos) {
		if (!FeatureToggle.isEnabled(FeatureToggle.MECHANICAL_FLUID_GUN)) {
			return null;
		}
		MechanicalFluidGunBlockEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos candidate : BlockPos.betweenClosed(
			beltPos.offset(-MechanicalFluidGunBlockEntity.RANGE, -MechanicalFluidGunBlockEntity.RANGE, -MechanicalFluidGunBlockEntity.RANGE),
			beltPos.offset(MechanicalFluidGunBlockEntity.RANGE, MechanicalFluidGunBlockEntity.RANGE, MechanicalFluidGunBlockEntity.RANGE))) {
			if (!(level.getBlockEntity(candidate) instanceof MechanicalFluidGunBlockEntity gun)) {
				continue;
			}
			if (gun.getSpeed() == 0 || !gun.targetsBeltPos(beltPos) || gun.sourceHandler() == null) {
				continue;
			}
			double distance = candidate.distSqr(beltPos);
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
		be.notifyGunUpdate();
	}
}
