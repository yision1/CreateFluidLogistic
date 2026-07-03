package com.yision.fluidlogistics.content.fluids.fluidHatch;

import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public final class FluidHatchFluidHandlerForwarder {
    private static final int MECHANICAL_FLUID_GUN_OPEN_TICKS = 45;

    private FluidHatchFluidHandlerForwarder() {}

    public static @Nullable IFluidHandler getForMechanicalFluidGun(Level level, BlockPos pos, BlockState state,
            @Nullable Direction side) {
        if (!isSupportedHatch(state) || !canExpose(state, side)) {
            return null;
        }
        return new ForwardedFluidHandler(level, pos);
    }

    public static @Nullable Direction getExposedSide(BlockState state) {
        if (!isSupportedHatch(state) || !state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return null;
        }
        return state.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
    }

    private static boolean isSupportedHatch(BlockState state) {
        return state.is(AllBlocks.FLUID_HATCH.get())
                && FeatureToggle.isEnabled(FeatureToggle.FLUID_HATCH);
    }

    private static boolean canExpose(BlockState state, @Nullable Direction side) {
        Direction outward = getExposedSide(state);
        return outward != null && (side == null || side == outward);
    }

    private static @Nullable IFluidHandler getTargetHandler(Level level, BlockPos pos) {
        return FluidHatchTarget.getTargetHandler(level, pos, level.getBlockState(pos));
    }

    private static boolean testFilter(Level level, BlockPos pos, FluidStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof FluidHatchBlockEntity hatchBlockEntity) {
            return hatchBlockEntity.testFluid(stack);
        }
        return true;
    }

    private static FluidStack visibleFluid(Level level, BlockPos pos, IFluidHandler target, int tank) {
        if (tank < 0 || tank >= target.getTanks()) {
            return FluidStack.EMPTY;
        }
        FluidStack stack = target.getFluidInTank(tank);
        return testFilter(level, pos, stack) ? stack : FluidStack.EMPTY;
    }

    private static class ForwardedFluidHandler implements IFluidHandler {
        private final Level level;
        private final BlockPos pos;

        private ForwardedFluidHandler(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Override
        public int getTanks() {
            IFluidHandler target = getTargetHandler(level, pos);
            return target == null ? 0 : target.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler target = getTargetHandler(level, pos);
            return target == null ? FluidStack.EMPTY : visibleFluid(level, pos, target, tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler target = getTargetHandler(level, pos);
            if (target == null || tank < 0 || tank >= target.getTanks()) {
                return 0;
            }
            FluidStack existing = target.getFluidInTank(tank);
            if (!existing.isEmpty() && !testFilter(level, pos, existing)) {
                return 0;
            }
            return target.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler target = getTargetHandler(level, pos);
            return target != null && testFilter(level, pos, stack) && target.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler target = getTargetHandler(level, pos);
            if (target == null || resource.isEmpty() || !testFilter(level, pos, resource)) {
                return 0;
            }
            int filled = target.fill(resource, action);
            if (filled > 0 && action.execute()) {
                pulseOpen(level, pos);
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    private static void pulseOpen(Level level, BlockPos pos) {
        FluidHatchBlock.pulseOpen(level, pos, MECHANICAL_FLUID_GUN_OPEN_TICKS);
    }
}
