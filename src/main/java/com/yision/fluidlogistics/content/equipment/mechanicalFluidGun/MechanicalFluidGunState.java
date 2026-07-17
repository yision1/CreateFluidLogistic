package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetFilling;
import net.createmod.catnip.lang.Lang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

interface MechanicalFluidGunContext {
	Level level();

	BlockPos gunPos();

	float speed();

	boolean testFilter(FluidStack stack);

	@Nullable
	IFluidHandler sourceHandler();

	void notifyGunUpdate();

	boolean canFillFluidContainer(BlockPos targetPos, IFluidHandler targetHandler, FluidStack candidate);

	void markFluidContainerFilled(BlockPos targetPos);

	void finishFluidContainerFill(BlockPos targetPos);
}

class MechanicalFluidGunCycle {

	private static final int ARM_REFERENCE_MAX_SPEED = 256;
	private static final int MIN_SPEED_ADJUSTED_INTERVAL = 4;
	private static final int CONTAINER_REFILL_COOLDOWN = 600;

	private final Map<BlockPos, Integer> containerFillCooldowns = new HashMap<>();
	private final Set<BlockPos> containerFillSessions = new HashSet<>();

	private boolean workCycleActive;
	private float targetProgress = 1;
	private int transferCooldown;
	private int lastScheduledTargetIndex = -1;

	boolean isActive() {
		return workCycleActive;
	}

	void setActive(boolean active) {
		this.workCycleActive = active;
	}

	void setTargetProgress(float progress) {
		this.targetProgress = progress;
	}

	void setTransferCooldown(int cooldown) {
		this.transferCooldown = cooldown;
	}

	boolean tickCooldown() {
		if (transferCooldown > 0) {
			transferCooldown--;
			return true;
		}
		return false;
	}

	void reset() {
		workCycleActive = false;
		targetProgress = 1;
		transferCooldown = 0;
	}

	int getLastScheduledTargetIndex() {
		return lastScheduledTargetIndex;
	}

	void markScheduledTarget(int index) {
		lastScheduledTargetIndex = index;
	}

	void resetScheduledTarget() {
		lastScheduledTargetIndex = -1;
	}

	void tickContainerFillCooldowns() {
		containerFillCooldowns.entrySet().removeIf(entry -> {
			int next = entry.getValue() - 1;
			if (next <= 0) return true;
			entry.setValue(next);
			return false;
		});
	}

	boolean isContainerFillCooldownReady(BlockPos targetPos) {
		return containerFillCooldowns.getOrDefault(targetPos, 0) <= 0;
	}

	boolean canStartOrContinueContainerFill(BlockPos targetPos, boolean belowHalf) {
		BlockPos key = targetPos.immutable();
		if (containerFillSessions.contains(key)) {
			return true;
		}
		if (!belowHalf && !isContainerFillCooldownReady(key)) {
			return false;
		}
		containerFillSessions.add(key);
		return true;
	}

	void markContainerFilled(BlockPos targetPos) {
		containerFillSessions.add(targetPos.immutable());
	}

	void finishContainerFill(BlockPos targetPos) {
		BlockPos key = targetPos.immutable();
		if (containerFillSessions.remove(key)) {
			containerFillCooldowns.put(key, CONTAINER_REFILL_COOLDOWN);
		}
	}

	void clearContainerFillCooldowns() {
		containerFillCooldowns.clear();
		containerFillSessions.clear();
	}

	static int getSpeedAdjustedInterval(int baseInterval, float speed) {
		if (speed <= 0) {
			return baseInterval;
		}
		float cappedSpeed = Math.min(speed, ARM_REFERENCE_MAX_SPEED);
		return Mth.clamp(Math.round(baseInterval * 64f / cappedSpeed), MIN_SPEED_ADJUSTED_INTERVAL, baseInterval * 4);
	}

	static float getArmMovementProgressStep(float speed) {
		return Math.min(ARM_REFERENCE_MAX_SPEED, Math.abs(speed)) / 1024f;
	}

