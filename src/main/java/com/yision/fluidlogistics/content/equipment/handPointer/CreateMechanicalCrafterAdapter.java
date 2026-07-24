package com.yision.fluidlogistics.content.equipment.handPointer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crafter.ConnectedInputHandler;
import com.simibubi.create.content.kinetics.crafter.CrafterHelper;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.yision.fluidlogistics.api.handpointer.crafter.HandPointerCrafterAdapter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class CreateMechanicalCrafterAdapter implements HandPointerCrafterAdapter {
    public static final CreateMechanicalCrafterAdapter INSTANCE = new CreateMechanicalCrafterAdapter();

    private CreateMechanicalCrafterAdapter() {
    }

    @Override
    public boolean matches(Level level, BlockPos pos, BlockState state) {
        return AllBlocks.MECHANICAL_CRAFTER.has(state)
            && level.getBlockEntity(pos) instanceof MechanicalCrafterBlockEntity;
    }

    @Override
    public Direction getFacing(Level level, BlockPos pos, BlockState state) {
        return state.getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
    }

    @Override
    public Direction getTargetDirection(Level level, BlockPos pos, BlockState state) {
        return MechanicalCrafterBlock.getTargetDirection(state);
    }

    @Override
    public boolean canConnect(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        Direction facing = getFacing(level, pos, state);
        return ConnectedInputHandler.shouldConnect(level, pos, facing.getOpposite(), direction);
    }

    @Override
    public boolean areConnected(Level level, BlockPos first, BlockPos second) {
        return CrafterHelper.areCraftersConnected(level, first, second);
    }

    @Override
    public void toggleConnection(Level level, BlockPos first, BlockPos second) {
        ConnectedInputHandler.toggleConnection(level, first, second);
    }

    @Override
    public void setTargetDirection(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        Direction facing = getFacing(level, pos, state);
        BlockState updated = state.setValue(
            MechanicalCrafterBlock.POINTING,
            MechanicalCrafterBlock.pointingFromFacing(direction.getOpposite(), facing));
        if (updated != state) {
            KineticBlockEntity.switchToBlockState(level, pos, updated);
        }
    }
}
