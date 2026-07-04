/*
 * Copyright (C) 2025 DragonsPlus
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Ported from CreateDragonsPlus to CreateFluidLogistics.
 */

package com.yision.fluidlogistics.content.fluids.fluidHatch;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllShapes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.fluid.FluidHelper.FluidExchange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import com.yision.fluidlogistics.content.fluids.itemTransfer.HatchStyleItemTransfer;
import com.yision.fluidlogistics.registry.AllBlockEntities;

public class FluidHatchBlock extends HorizontalDirectionalBlock
        implements IBE<FluidHatchBlockEntity>, IWrenchable, ProperWaterloggedBlock {
    public static final MapCodec<FluidHatchBlock> CODEC = simpleCodec(FluidHatchBlock::new);
    private static final int OPEN_TICKS = 10;

    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public FluidHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, OPEN, WATERLOGGED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace().getAxis().isVertical())
            return null;

        return withWater(defaultBlockState()
                .setValue(FACING, context.getClickedFace().getOpposite())
                .setValue(OPEN, false), context);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        updateWater(level, state, pos);
        return state;
    }

    public static void pulseOpen(Level level, BlockPos pos) {
        pulseOpen(level, pos, OPEN_TICKS);
    }

    static void pulseOpen(Level level, BlockPos pos, int ticks) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FluidHatchBlock))
            return;
        FluidHatchBlockEntity hatch = level.getBlockEntity(pos) instanceof FluidHatchBlockEntity fluidHatch
                ? fluidHatch
                : null;
        if (hatch != null) {
            hatch.extendOpen(serverLevel, ticks);
        }
        boolean wasOpen = state.getValue(OPEN);
        if (!wasOpen) {
            level.setBlockAndUpdate(pos, state.setValue(OPEN, true));
            AllSoundEvents.ITEM_HATCH.playOnServer(level, pos);
        }
        if (hatch != null) {
            if (!wasOpen || !hatch.hasScheduledCloseTick(serverLevel)) {
                scheduleCloseTick(serverLevel, pos, state.getBlock(), hatch);
            }
            return;
        }
        serverLevel.scheduleTick(pos, state.getBlock(), ticks);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN))
            return;
        FluidHatchBlockEntity hatch = level.getBlockEntity(pos) instanceof FluidHatchBlockEntity fluidHatch
                ? fluidHatch
                : null;
        if (hatch != null && hatch.shouldRemainOpen(level)) {
            if (!hatch.hasScheduledCloseTick(level)) {
                scheduleCloseTick(level, pos, this, hatch);
            }
            return;
        }
        if (hatch != null) {
            hatch.clearOpenPulse();
        }
        level.setBlockAndUpdate(pos, state.setValue(OPEN, false));
    }

    private static void scheduleCloseTick(ServerLevel level, BlockPos pos, Block block, FluidHatchBlockEntity hatch) {
        int delay = hatch.getRemainingOpenTicks(level);
        hatch.markCloseTickScheduled(level, delay);
        level.scheduleTick(pos, block, delay);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide())
            return ItemInteractionResult.SUCCESS;

        if (player instanceof FakePlayer)
            return ItemInteractionResult.SUCCESS;

        BlockEntity blockEntity = level.getBlockEntity(pos.relative(state.getValue(FACING)));
        if (blockEntity == null)
            return ItemInteractionResult.FAIL;

        IFluidHandler tankCapability = level.getCapability(FluidHandler.BLOCK, blockEntity.getBlockPos(), null);
        if (tankCapability == null)
            return ItemInteractionResult.FAIL;

        FilteringBehaviour filter = BlockEntityBehaviour.get(level, pos, FilteringBehaviour.TYPE);
        if (filter == null)
            return ItemInteractionResult.FAIL;

        boolean tankIsCreative = blockEntity instanceof CreativeFluidTankBlockEntity;
        Runnable onChanged = () -> {
            blockEntity.setChanged();
            if (level instanceof ServerLevel serverLevel)
                serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());
        };

        FluidExchange exchange;
        FluidStack fluidStack;
        if (player.isSecondaryUseActive()) {
            if (!(fluidStack = HatchStyleItemTransfer.tryFillItem(level, player, hand, stack, tankCapability, filter, tankIsCreative, onChanged)).isEmpty()) {
                exchange = FluidExchange.TANK_TO_ITEM;
            } else if (!(fluidStack = HatchStyleItemTransfer.tryEmptyItem(level, player, hand, stack, tankCapability, filter, tankIsCreative, onChanged)).isEmpty()) {
                exchange = FluidExchange.ITEM_TO_TANK;
            } else {
                exchange = null;
            }
        } else {
            if (!(fluidStack = HatchStyleItemTransfer.tryEmptyItem(level, player, hand, stack, tankCapability, filter, tankIsCreative, onChanged)).isEmpty()) {
                exchange = FluidExchange.ITEM_TO_TANK;
            } else if (!(fluidStack = HatchStyleItemTransfer.tryFillItem(level, player, hand, stack, tankCapability, filter, tankIsCreative, onChanged)).isEmpty()) {
                exchange = FluidExchange.TANK_TO_ITEM;
            } else {
                exchange = null;
            }
        }
        if (exchange == null) {
            if (HatchStyleItemTransfer.canItemBeEmptied(level, stack) || HatchStyleItemTransfer.canItemBeFilled(level, stack))
                return ItemInteractionResult.SUCCESS;
            return ItemInteractionResult.FAIL;
        }

        SoundEvent soundevent = switch (exchange) {
            case ITEM_TO_TANK -> FluidHelper.getEmptySound(fluidStack);
            case TANK_TO_ITEM -> FluidHelper.getFillSound(fluidStack);
        };
        if (soundevent != null) {
            float pitch = Mth.clamp(1 - (fluidStack.getAmount() / (FluidTankBlockEntity.getCapacityMultiplier() * 16f)), 0, 1);
            pitch /= 1.5f;
            pitch += .5f;
            pitch += (level.random.nextFloat() - .5f) / 4f;
            level.playSound(null, pos, soundevent, SoundSource.BLOCKS, .5f, pitch);
        }

        pulseOpen(level, pos);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.ITEM_HATCH.get(state.getValue(FACING).getOpposite());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<FluidHatchBlockEntity> getBlockEntityClass() {
        return FluidHatchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidHatchBlockEntity> getBlockEntityType() {
        return AllBlockEntities.FLUID_HATCH.get();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    protected MapCodec<? extends FluidHatchBlock> codec() {
        return CODEC;
    }
}
