package com.yision.fluidlogistics.content.fluids.fluidHatch;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public final class FluidHatchFluidHandlerForwarder {
    private static final ResourceLocation DRAGONS_PLUS_FLUID_HATCH =
            ResourceLocation.fromNamespaceAndPath("create_dragons_plus", "fluid_hatch");

    private FluidHatchFluidHandlerForwarder() {}

    public static @Nullable IFluidHandler get(Level level, BlockPos pos, BlockState state, @Nullable Direction side) {
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
        if (state.is(AllBlocks.FLUID_HATCH.get())) {
            return FeatureToggle.isEnabled(FeatureToggle.FLUID_HATCH);
        }
        return DRAGONS_PLUS_FLUID_HATCH.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static boolean canExpose(BlockState state, @Nullable Direction side) {
        Direction outward = getExposedSide(state);
        return outward != null && (side == null || side == outward);
    }

    private static @Nullable IFluidHandler getTargetHandler(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return null;
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos tankPos = pos.relative(facing);
        if (!level.isLoaded(tankPos)) {
            return null;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, tankPos, null);
    }

    private static boolean testFilter(Level level, BlockPos pos, FluidStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        FilteringBehaviour filter = BlockEntityBehaviour.get(level, pos, FilteringBehaviour.TYPE);
        return filter == null || filter.test(stack);
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
            return target.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler target = getTargetHandler(level, pos);
            if (target == null || resource.isEmpty() || !testFilter(level, pos, resource)) {
                return FluidStack.EMPTY;
            }
            return target.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler target = getTargetHandler(level, pos);
            if (target == null || maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            for (int tank = 0; tank < target.getTanks(); tank++) {
                FluidStack visible = visibleFluid(level, pos, target, tank);
                if (!visible.isEmpty()) {
                    return target.drain(visible.copyWithAmount(maxDrain), action);
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
