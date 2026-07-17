package com.yision.fluidlogistics.content.fluids.faucet;

import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.PASS;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.yision.fluidlogistics.compat.sable.SableSublevelTargetHelper;
import com.yision.fluidlogistics.content.fluids.infiniteWater.InfiniteWaterSource;
import com.yision.fluidlogistics.foundation.fluid.DepotFills;
import com.yision.fluidlogistics.foundation.fluid.FluidSourceScans;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

class FaucetItemFilling {

    private static final int FILLING_TIME = 20;
    private static final int BELT_COMPLETION_TICK = 5;
    private static final int BELT_RETRY_COOLDOWN = 4;
    private static final String TAG_PENDING_FLUID = "PendingFluid";
    private static final String TAG_PROCESSING_ITEM = "ProcessingItem";
    private static final String TAG_SOURCE_DIRECTION = "SourceDirection";
    private static final String TAG_SOURCE_POS = "SourcePos";
    private static final String TAG_IS_FILLING_ITEM = "IsFillingItem";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_PROCESSING_TARGET = "ProcessingTarget";

    private final FaucetBlockEntity be;
    private FluidStack pendingFluid = FluidStack.EMPTY;
    private ItemStack processingItem = ItemStack.EMPTY;
    private int processingTicks;
    private int beltRetryCooldown;
    private boolean isFillingItem;
    private @Nullable Direction sourceDirection;
    private @Nullable BlockPos sourceBlockPos;
    private ProcessingTarget processingTarget = ProcessingTarget.NONE;

    FaucetItemFilling(FaucetBlockEntity be) {
        this.be = be;
    }

    boolean isFilling() {
        return isFillingItem;
    }

    boolean isProcessing() {
        return isFillingItem && processingTicks > 0;
    }

    boolean isProcessingOnBelt() {
        return isFillingItem && processingTarget == ProcessingTarget.BELT;
    }

    int getProcessingTicks() {
        return processingTicks;
    }

    boolean hasPending() {
        return !pendingFluid.isEmpty();
    }

    void resetRetryCooldown() {
        beltRetryCooldown = 0;
    }

    boolean startDepotFilling(IFluidHandler sourceHandler, ItemStack item, Direction sourceDir, BlockPos sourcePos,
        FluidStack availableFluid) {
        return beginItemFilling(sourceHandler, item, sourceDir, sourcePos, availableFluid, ProcessingTarget.DEPOT);
    }

    private boolean startBeltFilling(ItemFillContext fillContext, ItemStack item) {
        return beginItemFilling(fillContext.sourceHandler(), item, fillContext.sourceDirection(), fillContext.sourcePos(),
            fillContext.availableFluid(), ProcessingTarget.BELT);
    }

    private boolean beginItemFilling(IFluidHandler sourceHandler, ItemStack item, Direction sourceDir, BlockPos sourcePos,
        FluidStack availableFluid, ProcessingTarget target) {
        int requiredAmount = FaucetFilling.getRequiredAmountForItem(be.getLevel(), item, availableFluid.copy());
        if (requiredAmount <= 0 || requiredAmount > availableFluid.getAmount()) {
            return false;
        }

        FluidStack simulatedDrain = sourceHandler.drain(availableFluid.copyWithAmount(requiredAmount), FluidAction.SIMULATE);
        if (simulatedDrain.isEmpty() || simulatedDrain.getAmount() < requiredAmount) {
            return false;
        }

        isFillingItem = true;
        processingTicks = FILLING_TIME;
        processingItem = item.copyWithCount(1);
        pendingFluid = simulatedDrain.copy();
        be.setRenderingFluid(simulatedDrain.copy());
        sourceDirection = sourceDir;
        sourceBlockPos = sourcePos.immutable();
        processingTarget = target;
        AllSoundEvents.SPOUTING.playOnServer(be.getLevel(), be.getBlockPos(), 0.75f,
            0.9f + 0.2f * be.getLevel().random.nextFloat());
        be.notifyUpdate();
        return true;
    }

