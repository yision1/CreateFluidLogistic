package com.yision.fluidlogistics.block.MechanicalFluidGun;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.item.TooltipHelper;
import dev.engine_room.flywheel.lib.transform.TransformStack;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class MechanicalFluidGunBlockEntity extends KineticBlockEntity implements MechanicalFluidGunContext {

	public static final int RANGE = 5;

	public LerpedFloat yaw;
	public LerpedFloat pitch;

	private MechanicalFluidGunTargets targets;
	private MechanicalFluidGunCycle cycle;
	private MechanicalFluidGunVisuals visuals;
	private MechanicalFluidGunItemFilling itemFilling;
	private MechanicalFluidGunBeltHandler beltHandler;
	private MechanicalFluidGunProcessor processor;

	private ScrollOptionBehaviour<MechanicalFluidGunScheduleMode> scheduleMode;
	BeltProcessingBehaviour beltProcessing;

	public MechanicalFluidGunBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		ensureComponentsInitialized(state);
		yaw.startWithValue(MechanicalFluidGunVisuals.computeIdleYaw(state));
		pitch.startWithValue(MechanicalFluidGunTarget.DEFAULT_PITCH);
		setLazyTickRate(20);
	}

	private void ensureComponentsInitialized(BlockState state) {
		if (yaw != null) {
			return;
		}
		yaw = LerpedFloat.angular();
		pitch = LerpedFloat.angular();

		targets = new MechanicalFluidGunTargets();
		cycle = new MechanicalFluidGunCycle();
		visuals = new MechanicalFluidGunVisuals(yaw, pitch);
		itemFilling = new MechanicalFluidGunItemFilling();
		beltHandler = new MechanicalFluidGunBeltHandler(this);
		processor = new MechanicalFluidGunProcessor(this);

		yaw.startWithValue(state == null
			? MechanicalFluidGunTarget.DEFAULT_YAW
			: MechanicalFluidGunVisuals.computeIdleYaw(state));
		pitch.startWithValue(MechanicalFluidGunTarget.DEFAULT_PITCH);
	}

	@Override
	protected AABB createRenderBoundingBox() {
		return super.createRenderBoundingBox().inflate(RANGE);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		ensureComponentsInitialized(getBlockState());
		super.addBehaviours(behaviours);

		scheduleMode = new ScrollOptionBehaviour<>(
			MechanicalFluidGunScheduleMode.class,
			Component.translatable("fluidlogistics.mechanical_fluid_gun.schedule_mode"),
			this, new ScheduleModeSlotPositioning());
		behaviours.add(scheduleMode);

		beltProcessing = new BeltProcessingBehaviour(this)
			.whenItemEnters(beltHandler::onBeltItemReceived)
			.whileItemHeld(beltHandler::whenBeltItemHeld);
		behaviours.add(beltProcessing);
	}

	@Override
	public void initialize() {
		super.initialize();
		if (level != null && level.isClientSide) {
			updateVisuals();
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (!com.yision.fluidlogistics.config.FeatureToggle.isEnabled(com.yision.fluidlogistics.config.FeatureToggle.MECHANICAL_FLUID_GUN)) {
			return;
		}

		if (level == null) return;

		if (level.isClientSide) {
			visuals.tickClient();
			MechanicalFluidGunTargetConfig activeTarget = targets.getActiveTarget();
			Vec3 aimPoint = getTargetAimPoint(activeTarget);
			visuals.spawnClientSprayParticles(level, worldPosition, getBlockState(), aimPoint);
			return;
		}

		cycle.tickContainerFillCooldowns();
		processor.tickServer();
	}

	@Override
	public Level level() {
		return level;
	}

	@Override
	public BlockPos gunPos() {
		return worldPosition;
	}

	@Override
	public float speed() {
		return getSpeed();
	}

	@Override
	public boolean testFilter(FluidStack stack) {
		return true;
	}

	@Override
	@Nullable
	public IFluidHandler sourceHandler() {
		return getSourceHandler();
	}

	@Override
	public void notifyGunUpdate() {
		setChanged();
		notifyUpdate();
	}

	@Override
	public boolean canFillFluidContainer(BlockPos targetPos, IFluidHandler targetHandler, FluidStack candidate) {
		return cycle.canStartOrContinueContainerFill(targetPos,
			MechanicalFluidGunFillOperations.isFluidContainerBelowHalf(targetHandler, candidate));
	}

	@Override
	public void markFluidContainerFilled(BlockPos targetPos) {
		cycle.markContainerFilled(targetPos);
	}

	@Override
	public void finishFluidContainerFill(BlockPos targetPos) {
		cycle.finishContainerFill(targetPos);
	}

	MechanicalFluidGunTargets getTargetsHelper() {
		return targets;
	}

	MechanicalFluidGunCycle getCycleHelper() {
		return cycle;
	}

	MechanicalFluidGunVisuals getVisualsHelper() {
		return visuals;
	}

	MechanicalFluidGunItemFilling getItemFillingHelper() {
		return itemFilling;
	}

	MechanicalFluidGunBeltHandler getBeltHandlerHelper() {
		return beltHandler;
	}

	void setActiveTarget(int index) {
		boolean changedTarget = targets.getActiveTargetIndex() != index || !cycle.isActive();
		targets.setActiveTargetIndex(index);
		cycle.setActive(true);
		if (changedTarget) {
			cycle.setTargetProgress(0);
		}
		updateVisuals();
		if (changedTarget) {
			setChanged();
			notifyUpdate();
		}
	}

	boolean aimAtTarget(int index) {
		setActiveTarget(index);
		return cycle.aim(Math.abs(getSpeed()));
	}

	void endWorkCycle() {
		if (!cycle.isActive() && !visuals.isSpraying()) {
			return;
		}
		cycle.reset();
		visuals.clearSpray();
		targets.resetActive();
		beltHandler.clearBeltState();
		updateVisuals();
		notifyUpdate();
	}

	void updateVisuals() {
		MechanicalFluidGunTargetConfig activeTarget = targets.getActiveTarget();
		Vec3 aimPoint = getTargetAimPoint(activeTarget);
		visuals.updateTargetAngles(worldPosition, getBlockState(), aimPoint,
			cycle.isActive(), itemFilling.isFilling(), Math.abs(getSpeed()));
	}

	Vec3 getTargetAimPoint(@Nullable MechanicalFluidGunTargetConfig target) {
		if (target == null) return null;
		if (itemFilling.isFillingBelt() && itemFilling.getProcessingBeltAimPoint() != null) {
			return itemFilling.getProcessingBeltAimPoint();
		}
		BlockPos absTarget = target.absoluteFrom(worldPosition);
		if (level != null) {
			BlockEntity targetEntity = level.getBlockEntity(absTarget);
			if (processor.isDepot(targetEntity) && !processor.getItemOnDepot(targetEntity).isEmpty()) {
				return MechanicalFluidGunTarget.getDepotItemCenter(absTarget);
			}
		}
		return MechanicalFluidGunTarget.getTargetCenter(absTarget);
	}

	MechanicalFluidGunScheduleMode getScheduleMode() {
		return scheduleMode == null ? MechanicalFluidGunScheduleMode.ROUND_ROBIN : scheduleMode.get();
	}

	@Nullable
	IFluidHandler getSourceHandler() {
		if (level == null) return null;
		Direction sourceSide = MechanicalFluidGunMount.getMountFace(getBlockState());
		BlockPos sourcePos = worldPosition.relative(sourceSide.getOpposite());
		IFluidHandler sided = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, sourceSide);
		if (sided != null) return sided;
		return level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, null);
	}

	public boolean shouldRenderSourceInterface() {
		return getSourceHandler() != null;
	}


	public void setTargets(List<MechanicalFluidGunTargetConfig> newTargets) {
		targets.setTargets(newTargets);
		cycle.reset();
		cycle.resetScheduledTarget();
		cycle.setTransferCooldown(0);
		cycle.clearContainerFillCooldowns();
		beltHandler.clearBeltState();
		updateVisuals();
		setChanged();
		notifyUpdate();
	}

	public void clearTarget() {
		targets.clear();
		cycle.reset();
		cycle.resetScheduledTarget();
		cycle.clearContainerFillCooldowns();
		visuals.clearSpray();
		beltHandler.clearBeltState();
		updateVisuals();
		setChanged();
		notifyUpdate();
	}

	public boolean hasTarget() {
		return targets.hasTarget();
	}

	@Override
	public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (super.addToTooltip(tooltip, isPlayerSneaking))
			return true;
		if (isPlayerSneaking)
			return false;
		if (hasTarget())
			return false;

		TooltipHelper.addHint(tooltip, "hint.mechanical_fluid_gun_no_targets");
		return true;
	}

	public List<MechanicalFluidGunTargetConfig> getTargets() {
		return targets.getTargets();
	}


	public int getComparatorOutput() {
		if (targets.isEmpty()) return 0;
		IFluidHandler source = getSourceHandler();
		if (source == null) return 0;
		for (int tank = 0; tank < source.getTanks(); tank++) {
			if (!source.getFluidInTank(tank).isEmpty()) return 15;
		}
		return 0;
	}

	public boolean targetsBeltPos(BlockPos beltPos) {
		return beltHandler.targetsBeltPos(beltPos);
	}

	@Nullable
	public static BeltProcessingBehaviour getBeltProcessingAt(Level level, BlockPos beltPos) {
		return MechanicalFluidGunBeltHandler.findProcessingAt(level, beltPos);
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		targets.write(tag);
		cycle.write(tag);
		visuals.write(tag, registries);
		itemFilling.write(tag, registries);
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		targets.read(tag);
		cycle.read(tag);
		visuals.read(tag, registries);
		itemFilling.read(tag, registries);
		updateVisuals();
	}

	private static class ScheduleModeSlotPositioning extends ValueBoxTransform.Sided {

		private static final Vec3 FLOOR_SIDE_SLOT = VecHelper.voxelSpace(8, 3, 15.5);

		@Override
		public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
			Direction mountFace = MechanicalFluidGunMount.getMountFace(state);
			Direction localSide = MechanicalFluidGunMount.toLocalDirection(mountFace, getSide());
			Vec3 localSlot = VecHelper.rotateCentered(FLOOR_SIDE_SLOT, AngleHelper.horizontalAngle(localSide),
				Direction.Axis.Y);
			return MechanicalFluidGunMount.toWorld(mountFace, localSlot);
		}

		@Override
		public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
			Direction side = getSide();
			Direction sourceDirection = MechanicalFluidGunMount.getMountFace(state).getOpposite();
			float yRot = AngleHelper.horizontalAngle(side) + 180;
			float xRot = side == Direction.UP ? 90 : side == Direction.DOWN ? 270 : 0;
			float zRot = getIconRollTowardsSource(sourceDirection, xRot, yRot);

			TransformStack.of(ms)
				.rotateYDegrees(yRot)
				.rotateXDegrees(xRot)
				.rotateZDegrees(zRot);
		}

		@Override
		protected Vec3 getSouthLocation() {
			return FLOOR_SIDE_SLOT;
		}

		@Override
		protected boolean isSideActive(BlockState state, Direction direction) {
			return direction.getAxis() != MechanicalFluidGunMount.getMountFace(state).getAxis();
		}

		private static float getIconRollTowardsSource(Direction sourceDirection, float xRot, float yRot) {
			Vec3 currentBottom = rotateForSide(new Vec3(0, -1, 0), xRot, yRot).normalize();
			Vec3 currentRight = rotateForSide(new Vec3(1, 0, 0), xRot, yRot).normalize();
			Vec3 desiredBottom = Vec3.atLowerCornerOf(sourceDirection.getNormal());

			double sin = desiredBottom.dot(currentRight);
			double cos = desiredBottom.dot(currentBottom);
			if (Math.abs(sin) < 1e-6 && Math.abs(cos) < 1e-6) {
				return 0;
			}
			return (float) Math.toDegrees(Math.atan2(sin, cos));
		}

		private static Vec3 rotateForSide(Vec3 vec, float xRot, float yRot) {
			return VecHelper.rotate(VecHelper.rotate(vec, xRot, Direction.Axis.X), yRot, Direction.Axis.Y);
		}
	}
}
