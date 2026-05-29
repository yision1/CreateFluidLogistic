package com.yision.fluidlogistics.block.SmartFaucet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.yision.fluidlogistics.block.Faucet.AbstractFaucetBlock;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmartFaucetFilterSlotPositioning extends ValueBoxTransform {

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(AbstractFaucetBlock.FACING).getOpposite();
        return VecHelper.rotateCentered(VecHelper.voxelSpace(8, 12.5f, 10), AngleHelper.horizontalAngle(facing),
            Direction.Axis.Y);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction facing = state.getValue(AbstractFaucetBlock.FACING).getOpposite();
        TransformStack.of(ms)
            .rotateYDegrees(AngleHelper.horizontalAngle(facing))
            .rotateXDegrees(90);
    }
}