    private @Nullable ItemFillContext getItemFillContext(ItemStack item) {
        Direction facing = be.sourceFacing();
        BlockPos sourcePos = be.sourcePos();
        IFluidHandler sourceHandler = be.sourceHandler(sourcePos, facing);
        if (sourceHandler == null) {
            return null;
        }

        FluidStack fillableFluid = FluidSourceScans.findForItem(be.getLevel(), sourceHandler,
            be::testFluidFilter, item, true);
        if (fillableFluid.isEmpty()) {
            return null;
        }

        return new ItemFillContext(sourceHandler, facing, sourcePos, fillableFluid);
    }

    BeltProcessingBehaviour.ProcessingResult onBeltItemReceived(TransportedItemStack transported,
        TransportedItemStackHandlerBehaviour handler) {
        if (!be.isSmart()) {
            return PASS;
        }
        if (handler.blockEntity.isVirtual()) {
            return PASS;
        }
        if (!isDirectlyAbove(handler)) {
            return PASS;
        }
        if (!be.getBlockState().getValue(FaucetBlock.OPEN)) {
            return PASS;
        }
        if (!FaucetFilling.canItemBeFilled(be.getLevel(), transported.stack)) {
            return PASS;
        }
        if (isFillingItem && processingTarget != ProcessingTarget.BELT) {
            return HOLD;
        }
        if (getItemFillContext(transported.stack) != null) {
            return HOLD;
        }

        return hasSourcePotentialFor(transported.stack) ? HOLD : PASS;
    }

    BeltProcessingBehaviour.ProcessingResult whenBeltItemHeld(TransportedItemStack transported,
        TransportedItemStackHandlerBehaviour handler) {
        if (!be.isSmart()) {
            return PASS;
        }
        if (!isDirectlyAbove(handler)) {
            if (processingTarget == ProcessingTarget.BELT) {
                cancel();
            }
            return PASS;
        }

        if (processingTarget == ProcessingTarget.DEPOT) {
            return HOLD;
        }

        if (processingTarget == ProcessingTarget.BELT) {
            if (processingItem.isEmpty() || transported.stack.getCount() < 1
                || !ItemStack.isSameItemSameComponents(transported.stack.copyWithCount(1), processingItem)) {
                cancel();
                return PASS;
            }
            if (processingTicks != BELT_COMPLETION_TICK) {
                return HOLD;
            }
            return finishBeltItemFilling(transported, handler);
        }

        if (!be.getBlockState().getValue(FaucetBlock.OPEN)) {
            return PASS;
        }

        if (beltRetryCooldown > 0) {
            beltRetryCooldown--;
            return HOLD;
        }

        if (!FaucetFilling.canItemBeFilled(be.getLevel(), transported.stack)) {
            return PASS;
        }

        ItemFillContext fillContext = getItemFillContext(transported.stack);
        if (fillContext == null) {
            if (hasSourcePotentialFor(transported.stack)) {
                beltRetryCooldown = BELT_RETRY_COOLDOWN;
                return HOLD;
            }
            return PASS;
        }

        beltRetryCooldown = 0;
        return startBeltFilling(fillContext, transported.stack) ? HOLD : PASS;
    }

    private boolean hasSourcePotentialFor(ItemStack item) {
        IFluidHandler sourceHandler = be.sourceHandler(be.sourcePos(), be.sourceFacing());
        return sourceHandler != null && FluidSourceScans.hasPotentialForItem(be.getLevel(), sourceHandler,
            be::testFluidFilter, item, true);
    }

    private boolean isDirectlyAbove(TransportedItemStackHandlerBehaviour handler) {
        if (handler.blockEntity == null) {
            return false;
        }
        return SableSublevelTargetHelper.isSameBlockAcrossSublevels(be.getLevel(), be.getBlockPos().below(),
            handler.blockEntity.getBlockPos());
    }

    private boolean validateItemStillPresent() {
        if (processingItem.isEmpty()) {
            return false;
        }

        var resolved = SableSublevelTargetHelper.resolveBlockEntity(be.getLevel(), be.getBlockPos().below());
        BlockEntity targetEntity = resolved.blockEntity();
        if (!DepotFills.isDepot(targetEntity)) {
            return false;
        }

        ItemStack currentItem = DepotFills.getItemOnDepot(targetEntity);
        return ItemStack.isSameItemSameComponents(currentItem, processingItem) && currentItem.getCount() >= 1;
    }

