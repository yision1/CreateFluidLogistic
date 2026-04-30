package com.yision.fluidlogistics.block.SmartHopper;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmartHopperFilterSlotPositioning extends ValueBoxTransform.Sided {

	@Override
	public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(SmartHopperBlock.FACING);
		Direction side = getSide();
		float y = 13;

		return switch (side) {
			case NORTH -> VecHelper.voxelSpace(8, y, 0.5);
			case SOUTH -> VecHelper.voxelSpace(8, y, 15.5);
			case EAST -> VecHelper.voxelSpace(15.5f, y, 8);
			case WEST -> VecHelper.voxelSpace(0.5f, y, 8);
			default -> VecHelper.voxelSpace(8, y, 8);
		};
	}

	@Override
	protected boolean isSideActive(BlockState state, Direction direction) {
		return direction.getAxis().isHorizontal();
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
		Direction side = getSide();

		TransformStack.of(ms)
			.rotateYDegrees(AngleHelper.horizontalAngle(side) + 180)
			.rotateXDegrees(0);
	}

	@Override
	protected Vec3 getSouthLocation() {
		return Vec3.ZERO;
	}
}