	boolean aim(float speed) {
		if (targetProgress >= 1) {
			return true;
		}
		targetProgress = Math.min(1, targetProgress + getArmMovementProgressStep(speed));
		return targetProgress >= 1;
	}

	void write(CompoundTag tag) {
		tag.putBoolean("WorkCycleActive", workCycleActive);
		tag.putFloat("TargetProgress", targetProgress);
	}

	void read(CompoundTag tag) {
		workCycleActive = tag.getBoolean("WorkCycleActive");
		targetProgress = tag.contains("TargetProgress")
			? tag.getFloat("TargetProgress")
			: (workCycleActive ? 0 : 1);
	}
}

class MechanicalFluidGunItemFilling {

	static final int FILLING_TIME = 20;

	enum ProcessingTarget {
		NONE, DEPOT, BELT
	}

	private boolean isFillingItem;
	private ItemStack processingItem = ItemStack.EMPTY;
	private FluidStack pendingFluid = FluidStack.EMPTY;
	private ProcessingTarget processingTarget = ProcessingTarget.NONE;
	private int processingTicks;
	private BlockPos processingBeltPos;
	private Vec3 processingBeltAimPoint;

	boolean isFilling() {
		return isFillingItem;
	}

	boolean isFillingDepot() {
		return isFillingItem && processingTarget == ProcessingTarget.DEPOT;
	}

	boolean isFillingBelt() {
		return isFillingItem && processingTarget == ProcessingTarget.BELT;
	}

	ItemStack getProcessingItem() {
		return processingItem;
	}

	@Nullable
	BlockPos getProcessingBeltPos() {
		return processingBeltPos;
	}

	@Nullable
	Vec3 getProcessingBeltAimPoint() {
		return processingBeltAimPoint;
	}

	FluidStack getPendingFluid() {
		return pendingFluid;
	}

	int getProcessingTicks() {
		return processingTicks;
	}

	void startDepot(ItemStack item, FluidStack fluid, int ticks) {
		isFillingItem = true;
		processingTarget = ProcessingTarget.DEPOT;
		processingItem = item.copyWithCount(1);
		pendingFluid = fluid.copy();
		processingTicks = ticks;
	}

	void startBelt(ItemStack item, FluidStack fluid, int ticks, BlockPos beltPos, Vec3 beltAimPoint) {
		isFillingItem = true;
		processingTarget = ProcessingTarget.BELT;
		processingItem = item.copyWithCount(1);
		pendingFluid = fluid.copy();
		processingTicks = ticks;
		processingBeltPos = beltPos.immutable();
		processingBeltAimPoint = beltAimPoint;
	}

	static boolean startFilling(MechanicalFluidGunBlockEntity be,
								IFluidHandler sourceHandler,
								ItemStack item,
								FluidStack availableFluid,
								ProcessingTarget targetType,
								@Nullable BlockPos beltPos) {
		return startFilling(be, sourceHandler, item, availableFluid, targetType, beltPos, null);
	}

	static boolean startFilling(MechanicalFluidGunBlockEntity be,
								IFluidHandler sourceHandler,
								ItemStack item,
								FluidStack availableFluid,
								ProcessingTarget targetType,
								@Nullable BlockPos beltPos,
								@Nullable Vec3 beltAimPoint) {
		int requiredAmount = FaucetFilling
			.getRequiredAmountForItem(be.getLevel(), item, availableFluid.copy());
		if (requiredAmount <= 0 || requiredAmount > availableFluid.getAmount()) return false;

		FluidStack simulatedDrain = sourceHandler.drain(
			availableFluid.copyWithAmount(requiredAmount), IFluidHandler.FluidAction.SIMULATE);
		if (simulatedDrain.isEmpty() || simulatedDrain.getAmount() < requiredAmount) return false;

		MechanicalFluidGunItemFilling itemFilling = be.getItemFillingHelper();
		MechanicalFluidGunVisuals visuals = be.getVisualsHelper();

		int fillingTicks = MechanicalFluidGunCycle.getSpeedAdjustedInterval(
			FILLING_TIME, Math.abs(be.getSpeed()));

		if (targetType == ProcessingTarget.BELT && beltPos != null && beltAimPoint != null) {
			itemFilling.startBelt(item, simulatedDrain, fillingTicks, beltPos, beltAimPoint);
		} else {
			itemFilling.startDepot(item, simulatedDrain, fillingTicks);
		}
		visuals.startSpraying(simulatedDrain, be.getSpeed(), false);
		AllSoundEvents.SPOUTING.playOnServer(
			be.getLevel(), be.gunPos(), 0.75f, 0.9f + be.getLevel().random.nextFloat() * 0.2f);
		be.notifyGunUpdate();
		return true;
	}