    private void finishDepotItemFilling() {
        if (!isFillingItem || processingTarget != ProcessingTarget.DEPOT || processingItem.isEmpty() || pendingFluid.isEmpty()) {
            return;
        }

        var resolved = SableSublevelTargetHelper.resolveBlockEntity(be.getLevel(), be.getBlockPos().below());
        BlockPos targetPos = resolved.resolvedPos();
        BlockEntity targetEntity = resolved.blockEntity();
        if (!DepotFills.isDepot(targetEntity)) {
            cancel();
            return;
        }

        ItemStack currentItem = DepotFills.getItemOnDepot(targetEntity);
        if (!ItemStack.isSameItemSameComponents(currentItem, processingItem) || currentItem.getCount() < 1) {
            cancel();
            return;
        }

        if (!consumePendingFluid()) {
            cancel();
            return;
        }

        TransportedItemStackHandlerBehaviour transportedHandler = DepotFills.getTransportedHandler(be.getLevel(), targetEntity);
        if (transportedHandler == null) {
            cancel();
            return;
        }

        boolean completed = DepotFills.fillFirstMatchingItem(transportedHandler,
            transported -> ItemStack.isSameItemSameComponents(transported.stack, processingItem)
                && transported.stack.getCount() >= 1,
            stack -> FaucetFilling.fillItem(be.getLevel(), pendingFluid.getAmount(), stack, pendingFluid.copy()));
        if (!completed) {
            cancel();
            return;
        }

        targetEntity.setChanged();
        DepotFills.notifyTargetUpdate(be.getLevel(), targetEntity);
        be.getLevel().playSound(null, targetPos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f + be.getLevel().random.nextFloat() * 0.2f);

        clearState();
        be.setTransferCooldown(FaucetBlockEntity.TRANSFER_INTERVAL);
        be.notifyUpdate();
    }

    private BeltProcessingBehaviour.ProcessingResult finishBeltItemFilling(TransportedItemStack transported,
        TransportedItemStackHandlerBehaviour handler) {
        if (!isFillingItem || processingTarget != ProcessingTarget.BELT || pendingFluid.isEmpty()) {
            return PASS;
        }
        if (!consumePendingFluid()) {
            cancel();
            return PASS;
        }

        ItemStack resultStack = FaucetFilling.fillItem(be.getLevel(), pendingFluid.getAmount(), transported.stack, pendingFluid.copy());
        if (resultStack.isEmpty()) {
            cancel();
            return PASS;
        }

        DepotFills.completeItemFill(handler, transported, resultStack);

        be.getLevel().playSound(null, be.getBlockPos().below(2), net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f + be.getLevel().random.nextFloat() * 0.2f);

        clearState();
        be.setTransferCooldown(FaucetBlockEntity.SUCCESS_COOLDOWN);
        be.notifyUpdate();
        return HOLD;
    }

    private boolean consumePendingFluid() {
        if (sourceBlockPos == null || sourceDirection == null) {
            return false;
        }

        if (InfiniteWaterSource.isActiveSourceFor(InfiniteWaterSource.Consumer.FAUCET,
                be.getLevel().getBlockState(sourceBlockPos))
            && pendingFluid.getFluid() == Fluids.WATER) {
            return true;
        }

        IFluidHandler sourceHandler = be.rawSourceCapability(sourceBlockPos, sourceDirection);
        if (sourceHandler == null) {
            return false;
        }

        FluidStack drained = sourceHandler.drain(pendingFluid.copy(), FluidAction.EXECUTE);
        return !drained.isEmpty() && drained.getAmount() >= pendingFluid.getAmount();
    }

    void cancel() {
        clearState();
        be.notifyUpdate();
    }

    void clearState() {
        isFillingItem = false;
        processingTicks = 0;
        processingItem = ItemStack.EMPTY;
        pendingFluid = FluidStack.EMPTY;
        be.setRenderingFluid(FluidStack.EMPTY);
        sourceDirection = null;
        sourceBlockPos = null;
        processingTarget = ProcessingTarget.NONE;
        beltRetryCooldown = 0;
    }

