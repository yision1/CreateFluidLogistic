package com.yision.fluidlogistics.foundation.fluid;

import com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class CauldronFills {

    private CauldronFills() {
    }

    public static boolean canFill(BlockState targetState, FluidStack availableFluid) {
        if (availableFluid.isEmpty()) {
            return false;
        }

        if (availableFluid.getFluid() == Fluids.WATER) {
            if (targetState.is(Blocks.CAULDRON)) {
                return availableFluid.getAmount() >= 250;
            }

            if (targetState.is(Blocks.WATER_CAULDRON) && targetState.hasProperty(LayeredCauldronBlock.LEVEL)) {
                int currentLevel = targetState.getValue(LayeredCauldronBlock.LEVEL);
                return currentLevel < LayeredCauldronBlock.MAX_FILL_LEVEL && availableFluid.getAmount() >= 250;
            }
            return false;
        }

        if (!targetState.is(Blocks.CAULDRON)) {
            return false;
        }

        var cauldronInfo = CauldronSpoutingBehavior.CAULDRON_INFO.get(availableFluid.getFluid());
        return cauldronInfo != null && availableFluid.getAmount() >= cauldronInfo.amount();
    }

    public static FluidStack fill(Level level, IFluidHandler sourceHandler,
        BlockPos targetPos, BlockState targetState, FluidStack availableFluid) {
        if (availableFluid.getFluid() == Fluids.WATER) {
            if (targetState.is(Blocks.CAULDRON)) {
                return fillWaterLevel(level, sourceHandler, targetPos, 1);
            }

            if (targetState.is(Blocks.WATER_CAULDRON) && targetState.hasProperty(LayeredCauldronBlock.LEVEL)) {
                int currentLevel = targetState.getValue(LayeredCauldronBlock.LEVEL);
                if (currentLevel < LayeredCauldronBlock.MAX_FILL_LEVEL) {
                    return fillWaterLevel(level, sourceHandler, targetPos, currentLevel + 1);
                }
            }
            return FluidStack.EMPTY;
        }

        if (!targetState.is(Blocks.CAULDRON)) {
            return FluidStack.EMPTY;
        }

        var cauldronInfo = CauldronSpoutingBehavior.CAULDRON_INFO.get(availableFluid.getFluid());
        if (cauldronInfo == null || availableFluid.getAmount() < cauldronInfo.amount()) {
            return FluidStack.EMPTY;
        }

        FluidStack drained = sourceHandler.drain(availableFluid.copyWithAmount(cauldronInfo.amount()), FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < cauldronInfo.amount()) {
            return FluidStack.EMPTY;
        }

        level.setBlockAndUpdate(targetPos, cauldronInfo.cauldron());
        return drained;
    }

    public static FluidStack fillWaterLevel(Level level, IFluidHandler sourceHandler,
        BlockPos targetPos, int targetLevel) {
        FluidStack drained = sourceHandler.drain(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < 250) {
            return FluidStack.EMPTY;
        }

        level.setBlockAndUpdate(targetPos,
            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, targetLevel));
        return drained;
    }
}
