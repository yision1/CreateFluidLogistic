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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
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

        FluidHatchFluidDisplayHandler display = new FluidHatchFluidDisplayHandler(target, this::testFluid);
        return display;
    }
}

final class FluidHatchFluidDisplayHandler implements IFluidHandler {
    private final List<DisplayedFluid> fluids = new ArrayList<>();

    FluidHatchFluidDisplayHandler(IFluidHandler source, Predicate<FluidStack> filter) {
        for (int tank = 0; tank < source.getTanks(); tank++) {
            FluidStack fluid = source.getFluidInTank(tank);
            if (fluid.isEmpty() || !filter.test(fluid)) {
                continue;
            }
            mergeDisplayedFluid(fluid, source.getTankCapacity(tank));
        }
    }

    private void mergeDisplayedFluid(FluidStack additionalFluid, int additionalCapacity) {
        for (DisplayedFluid entry : fluids) {
            if (FluidStack.isSameFluidSameComponents(entry.fluid, additionalFluid)) {
                entry.merge(additionalFluid, additionalCapacity);
                return;
            }
        }
        fluids.add(new DisplayedFluid(additionalFluid, additionalCapacity));
    }

    @Override
    public int getTanks() {
        return fluids.size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank >= 0 && tank < fluids.size() ? fluids.get(tank).fluid.copy() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank >= 0 && tank < fluids.size() ? fluids.get(tank).capacity : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }

    private static int saturatedAdd(int a, int b) {
        long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private static final class DisplayedFluid {
        final FluidStack fluid;
        int capacity;

        DisplayedFluid(FluidStack fluid, int capacity) {
            this.fluid = fluid.copy();
            this.capacity = capacity;
        }

        void merge(FluidStack additionalFluid, int additionalCapacity) {
            fluid.setAmount(saturatedAdd(fluid.getAmount(), additionalFluid.getAmount()));
            capacity = saturatedAdd(capacity, additionalCapacity);
        }
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
        return level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, null);
    }
}