    boolean tickActiveFill() {
        if (!isFillingItem) {
            return false;
        }
        if (processingTicks <= 0) {
            cancel();
            return true;
        }

        processingTicks--;
        return switch (processingTarget) {
            case DEPOT -> tickFillCountdown(this::validateItemStillPresent, this::finishDepotItemFilling);
            case BELT -> tickFillCountdown(this::validateBeltItemStillPresent, this::cancel);
            case NONE -> {
                cancel();
                yield true;
            }
        };
    }

    private boolean tickFillCountdown(BooleanSupplier stillValid, Runnable onFinish) {
        if (!stillValid.getAsBoolean()) {
            cancel();
            return true;
        }
        if (processingTicks == 0) {
            onFinish.run();
        }
        return true;
    }

    private boolean validateBeltItemStillPresent() {
        if (processingItem.isEmpty()) {
            return false;
        }

        ItemStack currentItem = getCurrentStackInBeltSegment(be.getBlockPos().below());
        return !currentItem.isEmpty()
            && currentItem.getCount() >= 1
            && ItemStack.isSameItemSameComponents(currentItem.copyWithCount(1), processingItem);
    }

    private ItemStack getCurrentStackInBeltSegment(BlockPos beltPos) {
        var resolved = SableSublevelTargetHelper.resolveBlockEntity(be.getLevel(), beltPos);
        BlockEntity blockEntity = resolved.blockEntity();
        if (blockEntity == null) {
            return ItemStack.EMPTY;
        }
        BlockPos resolvedPos = resolved.resolvedPos();
        var state = be.getLevel().getBlockState(resolvedPos);
        var handler = be.getLevel().getCapability(
            Capabilities.ItemHandler.BLOCK,
            resolvedPos,
            state,
            blockEntity,
            null
        );
        if (handler == null || handler.getSlots() <= 0) {
            return ItemStack.EMPTY;
        }

        return handler.getStackInSlot(0);
    }

    void write(CompoundTag tag, HolderLookup.Provider registries) {
        FaucetFilling.writeFluid(tag, registries, TAG_PENDING_FLUID, pendingFluid);
        FaucetFilling.writeItem(tag, registries, TAG_PROCESSING_ITEM, processingItem);
        FaucetFilling.writeDirection(tag, TAG_SOURCE_DIRECTION, sourceDirection);
        FaucetFilling.writeBlockPos(tag, TAG_SOURCE_POS, sourceBlockPos);
        tag.putBoolean(TAG_IS_FILLING_ITEM, isFillingItem);
        tag.putInt(TAG_PROCESSING_TICKS, processingTicks);
        tag.putInt(TAG_PROCESSING_TARGET, processingTarget.ordinal());
    }

    void read(CompoundTag tag, HolderLookup.Provider registries) {
        pendingFluid = FaucetFilling.readFluid(tag, registries, TAG_PENDING_FLUID);
        processingItem = FaucetFilling.readItem(tag, registries, TAG_PROCESSING_ITEM);
        sourceDirection = FaucetFilling.readDirection(tag, TAG_SOURCE_DIRECTION);
        sourceBlockPos = FaucetFilling.readBlockPos(tag, TAG_SOURCE_POS);
        isFillingItem = tag.getBoolean(TAG_IS_FILLING_ITEM);
        processingTicks = tag.getInt(TAG_PROCESSING_TICKS);
        processingTarget = ProcessingTarget.fromOrdinal(tag.getInt(TAG_PROCESSING_TARGET));

        if (!be.isSmart() && processingTarget == ProcessingTarget.BELT) {
            clearState();
        }
    }

    private record ItemFillContext(IFluidHandler sourceHandler, Direction sourceDirection, BlockPos sourcePos,
        FluidStack availableFluid) {
    }

    private enum ProcessingTarget {
        NONE,
        DEPOT,
        BELT;

        private static ProcessingTarget fromOrdinal(int ordinal) {
            ProcessingTarget[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                return NONE;
            }
            return values[ordinal];
        }
    }
}
