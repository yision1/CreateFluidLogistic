package com.yision.fluidlogistics.compat.kaleidoscopetavern;

import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.ITapBehavior;
import com.github.ysbbbbbb.kaleidoscopetavern.game.tap.TapBehaviorManager;
import com.yision.fluidlogistics.block.Faucet.AbstractFaucetBlock;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

public final class KaleidoscopeTavernCompat {
    private static final int DISPLAY_AMOUNT = 250;

    private KaleidoscopeTavernCompat() {
    }

    public static boolean hasTapBehavior(BlockState sourceState) {
        return TapBehaviorManager.contains(sourceState.getBlock());
    }

    public static @Nullable TapOperation prepare(
        Level level,
        BlockPos faucetPos,
        BlockState faucetState,
        Predicate<FluidStack> fluidFilter
    ) {
        TapContext context = resolve(level, faucetPos, faucetState);
        ITapBehavior behavior = getBehavior(context.sourceState());
        if (behavior == null) {
            return null;
        }
        if (!behavior.isMatch(level, null, faucetPos, faucetState, context.sourceState(), context.destinationState())) {
            return null;
        }

        FluidStack mappedFluid = mapFluidForFilter(context.sourceState(), fluidFilter);
        if (mappedFluid == null) {
            return null;
        }

        ParticleOptions particle = behavior.onStartExtract(
            level, null, faucetPos, faucetState, context.sourceState(), context.destinationState());
        return new TapOperation(particle, mappedFluid);
    }

    public static boolean canStart(
        Level level,
        BlockPos faucetPos,
        BlockState faucetState,
        Predicate<FluidStack> fluidFilter
    ) {
        TapContext context = resolve(level, faucetPos, faucetState);
        ITapBehavior behavior = getBehavior(context.sourceState());
        if (behavior == null) {
            return false;
        }
        if (!behavior.isMatch(level, null, faucetPos, faucetState, context.sourceState(), context.destinationState())) {
            return false;
        }
        return mapFluidForFilter(context.sourceState(), fluidFilter) != null;
    }

    public static void finish(Level level, BlockPos faucetPos, BlockState faucetState) {
        TapContext context = resolve(level, faucetPos, faucetState);
        ITapBehavior behavior = getBehavior(context.sourceState());
        if (behavior == null) {
            return;
        }
        if (!behavior.isMatch(level, null, faucetPos, faucetState, context.sourceState(), context.destinationState())) {
            return;
        }
        behavior.onEndExtract(level, faucetPos, faucetState, context.sourceState(), context.destinationState());
    }

    private static @Nullable ITapBehavior getBehavior(BlockState sourceState) {
        if (!hasTapBehavior(sourceState)) {
            return null;
        }
        return TapBehaviorManager.get(sourceState.getBlock());
    }

    private static TapContext resolve(Level level, BlockPos faucetPos, BlockState faucetState) {
        Direction facing = faucetState.getValue(AbstractFaucetBlock.FACING);
        BlockPos sourcePos = faucetPos.relative(facing.getOpposite());
        BlockPos destinationPos = faucetPos.below();
        return new TapContext(level.getBlockState(sourcePos), level.getBlockState(destinationPos));
    }

    private static @Nullable FluidStack mapFluidForFilter(BlockState sourceState, Predicate<FluidStack> fluidFilter) {
        FluidStack mapped = FluidStack.EMPTY;
        if (sourceState.is(Blocks.WATER_CAULDRON)) {
            mapped = new FluidStack(Fluids.WATER, DISPLAY_AMOUNT);
        } else if (sourceState.is(Blocks.LAVA_CAULDRON)) {
            mapped = new FluidStack(Fluids.LAVA, DISPLAY_AMOUNT);
        }

        if (mapped.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return fluidFilter.test(mapped) ? mapped : null;
    }

    private record TapContext(BlockState sourceState, BlockState destinationState) {
    }

    public record TapOperation(@Nullable ParticleOptions particle, FluidStack mappedFluid) {
    }
}
