package com.yision.fluidlogistics.block.FluidPump;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FluidPumpDirectionSlot extends CenteredSideValueBoxTransform {

	public FluidPumpDirectionSlot() {
		super((state, d) -> d == FluidPumpBlock.getModelTop(state));
	}

	@Override
	protected Vec3 getSouthLocation() {
		return VecHelper.voxelSpace(8, 8, 14.5);
	}

	@Override
	public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
		super.rotate(level, pos, state, ms);
		float zRot = FluidPumpBlock.getValueBoxZRotation(state);
		if (zRot != 0)
			TransformStack.of(ms).rotateZDegrees(zRot);
	}

	@Override
	public int getOverrideColor() {
		return 0x592424;
	}
}
