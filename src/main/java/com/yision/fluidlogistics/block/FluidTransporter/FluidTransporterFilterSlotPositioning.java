package com.yision.fluidlogistics.block.FluidTransporter;

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

public class FluidTransporterFilterSlotPositioning extends ValueBoxTransform.Sided {

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FluidTransporterBlock.FACING);
        Direction side = getSide();

        if (facing.getAxis() == Direction.Axis.Y) {
            float yPlane = facing == Direction.UP ? 9 : 7;
            return switch (side) {
                case NORTH -> VecHelper.voxelSpace(8, yPlane, 0.5);
                case SOUTH -> VecHelper.voxelSpace(8, yPlane, 15.5);
                case EAST -> VecHelper.voxelSpace(15.5, yPlane, 8);
                case WEST -> VecHelper.voxelSpace(0.5, yPlane, 8);
                default -> VecHelper.voxelSpace(8, yPlane, 8);
            };
        }

        if (facing.getAxis() == Direction.Axis.Z) {
            float zPlane = facing == Direction.SOUTH ? 9 : 7;
            return switch (side) {
                case EAST -> VecHelper.voxelSpace(15.5, 8, zPlane);
                case WEST -> VecHelper.voxelSpace(0.5, 8, zPlane);
                case UP -> VecHelper.voxelSpace(8, 15.5, zPlane);
                case DOWN -> VecHelper.voxelSpace(8, 0.5, zPlane);
                default -> VecHelper.voxelSpace(8, 8, zPlane);
            };
        }

        float xPlane = facing == Direction.EAST ? 9 : 7;
        return switch (side) {
            case NORTH -> VecHelper.voxelSpace(xPlane, 8, 0.5);
            case SOUTH -> VecHelper.voxelSpace(xPlane, 8, 15.5);
            case UP -> VecHelper.voxelSpace(xPlane, 15.5, 8);
            case DOWN -> VecHelper.voxelSpace(xPlane, 0.5, 8);
            default -> VecHelper.voxelSpace(xPlane, 8, 8);
        };
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        Direction facing = state.getValue(FluidTransporterBlock.FACING);
        return direction.getAxis() != facing.getAxis();
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction side = getSide();
        Direction transporterFacing = state.getValue(FluidTransporterBlock.FACING);
        float xRot = side == Direction.UP ? 90 : side == Direction.DOWN ? 270 : 0;
        float yRot = AngleHelper.horizontalAngle(side) + 180;
        boolean horizontalBottomSlot = transporterFacing.getAxis().isHorizontal() && side == Direction.DOWN;

        if (side.getAxis() == Direction.Axis.Y) {
            TransformStack.of(ms)
                .rotateYDegrees(180 + AngleHelper.horizontalAngle(transporterFacing));
        }

        TransformStack transform = TransformStack.of(ms)
            .rotateYDegrees(yRot)
            .rotateXDegrees(xRot);
        if (horizontalBottomSlot) {
            transform.rotateZDegrees(180);
        }
    }

    @Override
    protected Vec3 getSouthLocation() {
        return Vec3.ZERO;
    }
}