	void decrementTicks() {
		if (processingTicks > 0) processingTicks--;
	}

	boolean isProcessingBeltPos(BlockPos beltPos) {
		return processingTarget == ProcessingTarget.BELT
			&& processingBeltPos != null
			&& processingBeltPos.equals(beltPos);
	}

	void clear() {
		isFillingItem = false;
		processingTarget = ProcessingTarget.NONE;
		processingTicks = 0;
		processingItem = ItemStack.EMPTY;
		pendingFluid = FluidStack.EMPTY;
		processingBeltPos = null;
		processingBeltAimPoint = null;
	}

	void write(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putBoolean("IsFillingItem", isFillingItem);
		tag.putInt("ProcessingTarget", processingTarget.ordinal());
		tag.putInt("ProcessingTicks", processingTicks);
		if (!processingItem.isEmpty()) {
			tag.put("ProcessingItem", processingItem.save(registries));
		}
		if (!pendingFluid.isEmpty()) {
			tag.put("PendingFluid", pendingFluid.save(registries));
		}
		if (processingBeltPos != null) {
			tag.putLong("ProcessingBeltPos", processingBeltPos.asLong());
		}
		if (processingBeltAimPoint != null) {
			tag.putDouble("ProcessingBeltAimX", processingBeltAimPoint.x);
			tag.putDouble("ProcessingBeltAimY", processingBeltAimPoint.y);
			tag.putDouble("ProcessingBeltAimZ", processingBeltAimPoint.z);
		}
	}

	void read(CompoundTag tag, HolderLookup.Provider registries) {
		isFillingItem = tag.getBoolean("IsFillingItem");
		processingTarget = tag.contains("ProcessingTarget")
			? ProcessingTarget.values()[Math.min(tag.getInt("ProcessingTarget"), ProcessingTarget.values().length - 1)]
			: (isFillingItem ? ProcessingTarget.DEPOT : ProcessingTarget.NONE);
		processingTicks = tag.getInt("ProcessingTicks");
		processingItem = tag.contains("ProcessingItem")
			? ItemStack.parse(registries, tag.getCompound("ProcessingItem")).orElse(ItemStack.EMPTY)
			: ItemStack.EMPTY;
		pendingFluid = tag.contains("PendingFluid")
			? FluidStack.parse(registries, tag.getCompound("PendingFluid")).orElse(FluidStack.EMPTY)
			: FluidStack.EMPTY;
		processingBeltPos = tag.contains("ProcessingBeltPos")
			? BlockPos.of(tag.getLong("ProcessingBeltPos"))
			: null;
		processingBeltAimPoint = tag.contains("ProcessingBeltAimX")
			? new Vec3(tag.getDouble("ProcessingBeltAimX"), tag.getDouble("ProcessingBeltAimY"),
				tag.getDouble("ProcessingBeltAimZ"))
			: null;
	}
}

class MechanicalFluidGunTargets {

	private static final int RANGE = MechanicalFluidGunBlockEntity.RANGE;

	private final List<MechanicalFluidGunTargetConfig> targets = new ArrayList<>();
	private int activeTargetIndex = -1;

	List<MechanicalFluidGunTargetConfig> getTargets() {
		return Collections.unmodifiableList(targets);
	}

	boolean isEmpty() {
		return targets.isEmpty();
	}

	boolean hasTarget() {
		return !targets.isEmpty();
	}

	int getActiveTargetIndex() {
		return activeTargetIndex;
	}

