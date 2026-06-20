package com.yision.fluidlogistics.block.MechanicalFluidGun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MechanicalFluidGunTarget {

	public static final float DEFAULT_YAW = 0f;
	public static final float DEFAULT_PITCH = -0.2f;

	static final double PITCH_PIVOT_X = 8.0 / 16.0;
	static final double PITCH_PIVOT_Y = 22.0 / 16.0;
	static final double PITCH_PIVOT_Z = 8.0 / 16.0;

	private static final double GUNPOINT_BOTTOM_LENGTH = 13.5 / 16.0;

	private MechanicalFluidGunTarget() {
	}

	public static float computeYaw(BlockPos gunPos, BlockState state, Vec3 targetCenter) {
		Direction mountFace = MechanicalFluidGunMount.getMountFace(state);

		Vec3 localTarget = MechanicalFluidGunMount.toLocal(mountFace,
			targetCenter.subtract(Vec3.atLowerCornerOf(gunPos)));

		Vec3 pivot = new Vec3(PITCH_PIVOT_X, PITCH_PIVOT_Y, PITCH_PIVOT_Z);
		double dx = localTarget.x - pivot.x;
		double dz = localTarget.z - pivot.z;
		if (dx * dx + dz * dz < 0.001) {
			return DEFAULT_YAW;
		}
		return (float) Math.toDegrees(Math.atan2(-dx, -dz));
	}

	public static float computePitch(BlockPos gunPos, BlockState state, Vec3 targetCenter) {
		Direction mountFace = MechanicalFluidGunMount.getMountFace(state);

		Vec3 localTarget = MechanicalFluidGunMount.toLocal(mountFace,
			targetCenter.subtract(Vec3.atLowerCornerOf(gunPos)));

		Vec3 pivot = new Vec3(PITCH_PIVOT_X, PITCH_PIVOT_Y, PITCH_PIVOT_Z);
		double dx = localTarget.x - pivot.x;
		double dy = localTarget.y - pivot.y;
		double dz = localTarget.z - pivot.z;
		double horizontalDist = Math.sqrt(dx * dx + dz * dz);
		if (horizontalDist < 0.001) {
			return dy < 0 ? -90f : 0f;
		}
		return (float) Math.toDegrees(Math.atan2(dy, horizontalDist));
	}

	public static Vec3 getNozzleWorldPos(BlockPos gunPos, BlockState state, float yaw, float pitch) {
		Direction mountFace = MechanicalFluidGunMount.getMountFace(state);

		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);

		double dx = -Math.sin(yawRad) * Math.cos(pitchRad) * GUNPOINT_BOTTOM_LENGTH;
		double dy = Math.sin(pitchRad) * GUNPOINT_BOTTOM_LENGTH;
		double dz = -Math.cos(yawRad) * Math.cos(pitchRad) * GUNPOINT_BOTTOM_LENGTH;

		Vec3 pivot = new Vec3(PITCH_PIVOT_X, PITCH_PIVOT_Y, PITCH_PIVOT_Z);
		Vec3 localNozzle = pivot.add(dx, dy, dz);

		Vec3 worldOffset = MechanicalFluidGunMount.toWorld(mountFace, localNozzle);
		return Vec3.atLowerCornerOf(gunPos).add(worldOffset);
	}

	public static Vec3 getTargetCenter(BlockPos targetPos) {
		return Vec3.atCenterOf(targetPos);
	}

	public static Vec3 getDepotItemCenter(BlockPos targetPos) {
		return Vec3.atLowerCornerOf(targetPos).add(0.5, 15 / 16f, 0.5);
	}
}
