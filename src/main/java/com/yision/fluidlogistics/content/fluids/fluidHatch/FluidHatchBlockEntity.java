/*
 * Copyright (C) 2025 DragonsPlus
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Ported from CreateDragonsPlus to CreateFluidLogistics.
 */

package com.yision.fluidlogistics.content.fluids.fluidHatch;

import com.simibubi.create.content.logistics.itemHatch.HatchFilterSlot;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public class FluidHatchBlockEntity extends SmartBlockEntity {
    public FilteringBehaviour filtering;
    private long openUntilGameTime;
    private long scheduledCloseGameTime;

    public FluidHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(filtering = new FilteringBehaviour(this, new HatchFilterSlot()).forFluids());
    }

    public boolean testFluid(FluidStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (filtering == null) {
            return true;
        }
        return filtering.test(stack);
    }

    void extendOpen(ServerLevel level, int ticks) {
        openUntilGameTime = Math.max(openUntilGameTime, level.getGameTime() + ticks);
    }

    boolean shouldRemainOpen(ServerLevel level) {
        return openUntilGameTime > level.getGameTime();
    }

    boolean hasScheduledCloseTick(ServerLevel level) {
        return scheduledCloseGameTime > level.getGameTime();
    }

    int getRemainingOpenTicks(ServerLevel level) {
        long remaining = openUntilGameTime - level.getGameTime();
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, remaining));
    }

    void markCloseTickScheduled(ServerLevel level, int delay) {
        scheduledCloseGameTime = level.getGameTime() + delay;
    }

    void clearOpenPulse() {
        openUntilGameTime = 0;
        scheduledCloseGameTime = 0;
    }

    public @Nullable IFluidHandler getFluidDisplayCapability() {
        if (level == null) {
            return null;
        }
        IFluidHandler target = FluidHatchTarget.getTargetHandler(level, worldPosition, getBlockState());
        if (target == null) {
            return null;
        }

        com.yision.fluidlogistics.util.MergedFluidDisplayHandler display =
            new com.yision.fluidlogistics.util.MergedFluidDisplayHandler(target, this::testFluid);
        return display;
    }
}

final class FluidHatchTarget {
    private FluidHatchTarget() {
    }

    static @Nullable Direction getFacing(BlockState state) {
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING)
                : null;
    }

    static BlockPos getTargetPos(BlockPos hatchPos, Direction facing) {
        return hatchPos.relative(facing);
    }

    static @Nullable IFluidHandler getTargetHandler(Level level, BlockPos hatchPos, BlockState state) {
        Direction facing = getFacing(state);
        if (facing == null) {
            return null;
        }
        BlockPos targetPos = getTargetPos(hatchPos, facing);
        if (!level.isLoaded(targetPos)) {
            return null;
        }
        if (level.getBlockState(targetPos).getBlock() instanceof FluidHatchBlock) {
            return null;
        }
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        if (targetEntity == null) {
            return null;
        }
        return targetEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
    }
}
