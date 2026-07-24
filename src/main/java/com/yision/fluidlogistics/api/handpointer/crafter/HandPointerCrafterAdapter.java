package com.yision.fluidlogistics.api.handpointer.crafter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Connects one crafter implementation to CFL's Hand Pointer selection logic.
 * All directions in this contract are world directions; adapters translate
 * them to their own block-state representation.
 */
public interface HandPointerCrafterAdapter {

    /** Returns whether this adapter owns the block at {@code pos}. */
    boolean matches(Level level, BlockPos pos, BlockState state);

    /** Returns the crafter's horizontal front-facing direction. */
    Direction getFacing(Level level, BlockPos pos, BlockState state);

    /** Returns the world direction in which the crafter sends its output. */
    Direction getTargetDirection(Level level, BlockPos pos, BlockState state);

    /**
     * Returns whether the crafter can connect to the adjacent block in
     * {@code direction}, including compatibility checks for that neighbor.
     */
    boolean canConnect(Level level, BlockPos pos, Direction direction);

    /** Returns whether both positions belong to the same logical input group. */
    boolean areConnected(Level level, BlockPos first, BlockPos second);

    /** Toggles the input connection between two adjacent compatible crafters. */
    void toggleConnection(Level level, BlockPos first, BlockPos second);

    /** Sets the crafter's output to the supplied world direction. */
    void setTargetDirection(Level level, BlockPos pos, Direction direction);
}
