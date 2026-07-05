package com.yision.fluidlogistics.content.fluids.infiniteWater;

import com.yision.fluidlogistics.config.Config;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class InfiniteWaterSource {

    public enum Consumer { FAUCET, FLUID_TRANSPORTER, SMART_HOPPER }

    public static final IFluidHandler HANDLER = new InfiniteWaterFluidHandler();

    private InfiniteWaterSource() {}

    public static boolean isWaterSourceBlock(BlockState state) {
        return state.is(BlockTags.LEAVES)
            && state.hasProperty(BlockStateProperties.WATERLOGGED)
            && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    public static boolean isEnabledFor(Consumer consumer) {
        return switch (consumer) {
            case FAUCET -> Config.isFaucetInfiniteWaterEnabled();
            case FLUID_TRANSPORTER -> Config.isFluidTransporterInfiniteWaterEnabled();
            case SMART_HOPPER -> Config.isSmartHopperInfiniteWaterEnabled();
        };
    }

    public static boolean isActiveSourceFor(Consumer consumer, BlockState state) {
        return isEnabledFor(consumer) && isWaterSourceBlock(state);
    }

    @Nullable
    public static IFluidHandler getSourceHandler(Consumer consumer, BlockState state) {
        return isActiveSourceFor(consumer, state) ? HANDLER : null;
    }

    private static final class InfiniteWaterFluidHandler implements IFluidHandler {
        private static final FluidStack WATER = new FluidStack(Fluids.WATER, 1000);

        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return WATER.copy(); }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) {
                return FluidStack.EMPTY;
            }
            return new FluidStack(Fluids.WATER, Math.min(resource.getAmount(), WATER.getAmount()));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return maxDrain <= 0 ? FluidStack.EMPTY
                : new FluidStack(Fluids.WATER, Math.min(maxDrain, WATER.getAmount()));
        }
    }
}
