package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.network.MechanicalFluidGunPackets;
import com.yision.fluidlogistics.particle.MechanicalFluidGunStreamParticleData;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

class MechanicalFluidGunVisuals {

	private static final int STREAM_PARTICLES_PER_TICK = 12;
	private static final double STREAM_TRAIL_START = 0.03;
	private static final double STREAM_TRAIL_END = 0.92;
	private static final double STREAM_BASE_RADIUS = 0.018;
	private static final double STREAM_END_RADIUS = 0.085;
	private static final double STREAM_PARTICLE_SPEED = 0.13;
	private static final double STREAM_SPEED_VARIANCE = 0.08;
	private static final double STREAM_SPREAD_SPEED = 0.018;
	private static final int ITEM_COMPLETION_SPRAY_TICKS = 6;
	private static final int SPRAY_VISUAL_EXTRA_TICKS = 1;
	private static final float IDLE_CHASE_SPEED = 0.18f;
	private static final float MIN_ACTIVE_CHASE_SPEED = 0.08f;
	private static final float MAX_ACTIVE_CHASE_SPEED = 0.65f;

	private final LerpedFloat yaw;
	private final LerpedFloat pitch;

	private FluidStack renderingFluid = FluidStack.EMPTY;
	private boolean spraying;
	private int sprayTicks;
	private boolean advanceTargetAfterSpray = true;

	MechanicalFluidGunVisuals(LerpedFloat yaw, LerpedFloat pitch) {
		this.yaw = yaw;
		this.pitch = pitch;
	}

	FluidStack getRenderingFluid() {
		return renderingFluid;
	}

	boolean isSpraying() {
		return spraying;
	}

	boolean shouldAdvanceAfterSpray() {
		return advanceTargetAfterSpray;
	}

	void tickClient() {
		yaw.tickChaser();
		pitch.tickChaser();
	}

	void spawnClientSprayParticles(Level level, BlockPos gunPos, BlockState blockState,
								   @Nullable Vec3 aimPoint) {
		if (!spraying || renderingFluid.isEmpty() || aimPoint == null) return;

		Vec3 nozzle = MechanicalFluidGunTarget.getNozzleWorldPos(gunPos, blockState, yaw.getValue(), pitch.getValue());
		Vec3 motion = aimPoint.subtract(nozzle);
		if (motion.lengthSqr() < 0.001) return;

		double travelDistance = motion.length();
		Vec3 direction = motion.normalize();
		Vec3 side = direction.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 0.001) {
			side = direction.cross(new Vec3(1, 0, 0));
		}
		side = side.normalize();
		Vec3 up = side.cross(direction).normalize();

		for (int i = 0; i < STREAM_PARTICLES_PER_TICK; i++) {
			double trail = STREAM_TRAIL_START
				+ (STREAM_TRAIL_END - STREAM_TRAIL_START) * ((i + level.random.nextDouble()) / STREAM_PARTICLES_PER_TICK);
			double radius = STREAM_BASE_RADIUS + (STREAM_END_RADIUS - STREAM_BASE_RADIUS) * trail;
			Vec3 offset = side.scale(randomSigned(level, radius))
				.add(up.scale(randomSigned(level, radius)));
			Vec3 pos = nozzle.lerp(aimPoint, trail).add(offset);
			double forwardSpeed = STREAM_PARTICLE_SPEED + level.random.nextDouble() * STREAM_SPEED_VARIANCE;
			double remainingForwardDistance = travelDistance * (1.0 - trail);
			double cappedForwardSpeed = Math.min(forwardSpeed,
				remainingForwardDistance / MechanicalFluidGunStreamParticleData.LIFETIME);
			Vec3 velocity = direction.scale(cappedForwardSpeed)
				.add(side.scale(randomSigned(level, STREAM_SPREAD_SPEED)))
				.add(up.scale(randomSigned(level, STREAM_SPREAD_SPEED)));

			level.addAlwaysVisibleParticle(new MechanicalFluidGunStreamParticleData(renderingFluid),
				pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		}
	}

	private static double randomSigned(Level level, double scale) {
		return (level.random.nextDouble() * 2 - 1) * scale;
	}

	void startSpraying(FluidStack fluid, float speed, boolean advanceAfterSpray) {
		renderingFluid = fluid.copy();
		spraying = true;
		this.advanceTargetAfterSpray = advanceAfterSpray;
		sprayTicks = advanceAfterSpray ? getTransientSprayTicks(speed) : getItemCompletionSprayTicks(speed);
	}

	void startSpraying(FluidStack fluid, float speed) {
		startSpraying(fluid, speed, true);
	}