	@Nullable
	MechanicalFluidGunTargetConfig getActiveTarget() {
		if (targets.isEmpty() || activeTargetIndex < 0 || activeTargetIndex >= targets.size()) {
			return null;
		}
		return targets.get(activeTargetIndex);
	}

	@Nullable
	BlockPos getAbsoluteTarget(BlockPos gunPos) {
		MechanicalFluidGunTargetConfig target = getActiveTarget();
		return target == null ? null : target.absoluteFrom(gunPos);
	}

	void setActiveTargetIndex(int index) {
		this.activeTargetIndex = index;
	}

	void resetActive() {
		this.activeTargetIndex = -1;
	}

	int size() {
		return targets.size();
	}

	MechanicalFluidGunTargetConfig get(int index) {
		return targets.get(index);
	}

	int getTargetIndexFor(BlockPos gunPos, BlockPos targetPos) {
		for (int i = 0; i < targets.size(); i++) {
			if (targets.get(i).absoluteFrom(gunPos).equals(targetPos)) {
				return i;
			}
		}
		return -1;
	}

	boolean isTargetValid(Level level, BlockPos gunPos, BlockPos absTarget) {
		if (!level.isLoaded(absTarget)) return false;
		return gunPos.distSqr(absTarget) <= RANGE * RANGE;
	}

	boolean hasValidTarget(Level level, BlockPos gunPos) {
		if (targets.isEmpty()) return false;
		for (MechanicalFluidGunTargetConfig target : targets) {
			if (isTargetValid(level, gunPos, target.absoluteFrom(gunPos))) {
				return true;
			}
		}
		return false;
	}

	void setTargets(List<MechanicalFluidGunTargetConfig> newTargets) {
		this.targets.clear();
		this.targets.addAll(newTargets);
		this.activeTargetIndex = -1;
	}

	void clear() {
		this.targets.clear();
		this.activeTargetIndex = -1;
	}

	void write(CompoundTag tag) {
		ListTag targetList = new ListTag();
		for (MechanicalFluidGunTargetConfig target : targets) {
			targetList.add(target.serialize());
		}
		tag.put("Targets", targetList);
		tag.putInt("ActiveTargetIndex", activeTargetIndex);
	}

	void read(CompoundTag tag) {
		targets.clear();
		if (tag.contains("Targets", Tag.TAG_LIST)) {
			ListTag targetList = tag.getList("Targets", Tag.TAG_COMPOUND);
			for (Tag targetTag : targetList) {
				targets.add(MechanicalFluidGunTargetConfig.deserialize((CompoundTag) targetTag));
			}
		}
		activeTargetIndex = tag.contains("ActiveTargetIndex")
			? tag.getInt("ActiveTargetIndex")
			: (targets.isEmpty() ? -1 : 0);
		if (activeTargetIndex >= targets.size()) {
			activeTargetIndex = targets.isEmpty() ? -1 : 0;
		}
	}

	void transform(StructureTransform transform) {
		if (targets.isEmpty()) return;
		for (int i = 0; i < targets.size(); i++) {
			targets.set(i, targets.get(i).transform(transform));
		}
		if (activeTargetIndex >= targets.size()) {
			activeTargetIndex = targets.isEmpty() ? -1 : 0;
		}
	}
}

enum MechanicalFluidGunScheduleMode implements INamedIconOptions {
	ROUND_ROBIN(AllIcons.I_ARM_ROUND_ROBIN),
	FORCED_ROUND_ROBIN(AllIcons.I_ARM_FORCED_ROUND_ROBIN),
	PREFER_FIRST(AllIcons.I_ARM_PREFER_FIRST),

	;

	private final String translationKey;
	private final AllIcons icon;

	MechanicalFluidGunScheduleMode(AllIcons icon) {
		this.icon = icon;
		this.translationKey = "fluidlogistics.mechanical_fluid_gun.schedule_mode." + Lang.asId(name());
	}

	@Override
	public AllIcons getIcon() {
		return icon;
	}

	@Override
	public String getTranslationKey() {
		return translationKey;
	}
}
