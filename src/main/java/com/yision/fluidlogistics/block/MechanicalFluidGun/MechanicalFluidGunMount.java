package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;


final class MechanicalFluidGunMount {

	private MechanicalFluidGunMount() {
	}

	static Direction getMountFace(BlockState state) {
		return state.hasProperty(MechanicalFluidGunBlock.MOUNT_FACE)
			? state.getValue(MechanicalFluidGunBlock.MOUNT_FACE)
			: Direction.UP;
	}

	static Direction getFacing(BlockState state) {
		Direction facing = state.hasProperty(MechanicalFluidGunBlock.FACING)
			? state.getValue(MechanicalFluidGunBlock.FACING)
			: Direction.NORTH;
		return normalizeFacing(getMountFace(state), facing);
	}

	static boolean isFacingValid(Direction mountFace, Direction facing) {
		return facing.getAxis().isHorizontal() && facing.getAxis() != mountFace.getAxis();
	}

	static Direction normalizeFacing(Direction mountFace, Direction facing) {
		if (isFacingValid(mountFace, facing)) return facing;
		for (Direction candidate : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
			if (candidate.getAxis() != mountFace.getAxis()) return candidate;
		}
		return Direction.NORTH;
	}

	static Direction resolveFacing(Direction mountFace, Direction playerHorizontal) {
		if (isFacingValid(mountFace, playerHorizontal)) return playerHorizontal;
		return playerHorizontal.getClockWise();
	}

	static void rotateModel(PoseStack ms, BlockState state) {
		Direction mountFace = getMountFace(state);
		rotateModel(ms, mountFace);
	}

	static void rotateModel(PoseStack ms, Direction mountFace) {
		ms.translate(0.5, 0.5, 0.5);

		switch (mountFace) {
			case UP -> {} // default, no rotation
			case DOWN -> ms.mulPose(Axis.XP.rotationDegrees(180));
			case NORTH -> ms.mulPose(Axis.XP.rotationDegrees(-90));
			case SOUTH -> ms.mulPose(Axis.XP.rotationDegrees(90));
			case EAST -> ms.mulPose(Axis.ZP.rotationDegrees(-90));
			case WEST -> ms.mulPose(Axis.ZP.rotationDegrees(90));
		}

		ms.translate(-0.5, -0.5, -0.5);
	}

	static Vec3 toLocal(Direction mountFace, Vec3 worldOffset) {
		Vec3 v = worldOffset.subtract(0.5, 0.5, 0.5);
		v = undoMountRotation(v, mountFace);
		return v.add(0.5, 0.5, 0.5);
	}

	static Vec3 toWorld(Direction mountFace, Vec3 localOffset) {
		Vec3 v = localOffset.subtract(0.5, 0.5, 0.5);
		v = applyMountRotation(v, mountFace);
		return v.add(0.5, 0.5, 0.5);
	}

	static Direction toLocalDirection(Direction mountFace, Direction worldDirection) {
		Vec3 v = Vec3.atLowerCornerOf(worldDirection.getNormal());
		v = undoMountRotation(v, mountFace);
		return Direction.getNearest(v.x, v.y, v.z);
	}

	private static Vec3 applyMountRotation(Vec3 v, Direction mountFace) {
		return switch (mountFace) {
			case UP -> v;
			case DOWN -> new Vec3(v.x, -v.y, -v.z);
			case NORTH -> new Vec3(v.x, v.z, -v.y);
			case SOUTH -> new Vec3(v.x, -v.z, v.y);
			case EAST -> new Vec3(v.y, -v.x, v.z);
			case WEST -> new Vec3(-v.y, v.x, v.z);
		};
	}

	private static Vec3 undoMountRotation(Vec3 v, Direction mountFace) {
		return switch (mountFace) {
			case UP -> v;
			case DOWN -> new Vec3(v.x, -v.y, -v.z);
			case NORTH -> new Vec3(v.x, -v.z, v.y);
			case SOUTH -> new Vec3(v.x, v.z, -v.y);
			case EAST -> new Vec3(-v.y, v.x, v.z);
			case WEST -> new Vec3(v.y, -v.x, v.z);
		};
	}

	private static final VoxelShape FLOOR_SHAPE = buildShape(Direction.UP);
	private static final VoxelShape CEILING_SHAPE = buildShape(Direction.DOWN);
	private static final VoxelShape NORTH_WALL_SHAPE = buildShape(Direction.NORTH);
	private static final VoxelShape SOUTH_WALL_SHAPE = buildShape(Direction.SOUTH);
	private static final VoxelShape EAST_WALL_SHAPE = buildShape(Direction.EAST);
	private static final VoxelShape WEST_WALL_SHAPE = buildShape(Direction.WEST);

	static VoxelShape getShapeForMount(Direction mountFace) {
		return switch (mountFace) {
			case UP -> FLOOR_SHAPE;
			case DOWN -> CEILING_SHAPE;
			case NORTH -> NORTH_WALL_SHAPE;
			case SOUTH -> SOUTH_WALL_SHAPE;
			case EAST -> EAST_WALL_SHAPE;
			case WEST -> WEST_WALL_SHAPE;
		};
	}

	private static VoxelShape buildShape(Direction mountFace) {
		return Shapes.or(
			rotatedBox(mountFace, 0, 0, 0, 16, 6, 16),
			rotatedBox(mountFace, 2, 6, 2, 14, 10, 14)
		);
	}

	private static VoxelShape rotatedBox(Direction mountFace, double minX, double minY, double minZ, double maxX,
		double maxY, double maxZ) {
		double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};

		for (double x : new double[]{minX, maxX}) {
			for (double y : new double[]{minY, maxY}) {
				for (double z : new double[]{minZ, maxZ}) {
					Vec3 rotated = toWorld(mountFace, new Vec3(x / 16, y / 16, z / 16)).scale(16);
					bounds[0] = Math.min(bounds[0], rotated.x);
					bounds[1] = Math.min(bounds[1], rotated.y);
					bounds[2] = Math.min(bounds[2], rotated.z);
					bounds[3] = Math.max(bounds[3], rotated.x);
					bounds[4] = Math.max(bounds[4], rotated.y);
					bounds[5] = Math.max(bounds[5], rotated.z);
				}
			}
		}

		return Block.box(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
	}
}
