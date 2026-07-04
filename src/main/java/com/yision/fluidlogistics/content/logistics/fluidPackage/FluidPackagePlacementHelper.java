package com.yision.fluidlogistics.content.logistics.fluidPackage;

import com.simibubi.create.foundation.fluid.FluidHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidPackagePlacementHelper {

    private FluidPackagePlacementHelper() {
    }

    public static boolean tryPlaceOneBucket(UseOnContext context, FluidStack fluidStack) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        if (tryPlaceAt(level, player, clickedPos, clickedFace, fluidStack, context.getItemInHand())) {
            return true;
        }
        return tryPlaceAt(level, player, clickedPos.relative(clickedFace), clickedFace, fluidStack, context.getItemInHand());
    }

    private static boolean tryPlaceAt(Level level, Player player, BlockPos pos, Direction face,
                                      FluidStack fluidStack, ItemStack container) {
        if (player != null && !level.mayInteract(player, pos)) {
            return false;
        }
        if (player != null && !player.mayUseItemAt(pos, face, container)) {
            return false;
        }

        Fluid fluid = fluidStack.getFluid();

        if (handleCauldron(level, pos, fluid)) {
            playEmptySound(level, player, pos, fluidStack);
            level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
            return true;
        }

        BlockState state = level.getBlockState(pos);
        boolean replaceable = state.canBeReplaced(fluid);

        if (state.getBlock() instanceof LiquidBlockContainer liquidBlockContainer
            && liquidBlockContainer.canPlaceLiquid(player, level, pos, state, fluid)) {
            if (fluid instanceof FlowingFluid flowingFluid) {
                liquidBlockContainer.placeLiquid(level, pos, state, flowingFluid.getSource(false));
                playEmptySound(level, player, pos, fluidStack);
                level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
                return true;
            }
        }

        if (!replaceable && !state.isAir()) {
            return false;
        }

        if (fluid.getFluidType().isVaporizedOnPlacement(level, pos, fluidStack)) {
            fluid.getFluidType().onVaporize(player, level, pos, fluidStack);
            return true;
        }

        if (level.dimensionType().ultraWarm() && fluid.is(FluidTags.WATER)) {
            level.playSound(
                player, pos,
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS,
                0.5F,
                2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
            for (int k = 0; k < 8; k++) {
                level.addParticle(
                    ParticleTypes.LARGE_SMOKE,
                    pos.getX() + Math.random(),
                    pos.getY() + Math.random(),
                    pos.getZ() + Math.random(),
                    0.0, 0.0, 0.0);
            }
            return true;
        }

        if (!(fluid instanceof FlowingFluid)) {
            return false;
        }

        if (!level.isClientSide && replaceable && !state.liquid()) {
            level.destroyBlock(pos, true);
        }

        if (!level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), 11)
            && !state.getFluidState().isSource()) {
            return false;
        }

        playEmptySound(level, player, pos, fluidStack);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
        return true;
    }

    private static boolean handleCauldron(Level level, BlockPos pos, Fluid fluid) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.CAULDRON)) {
            if (fluid.isSame(Fluids.WATER)) {
                level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
                return true;
            }
            if (fluid.isSame(Fluids.LAVA)) {
                level.setBlockAndUpdate(pos, Blocks.LAVA_CAULDRON.defaultBlockState());
                return true;
            }
        }
        return false;
    }

    private static void playEmptySound(LevelAccessor level, Player player, BlockPos pos, FluidStack fluidStack) {
        SoundEvent soundEvent = FluidHelper.getEmptySound(fluidStack);
        if (soundEvent == null) {
            soundEvent = fluidStack.getFluid().is(FluidTags.LAVA)
                ? SoundEvents.BUCKET_EMPTY_LAVA
                : SoundEvents.BUCKET_EMPTY;
        }
        level.playSound(player, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
