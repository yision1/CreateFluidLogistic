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
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.fluid.FluidHelper.FluidExchange;
import net.createmod.catnip.data.Pair;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.content.fluids.fluidHatch.FluidHatchItemFluidTransfer.TransferResult;
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
        if (!FeatureToggle.isEnabled(FeatureToggle.FLUID_HATCH))
            return null;

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
        if (!FeatureToggle.isEnabled(FeatureToggle.FLUID_HATCH))
            return ItemInteractionResult.FAIL;

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

        FluidExchange exchange;
        FluidStack fluidStack;
        if (player.isSecondaryUseActive()) {
            if (!(fluidStack = tryFillItem(level, player, hand, stack, blockEntity, tankCapability, filter)).isEmpty()) {
                exchange = FluidExchange.TANK_TO_ITEM;
            } else if (!(fluidStack = tryEmptyItem(level, player, hand, stack, blockEntity, tankCapability, filter)).isEmpty()) {
                exchange = FluidExchange.ITEM_TO_TANK;
            } else {
                exchange = null;
            }
        } else {
            if (!(fluidStack = tryEmptyItem(level, player, hand, stack, blockEntity, tankCapability, filter)).isEmpty()) {
                exchange = FluidExchange.ITEM_TO_TANK;
            } else if (!(fluidStack = tryFillItem(level, player, hand, stack, blockEntity, tankCapability, filter)).isEmpty()) {
                exchange = FluidExchange.TANK_TO_ITEM;
            } else {
                exchange = null;
            }
        }
        if (exchange == null) {
            if (canItemBeEmptied(level, stack) || canItemBeFilled(level, stack))
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

    public FluidStack tryEmptyItem(
            Level level, Player player, InteractionHand hand, ItemStack stack,
            BlockEntity blockEntity, IFluidHandler capability, FilteringBehaviour filter) {
        ItemStack transferredStack = stack.copy();
        TransferResult transfer = FluidHatchItemFluidTransfer.tryDrainItemToTank(transferredStack, capability, filter);
        if (!transfer.isEmpty()) {
            blockEntity.setChanged();
            if (level instanceof ServerLevel serverLevel)
                serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());

            if (!player.isCreative() && !(blockEntity instanceof CreativeFluidTankBlockEntity))
                replaceItem(player, hand, transferredStack, transfer.result());
            return transfer.fluidStack();
        }

        if (!GenericItemEmptying.canItemBeEmptied(level, stack))
            return FluidStack.EMPTY;

        Pair<FluidStack, ItemStack> emptying = GenericItemEmptying.emptyItem(level, stack, true);
        FluidStack fluidStack = emptying.getFirst();

        if (!filter.test(fluidStack))
            return FluidStack.EMPTY;

        if (fluidStack.getAmount() != capability.fill(fluidStack, FluidAction.SIMULATE))
            return FluidStack.EMPTY;
        if (level.isClientSide)
            return fluidStack;

        ItemStack copy = stack.copy();
        emptying = GenericItemEmptying.emptyItem(level, copy, false);

        // Prevent special cap behavior interrupting insert fluid.
        int realFill = capability.fill(fluidStack.copy(), FluidAction.SIMULATE);
        if (realFill == 0) return fluidStack;
        capability.fill(fluidStack.copy(), FluidAction.EXECUTE);
        blockEntity.setChanged();

        if (level instanceof ServerLevel serverLevel)
            serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());

        if (!player.isCreative() && !(blockEntity instanceof CreativeFluidTankBlockEntity)) {
            replaceItem(player, hand, copy, emptying.getSecond());
        }
        return fluidStack;
    }

    public FluidStack tryFillItem(Level level, Player player, InteractionHand hand, ItemStack stack, BlockEntity blockEntity, IFluidHandler capability, FilteringBehaviour filter) {
        FluidStack fluidStack = tryFillItemWithExtraHandler(level, player, hand, stack, blockEntity, capability, filter);
        if (!fluidStack.isEmpty())
            return fluidStack;

        fluidStack = tryFillItemWithFillingRecipe(level, player, hand, stack, blockEntity, capability, filter);
        if (!fluidStack.isEmpty())
            return fluidStack;

        ItemStack transferredStack = stack.copy();
        TransferResult transfer = FluidHatchItemFluidTransfer.tryFillItemFromTank(transferredStack, capability, filter);
        if (!transfer.isEmpty()) {
            blockEntity.setChanged();
            if (level instanceof ServerLevel serverLevel)
                serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());

            if (!player.isCreative())
                replaceItem(player, hand, transferredStack, transfer.result());
            return transfer.fluidStack();
        }

        if (!GenericItemFilling.canItemBeFilled(level, stack))
            return FluidStack.EMPTY;

        for (int i = 0; i < capability.getTanks(); i++) {
            fluidStack = capability.getFluidInTank(i);
            if (fluidStack.isEmpty() || !filter.test(fluidStack))
                continue;
            int requiredAmountForItem = FluidHatchItemFilling.getRequiredAmountForItem(level, stack, fluidStack.copy());
            if (requiredAmountForItem == -1)
                continue;
            if (requiredAmountForItem > fluidStack.getAmount())
                continue;

            FluidStack fluidCopy = fluidStack.copy();
            fluidCopy.setAmount(requiredAmountForItem);

            FluidStack realDraw = capability.drain(fluidCopy, FluidAction.SIMULATE);
            if (realDraw.isEmpty() || realDraw.getAmount() != requiredAmountForItem)
                continue;

            if (level.isClientSide)
                return fluidCopy;

            ItemStack workingStack = player.isCreative() || blockEntity instanceof CreativeFluidTankBlockEntity
                    ? stack.copy()
                    : stack;
            ItemStack result = FluidHatchItemFilling.fillItem(level, requiredAmountForItem, workingStack, fluidStack.copy());
            if (result.isEmpty())
                continue;
            capability.drain(fluidCopy, FluidAction.EXECUTE);

            if (!player.isCreative())
                replaceItem(player, hand, workingStack, result);
            blockEntity.setChanged();
            if (level instanceof ServerLevel serverLevel)
                serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());
            return fluidCopy;
        }
        return FluidStack.EMPTY;
    }

    private FluidStack tryFillItemWithFillingRecipe(
            Level level, Player player, InteractionHand hand, ItemStack stack,
            BlockEntity blockEntity, IFluidHandler capability, FilteringBehaviour filter) {
        for (int i = 0; i < capability.getTanks(); i++) {
            FluidStack fluidStack = capability.getFluidInTank(i);
            if (fluidStack.isEmpty() || !filter.test(fluidStack))
                continue;

            var requiredAmount = FluidHatchFillingRecipeTransfer.getRequiredAmountForItem(level, stack, fluidStack.copy());
            if (requiredAmount.isEmpty())
                continue;
            int requiredAmountForItem = requiredAmount.getAsInt();
            if (requiredAmountForItem > fluidStack.getAmount())
                continue;

            FluidStack fluidCopy = fluidStack.copy();
            fluidCopy.setAmount(requiredAmountForItem);

            FluidStack realDraw = capability.drain(fluidCopy, FluidAction.SIMULATE);
            if (realDraw.isEmpty() || realDraw.getAmount() != requiredAmountForItem)
                continue;

            if (level.isClientSide)
                return fluidCopy;

            ItemStack workingStack = player.isCreative() || blockEntity instanceof CreativeFluidTankBlockEntity
                    ? stack.copy()
                    : stack;
            var result = FluidHatchFillingRecipeTransfer.fillItem(level, requiredAmountForItem, workingStack, fluidStack.copy());
            if (result.isEmpty())
                continue;

            capability.drain(fluidCopy, FluidAction.EXECUTE);

            if (!player.isCreative())
                replaceItem(player, hand, workingStack, result.get());
            blockEntity.setChanged();
            if (level instanceof ServerLevel serverLevel)
                serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());
            return fluidCopy;
        }
        return FluidStack.EMPTY;
    }

    private FluidStack tryFillItemWithExtraHandler(
            Level level, Player player, InteractionHand hand, ItemStack stack,
            BlockEntity blockEntity, IFluidHandler capability, FilteringBehaviour filter) {
        for (int i = 0; i < capability.getTanks(); i++) {
            FluidStack fluidStack = capability.getFluidInTank(i);
            if (fluidStack.isEmpty() || !filter.test(fluidStack))
                continue;

            var requiredAmount = FluidHatchItemFilling.getRequiredAmountForExtraHandler(stack, fluidStack.copy());
            if (requiredAmount.isEmpty())
                continue;
            int requiredAmountForItem = requiredAmount.getAsInt();
            if (requiredAmountForItem > fluidStack.getAmount())
                continue;

            FluidStack fluidCopy = fluidStack.copy();
            fluidCopy.setAmount(requiredAmountForItem);

            FluidStack realDraw = capability.drain(fluidCopy, FluidAction.SIMULATE);
            if (realDraw.isEmpty() || realDraw.getAmount() != requiredAmountForItem)
                continue;

            if (level.isClientSide)
                return fluidCopy;

            ItemStack workingStack = player.isCreative() || blockEntity instanceof CreativeFluidTankBlockEntity
                    ? stack.copy()
                    : stack;
            var result = FluidHatchItemFilling.fillItemWithExtraHandler(requiredAmountForItem, workingStack, fluidStack.copy());
            if (result.isEmpty())
                continue;
            capability.drain(fluidCopy, FluidAction.EXECUTE);

            if (!player.isCreative())
                replaceItem(player, hand, workingStack, result.get());
            blockEntity.setChanged();
            if (level instanceof ServerLevel serverLevel)
                serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());
            return fluidCopy;
        }
        return FluidStack.EMPTY;
    }

    private static void replaceItem(Player player, InteractionHand hand, ItemStack stack, ItemStack result) {
        if (stack.isEmpty()) {
            player.setItemInHand(hand, result);
        } else {
            player.setItemInHand(hand, stack);
            player.getInventory().placeItemBackInInventory(result);
        }
    }

    private static boolean canItemBeEmptied(Level level, ItemStack stack) {
        return GenericItemEmptying.canItemBeEmptied(level, stack)
                || FluidHatchItemFluidTransfer.canItemBeEmptied(stack);
    }

    private static boolean canItemBeFilled(Level level, ItemStack stack) {
        return FluidHatchFillingRecipeTransfer.canItemBeFilled(level, stack)
                || FluidHatchItemFluidTransfer.canItemBeFilled(stack)
                || GenericItemFilling.canItemBeFilled(level, stack);
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
