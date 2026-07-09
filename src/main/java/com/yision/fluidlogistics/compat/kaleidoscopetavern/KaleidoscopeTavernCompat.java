package com.yision.fluidlogistics.compat.kaleidoscopetavern;

import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.ITapBehavior;
import com.github.ysbbbbbb.kaleidoscopetavern.game.tap.TapBehaviorManager;
import com.yision.fluidlogistics.compat.CompatMods;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetBlock;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class KaleidoscopeTavernCompat {
    private static final ResourceLocation KT_BARREL =
        ResourceLocation.fromNamespaceAndPath(CompatMods.KALEIDOSCOPE_TAVERN, "barrel");

    private KaleidoscopeTavernCompat() {
    }

    public static boolean hasTapBehavior(BlockState sourceState) {
        if (!isKnownTapSource(sourceState)) {
            return false;
        }
        return TapBehaviorManager.contains(sourceState);
    }

    public static @Nullable TapOperation prepare(
        Level level,
        BlockPos faucetPos,
        BlockState faucetState,
        Predicate<FluidStack> fluidFilter
    ) {
        return prepareInternal(level, faucetPos, faucetState, fluidFilter);
    }

    public static boolean canStart(
        Level level,
        BlockPos faucetPos,
        BlockState faucetState,
        Predicate<FluidStack> fluidFilter
    ) {
        return canStartInternal(level, faucetPos, faucetState, fluidFilter);
    }

    public static void finish(Level level, BlockPos faucetPos, BlockState faucetState) {
        finishInternal(level, faucetPos, faucetState);
    }

    private static @Nullable TapOperation prepareInternal(
        Level level,
        BlockPos faucetPos,
        BlockState faucetState,
        Predicate<FluidStack> fluidFilter
    ) {
        Direction facing = faucetState.getValue(FaucetBlock.FACING);
        BlockPos sourcePos = faucetPos.relative(facing.getOpposite());
        BlockPos destinationPos = faucetPos.below();
        BlockState sourceState = level.getBlockState(sourcePos);
        BlockState destinationState = level.getBlockState(destinationPos);

        ITapBehavior behavior = getBehavior(sourceState);
        if (behavior == null) {
            return null;
        }
        if (!behavior.isMatch(level, null, faucetPos, faucetState, sourceState, destinationState)) {
            return null;
        }

        FluidStack mappedFluid = mapFluidForFilter(sourceState, fluidFilter);
        if (mappedFluid == null) {
            return null;
        }

        ParticleOptions particle = behavior.onStartExtract(level, null, faucetPos, faucetState, sourceState, destinationState);
        return new TapOperation(particle, mappedFluid);
    }

    private static boolean canStartInternal(
        Level level,
        BlockPos faucetPos,
        BlockState faucetState,
        Predicate<FluidStack> fluidFilter
    ) {
        Direction facing = faucetState.getValue(FaucetBlock.FACING);
        BlockPos sourcePos = faucetPos.relative(facing.getOpposite());
        BlockPos destinationPos = faucetPos.below();
        BlockState sourceState = level.getBlockState(sourcePos);
        BlockState destinationState = level.getBlockState(destinationPos);

        ITapBehavior behavior = getBehavior(sourceState);
        if (behavior == null) {
            return false;
        }
        if (!behavior.isMatch(level, null, faucetPos, faucetState, sourceState, destinationState)) {
            return false;
        }
        return mapFluidForFilter(sourceState, fluidFilter) != null;
    }

    private static void finishInternal(Level level, BlockPos faucetPos, BlockState faucetState) {
        Direction facing = faucetState.getValue(FaucetBlock.FACING);
        BlockPos sourcePos = faucetPos.relative(facing.getOpposite());
        BlockPos destinationPos = faucetPos.below();
        BlockState sourceState = level.getBlockState(sourcePos);
        BlockState destinationState = level.getBlockState(destinationPos);

        ITapBehavior behavior = getBehavior(sourceState);
        if (behavior == null) {
            return;
        }
        if (!behavior.isMatch(level, null, faucetPos, faucetState, sourceState, destinationState)) {
            return;
        }
        behavior.onEndExtract(level, faucetPos, faucetState, sourceState, destinationState);
    }

    private static @Nullable ITapBehavior getBehavior(BlockState sourceState) {
        if (!isKnownTapSource(sourceState)) {
            return null;
        }
        return TapBehaviorManager.get(sourceState);
    }

    private static @Nullable FluidStack mapFluidForFilter(BlockState sourceState, Predicate<FluidStack> fluidFilter) {
        FluidStack mapped = FluidStack.EMPTY;
        if (sourceState.is(Blocks.WATER_CAULDRON)) {
            mapped = new FluidStack(Fluids.WATER, 250);
        } else if (sourceState.is(Blocks.LAVA_CAULDRON)) {
            mapped = new FluidStack(Fluids.LAVA, 250);
        }
        if (mapped.isEmpty()) {
            return FluidStack.EMPTY;
        }
        return fluidFilter.test(mapped) ? mapped : null;
    }

    private static boolean isKnownTapSource(BlockState state) {
        return state.is(Blocks.WATER_CAULDRON)
            || state.is(Blocks.LAVA_CAULDRON)
            || state.is(Blocks.BEE_NEST)
            || state.is(Blocks.BEEHIVE)
            || state.is(Blocks.DRAGON_HEAD)
            || state.is(Blocks.DRAGON_WALL_HEAD)
            || state.is(Blocks.MELON)
            || KT_BARREL.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public record TapOperation(@Nullable ParticleOptions particle, FluidStack mappedFluid) {
    }
}