	static int getTransientSprayTicks(float speed) {
		return MechanicalFluidGunCycle.getSpeedAdjustedInterval(
			MechanicalFluidGunProcessor.TRANSFER_INTERVAL, Math.abs(speed)) + SPRAY_VISUAL_EXTRA_TICKS;
	}

	private static int getItemCompletionSprayTicks(float speed) {
		return Math.min(ITEM_COMPLETION_SPRAY_TICKS,
			MechanicalFluidGunCycle.getSpeedAdjustedInterval(10, Math.abs(speed)));
	}

	boolean tickTransientSpray(boolean isFillingItem, Runnable onSprayComplete) {
		if (!spraying || isFillingItem) return false;
		if (sprayTicks > 0) sprayTicks--;
		if (sprayTicks <= 0) {
			boolean shouldAdvance = advanceTargetAfterSpray;
			completeTransientSpray();
			if (shouldAdvance) {
				onSprayComplete.run();
			}
			return true;
		}
		return false;
	}

	private void completeTransientSpray() {
		spraying = false;
		renderingFluid = FluidStack.EMPTY;
		sprayTicks = 0;
		advanceTargetAfterSpray = true;
	}

	void clearSpray() {
		spraying = false;
		renderingFluid = FluidStack.EMPTY;
		sprayTicks = 0;
		advanceTargetAfterSpray = true;
	}

	void spawnServerSprayParticles(Level level, BlockPos gunPos, @Nullable Vec3 aimPoint) {
		if (!(level instanceof ServerLevel)) return;
		if (aimPoint == null) return;
		FluidStack fluid = renderingFluid;
		if (fluid.isEmpty()) return;

		MechanicalFluidGunPackets.SprayParticlePacket packet =
			new MechanicalFluidGunPackets.SprayParticlePacket(aimPoint, fluid.copy());
		FluidLogisticsPackets.sendToNear(level, gunPos, 64, packet);
	}

	void updateTargetAngles(BlockPos gunPos, BlockState blockState,
							@Nullable Vec3 aimPoint, boolean isCycleActive, boolean isFillingItem, float speed) {
		if (aimPoint != null && (isCycleActive || spraying || isFillingItem)) {
			float targetYaw = MechanicalFluidGunTarget.computeYaw(gunPos, blockState, aimPoint);
			float targetPitch = MechanicalFluidGunTarget.computePitch(gunPos, blockState, aimPoint);
			float chaseSpeed = getActiveChaseSpeed(speed);
			yaw.chase(targetYaw, chaseSpeed, LerpedFloat.Chaser.EXP);
			pitch.chase(targetPitch, chaseSpeed, LerpedFloat.Chaser.EXP);
		} else {
			float idleYaw = getIdleYaw(blockState);
			yaw.chase(idleYaw, IDLE_CHASE_SPEED, LerpedFloat.Chaser.EXP);
			pitch.chase(MechanicalFluidGunTarget.DEFAULT_PITCH, IDLE_CHASE_SPEED, LerpedFloat.Chaser.EXP);
		}
	}

	private static float getActiveChaseSpeed(float speed) {
		return Mth.clamp(MechanicalFluidGunCycle.getArmMovementProgressStep(speed) * 2.5f,
			MIN_ACTIVE_CHASE_SPEED, MAX_ACTIVE_CHASE_SPEED);
	}

	static float computeIdleYaw(BlockState state) {
		return getIdleYaw(state);
	}

	private static float getIdleYaw(BlockState state) {
		Direction facing = state.hasProperty(MechanicalFluidGunBlock.FACING)
			? state.getValue(MechanicalFluidGunBlock.FACING)
			: Direction.NORTH;
		return switch (facing) {
			case SOUTH -> 180f;
			case EAST -> -90f;
			case WEST -> 90f;
			default -> 0f;
		};
	}

	void write(CompoundTag tag) {
		if (!renderingFluid.isEmpty()) {
			tag.put("RenderingFluid", renderingFluid.writeToNBT(new CompoundTag()));
		}
		tag.putBoolean("Spraying", spraying);
		tag.putInt("SprayTicks", sprayTicks);
		tag.putBoolean("AdvanceTargetAfterSpray", advanceTargetAfterSpray);
	}

	void read(CompoundTag tag) {
		renderingFluid = tag.contains("RenderingFluid")
			? FluidStack.loadFluidStackFromNBT(tag.getCompound("RenderingFluid"))
			: FluidStack.EMPTY;
		spraying = tag.getBoolean("Spraying");
		sprayTicks = tag.getInt("SprayTicks");
		advanceTargetAfterSpray = !tag.contains("AdvanceTargetAfterSpray") || tag.getBoolean("AdvanceTargetAfterSpray");
	}
}
