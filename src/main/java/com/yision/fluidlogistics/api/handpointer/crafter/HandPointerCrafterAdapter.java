package com.yision.fluidlogistics.api.handpointer.crafter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface HandPointerCrafterAdapter {

    boolean matches(Level level, BlockPos pos, BlockState state);

    Direction getFacing(Level level, BlockPos pos, BlockState state);

    Direction getTargetDirection(Level level, BlockPos pos, BlockState state);

    boolean canConnect(Level level, BlockPos pos, Direction direction);

    boolean areConnected(Level level, BlockPos first, BlockPos second);

    void toggleConnection(Level level, BlockPos first, BlockPos second);

    void setTargetDirection(Level level, BlockPos pos, Direction direction);
}
