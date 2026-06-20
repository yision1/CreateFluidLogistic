package com.yision.fluidlogistics.block.Faucet;

import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult.PASS;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.SmartFaucet.SmartFaucetFilterSlotPositioning;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.network.SmartFaucetDripParticlePacket;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;

public abstract class AbstractFaucetBlockEntity extends SmartBlockEntity {

    private static final int FILLING_TIME = 20;
    private static final int TRANSFER_RATE = 250;
    private static final int TRANSFER_INTERVAL = 10;
    private static final int IDLE_RECHECK_INTERVAL = 20;
    private static final int DRIP_CACHE_REFRESH_INTERVAL = 120;
    private static final int DRIP_INTERVAL = 25;
    private static final int DRIP_AMOUNT = 250;
    private static final int SUCCESS_COOLDOWN = 5;
    private static final TagKey<Block> FAUCET_FILLABLE = TagKey.create(Registries.BLOCK,
        FluidLogistics.asResource("faucet_fillable"));
    private static final TagKey<Block> LEGACY_SMART_FAUCET_FILLABLE = TagKey.create(Registries.BLOCK,
        FluidLogistics.asResource("smart_faucet_fillable"));
    private static final String TAG_RENDERING_FLUID = "RenderingFluid";
    private static final String TAG_PENDING_FLUID = "PendingFluid";
    private static final String TAG_DRIP_FLUID = "DripFluid";
    private static final String TAG_PROCESSING_ITEM = "ProcessingItem";
    private static final String TAG_SOURCE_DIRECTION = "SourceDirection";
    private static final String TAG_SOURCE_POS = "SourcePos";
    private static final String TAG_IS_FILLING_ITEM = "IsFillingItem";
    private static final String TAG_SHOULD_DRIP = "ShouldDrip";
    private static final String TAG_PROCESSING_TICKS = "ProcessingTicks";
    private static final String TAG_PROCESSING_TARGET = "ProcessingTarget";
    private static final String TAG_TRANSFER_COOLDOWN = "TransferCooldown";
    private static final String TAG_DRIP_TICK_COUNTER = "DripTickCounter";
    private static final String TAG_DRIP_CYCLE_INDEX = "DripCycleIndex";

    private BeltProcessingBehaviour beltProcessing;
    private FilteringBehaviour filtering;
    private FluidStack renderingFluid = FluidStack.EMPTY;
    private FluidStack pendingFluid = FluidStack.EMPTY;
    private FluidStack dripFluid = FluidStack.EMPTY;
    private final List<FluidStack> cachedDripFluids = new ArrayList<>();
    private ItemStack processingItem = ItemStack.EMPTY;
    private int processingTicks;
    private int transferCooldown;
    private int dripCacheRefreshCooldown;
    private int dripTickCounter;
    private int dripCycleIndex;
    private boolean isFillingItem;
    private boolean shouldDrip;
    private @Nullable Direction sourceDirection;
    private @Nullable BlockPos sourceBlockPos;
    private ProcessingTarget processingTarget = ProcessingTarget.NONE;

    public AbstractFaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected boolean supportsFluidFilter() {
        return false;
    }

    protected boolean supportsBeltFilling() {
        return false;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().expandTowards(0, -2, 0);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        if (supportsFluidFilter()) {
            behaviours.add(filtering = new FilteringBehaviour(this, new SmartFaucetFilterSlotPositioning()).forFluids()
                .withCallback($ -> notifyUpdate()));
        }

        if (supportsBeltFilling()) {
            behaviours.add(beltProcessing = new BeltProcessingBehaviour(this).whenItemEnters(this::onBeltItemReceived)
                .whileItemHeld(this::whenBeltItemHeld));
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        if (!isOpen()) {
            resetVisualState();
            return;
        }

        tickCooldown();
        if (tickActiveFill()) {
            return;
        }

        tickDripCacheRefresh();

        if (transferCooldown == 0) {
            tryTransferFluid();
        }

        tickDripEffect();
    }

    public FluidStack getRenderingFluid() {
        return renderingFluid;
    }

    public int getProcessingTicks() {
        return processingTicks;
    }

    public boolean isProcessing() {
        return isFillingItem && processingTicks > 0;
    }

    public boolean isProcessingOnBelt() {
        return isFillingItem && processingTarget == ProcessingTarget.BELT;
    }

    public boolean hasFluidToRender() {
        return !renderingFluid.isEmpty();
    }

    public boolean shouldRenderSourceInterface() {
        if (level == null) {
            return false;
        }

        Direction sourceSide = getBlockState().getValue(AbstractFaucetBlock.FACING).getOpposite();
        BlockPos sourcePos = worldPosition.relative(sourceSide);
        BlockState sourceState = level.getBlockState(sourcePos);
        if (AbstractFaucetBlock.isInfiniteWaterSource(sourceState)) {
            return false;
        }

        return FaucetFluidSupport.hasFluidCapability(level, sourcePos, sourceSide.getOpposite())
            || FaucetFluidSupport.hasFluidCapability(level, sourcePos, null);
    }

    @Nullable
    public IFluidHandler getFluidDisplayCapability() {
        if (level == null) {
            return null;
        }

        Direction facing = getBlockState().getValue(AbstractFaucetBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(facing.getOpposite());
        if (AbstractFaucetBlock.isInfiniteWaterSource(level.getBlockState(sourcePos))) {
            return null;
        }

        IFluidHandler source = FaucetFluidSupport.getSourceHandler(level, sourcePos, facing);
        if (source == null) {
            return null;
        }

        FaucetFluidDisplayHandler display = new FaucetFluidDisplayHandler(source, this::testFluidFilter);
        return display.getTanks() == 0 ? null : display;
    }

    public void onTargetChanged() {
        if (isFillingItem) {
            cancelItemFilling();
        }
        transferCooldown = 0;
        clearDripState();
        notifyUpdate();
    }

    private boolean testFluidFilter(FluidStack fluid) {
        return filtering == null || filtering.test(fluid);
    }

    private void tryTransferFluid() {
        BlockPos targetPos = worldPosition.below();
        Direction facing = getBlockState().getValue(AbstractFaucetBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(facing.getOpposite());
        IFluidHandler sourceHandler = FaucetFluidSupport.getSourceHandler(level, sourcePos, facing);

        if (!hasProcessableTarget(targetPos)) {
            transferCooldown = IDLE_RECHECK_INTERVAL;
            if (sourceHandler == null) {
                clearFlowVisuals();
                return;
            }

            primeDripCache(sourceHandler);
            return;
        }

        if (sourceHandler == null) {
            transferCooldown = IDLE_RECHECK_INTERVAL;
            clearFlowVisuals();
            return;
        }

        boolean success = tryProcess(sourceHandler, targetPos, facing, sourcePos);
        if (success) {
            transferCooldown = SUCCESS_COOLDOWN;
            if (shouldDrip) {
                clearDripState();
                notifyUpdate();
            }
            return;
        }

        transferCooldown = TRANSFER_INTERVAL;
        updateDripPreview(sourceHandler);
    }

    private boolean hasProcessableTarget(BlockPos targetPos) {
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        BlockState targetState = level.getBlockState(targetPos);

        if (targetEntity != null && FaucetTargetSupport.isDepot(targetEntity)) {
            ItemStack itemOnDepot = FaucetTargetSupport.getItemOnDepot(targetEntity);
            return !itemOnDepot.isEmpty() && FaucetFilling.canItemBeFilled(level, itemOnDepot);
        }

        if (targetState.is(net.minecraft.world.level.block.Blocks.CAULDRON)
            || targetState.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) {
            return true;
        }

        return targetEntity != null && isFaucetFillable(targetState);
    }

    private boolean tryProcess(IFluidHandler sourceHandler, BlockPos targetPos, Direction sourceDir, BlockPos sourcePos) {
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        BlockState targetState = level.getBlockState(targetPos);

        if (targetEntity != null && FaucetTargetSupport.isDepot(targetEntity)) {
            ItemStack itemOnDepot = FaucetTargetSupport.getItemOnDepot(targetEntity);
            if (!itemOnDepot.isEmpty() && FaucetFilling.canItemBeFilled(level, itemOnDepot)) {
                FluidStack fillableFluid = FaucetFluidSupport.findFillableFluidForItem(
                    level, sourceHandler, this::testFluidFilter, itemOnDepot);
                if (!fillableFluid.isEmpty()) {
                    return startItemFilling(sourceHandler, itemOnDepot, sourceDir, sourcePos, fillableFluid);
                }
            }
            return false;
        }

        if (targetState.is(net.minecraft.world.level.block.Blocks.CAULDRON)
            || targetState.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) {
            FluidStack fillableFluid = FaucetFluidSupport.findFillableFluidForCauldron(
                sourceHandler, this::testFluidFilter, targetState);
            if (fillableFluid.isEmpty()) {
                return false;
            }

            FluidStack transferred = FaucetTargetSupport.fillCauldron(level, sourceHandler, targetPos, targetState,
                fillableFluid);
            if (transferred.isEmpty()) {
                return false;
            }

            renderingFluid = transferred.copy();
            notifyUpdate();
            return true;
        }

        if (targetEntity == null || !isFaucetFillable(targetState)) {
            return false;
        }

        IFluidHandler targetHandler = FaucetTargetSupport.getTargetHandler(level, targetEntity);
        if (targetHandler == null) {
            return false;
        }

        FluidStack transferred = FaucetTargetSupport.fillContainer(
            level, worldPosition, sourceHandler, targetHandler, this::testFluidFilter, TRANSFER_RATE);
        if (transferred.isEmpty()) {
            return false;
        }

        renderingFluid = transferred.copy();
        notifyUpdate();
        return true;
    }

    private boolean startItemFilling(IFluidHandler sourceHandler, ItemStack item, Direction sourceDir, BlockPos sourcePos,
        FluidStack availableFluid) {
        return beginItemFilling(sourceHandler, item, sourceDir, sourcePos, availableFluid, ProcessingTarget.DEPOT);
    }

    private boolean startBeltFilling(ItemFillContext fillContext, ItemStack item) {
        return beginItemFilling(fillContext.sourceHandler(), item, fillContext.sourceDirection(), fillContext.sourcePos(),
            fillContext.availableFluid(), ProcessingTarget.BELT);
    }

    private boolean beginItemFilling(IFluidHandler sourceHandler, ItemStack item, Direction sourceDir, BlockPos sourcePos,
        FluidStack availableFluid, ProcessingTarget target) {
        int requiredAmount = FaucetFilling.getRequiredAmountForItem(level, item, availableFluid.copy());
        if (requiredAmount <= 0 || requiredAmount > availableFluid.getAmount()) {
            return false;
        }

        FluidStack simulatedDrain = sourceHandler.drain(FaucetFluidSupport.copyFluidWithAmount(availableFluid,
            requiredAmount), FluidAction.SIMULATE);
        if (simulatedDrain.isEmpty() || simulatedDrain.getAmount() < requiredAmount) {
            return false;
        }

        isFillingItem = true;
        processingTicks = FILLING_TIME;
        processingItem = item.copyWithCount(1);
        pendingFluid = simulatedDrain.copy();
        renderingFluid = simulatedDrain.copy();
        sourceDirection = sourceDir;
        sourceBlockPos = sourcePos.immutable();
        processingTarget = target;
        AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.75f, 0.9f + 0.2f * level.random.nextFloat());
        notifyUpdate();
        return true;
    }

    private @Nullable ItemFillContext getItemFillContext(ItemStack item) {
        Direction facing = getBlockState().getValue(AbstractFaucetBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(facing.getOpposite());
        IFluidHandler sourceHandler = FaucetFluidSupport.getSourceHandler(level, sourcePos, facing);
        if (sourceHandler == null) {
            return null;
        }

        FluidStack fillableFluid = FaucetFluidSupport.findFillableFluidForItem(
            level, sourceHandler, this::testFluidFilter, item);
        if (fillableFluid.isEmpty()) {
            return null;
        }

        return new ItemFillContext(sourceHandler, facing, sourcePos, fillableFluid);
    }

    private BeltProcessingBehaviour.ProcessingResult onBeltItemReceived(TransportedItemStack transported,
        TransportedItemStackHandlerBehaviour handler) {
        if (!supportsBeltFilling()) {
            return PASS;
        }
        if (handler.blockEntity.isVirtual()) {
            return PASS;
        }
        if (!isDirectlyAbove(handler)) {
            return PASS;
        }
        if (!getBlockState().getValue(AbstractFaucetBlock.OPEN)) {
            return PASS;
        }
        if (!FaucetFilling.canItemBeFilled(level, transported.stack)) {
            return PASS;
        }
        if (isFillingItem && processingTarget != ProcessingTarget.BELT) {
            return HOLD;
        }
        if (getItemFillContext(transported.stack) != null) {
            return HOLD;
        }

        Direction facing = getBlockState().getValue(AbstractFaucetBlock.FACING);
        IFluidHandler sourceHandler = FaucetFluidSupport.getSourceHandler(level,
            worldPosition.relative(facing.getOpposite()), facing);
        return sourceHandler != null
            && FaucetFluidSupport.hasPotentialFluidForItem(level, sourceHandler, this::testFluidFilter, transported.stack)
            ? HOLD : PASS;
    }

    private BeltProcessingBehaviour.ProcessingResult whenBeltItemHeld(TransportedItemStack transported,
        TransportedItemStackHandlerBehaviour handler) {
        if (!supportsBeltFilling()) {
            return PASS;
        }
        if (!isDirectlyAbove(handler)) {
            if (processingTarget == ProcessingTarget.BELT) {
                cancelItemFilling();
            }
            return PASS;
        }

        if (processingTarget == ProcessingTarget.DEPOT) {
            return HOLD;
        }

        if (processingTarget == ProcessingTarget.BELT) {
            if (processingItem.isEmpty() || transported.stack.getCount() < 1
                || !ItemStack.isSameItemSameTags(transported.stack, processingItem)) {
                cancelItemFilling();
                return PASS;
            }
            if (processingTicks != 5) {
                return HOLD;
            }
            return finishBeltItemFilling(transported, handler);
        }

        if (!getBlockState().getValue(AbstractFaucetBlock.OPEN)) {
            return PASS;
        }
        if (!FaucetFilling.canItemBeFilled(level, transported.stack)) {
            return PASS;
        }

        ItemFillContext fillContext = getItemFillContext(transported.stack);
        if (fillContext == null) {
            Direction facing = getBlockState().getValue(AbstractFaucetBlock.FACING);
            IFluidHandler sourceHandler = FaucetFluidSupport.getSourceHandler(level,
                worldPosition.relative(facing.getOpposite()), facing);
            return sourceHandler != null
                && FaucetFluidSupport.hasPotentialFluidForItem(level, sourceHandler, this::testFluidFilter,
                    transported.stack)
                ? HOLD : PASS;
        }

        return startBeltFilling(fillContext, transported.stack) ? HOLD : PASS;
    }

    private boolean isDirectlyAbove(TransportedItemStackHandlerBehaviour handler) {
        return handler.blockEntity != null && handler.blockEntity.getBlockPos().above().equals(worldPosition);
    }

    private boolean validateItemStillPresent() {
        if (processingItem.isEmpty()) {
            return false;
        }

        BlockEntity targetEntity = level.getBlockEntity(worldPosition.below());
        if (!FaucetTargetSupport.isDepot(targetEntity)) {
            return false;
        }

        ItemStack currentItem = FaucetTargetSupport.getItemOnDepot(targetEntity);
        return ItemStack.isSameItemSameTags(currentItem, processingItem) && currentItem.getCount() >= 1;
    }

    private void finishDepotItemFilling() {
        if (!isFillingItem || processingTarget != ProcessingTarget.DEPOT || processingItem.isEmpty() || pendingFluid.isEmpty()) {
            return;
        }

        BlockPos targetPos = worldPosition.below();
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        if (!FaucetTargetSupport.isDepot(targetEntity)) {
            cancelItemFilling();
            return;
        }

        ItemStack currentItem = FaucetTargetSupport.getItemOnDepot(targetEntity);
        if (!ItemStack.isSameItemSameTags(currentItem, processingItem) || currentItem.getCount() < 1) {
            cancelItemFilling();
            return;
        }

        if (!consumePendingFluid()) {
            cancelItemFilling();
            return;
        }

        DepotBehaviour behaviour = DepotBehaviour.get(targetEntity, DepotBehaviour.TYPE);
        if (behaviour == null) {
            cancelItemFilling();
            return;
        }

        ItemStack result = FaucetFilling.fillItem(level, pendingFluid.getAmount(), currentItem, pendingFluid.copy());
        if (result.isEmpty()) {
            cancelItemFilling();
            return;
        }

        if (currentItem.isEmpty()) {
            behaviour.setHeldItem(new TransportedItemStack(result));
        } else {
            behaviour.setHeldItem(new TransportedItemStack(currentItem.copy()));
            FaucetTargetSupport.storeDepotOutput(level, behaviour, result, targetPos);
        }

        targetEntity.setChanged();
        FaucetTargetSupport.notifyTargetUpdate(level, targetEntity);
        level.playSound(null, targetPos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f + level.random.nextFloat() * 0.2f);

        clearItemFillingState();
        transferCooldown = TRANSFER_INTERVAL;
        notifyUpdate();
    }

    private BeltProcessingBehaviour.ProcessingResult finishBeltItemFilling(TransportedItemStack transported,
        TransportedItemStackHandlerBehaviour handler) {
        if (!isFillingItem || processingTarget != ProcessingTarget.BELT || pendingFluid.isEmpty()) {
            return PASS;
        }
        if (!consumePendingFluid()) {
            cancelItemFilling();
            return PASS;
        }

        ItemStack resultStack = FaucetFilling.fillItem(level, pendingFluid.getAmount(), transported.stack, pendingFluid.copy());
        if (resultStack.isEmpty()) {
            cancelItemFilling();
            return PASS;
        }

        transported.clearFanProcessingData();
        List<TransportedItemStack> outList = new ArrayList<>();
        TransportedItemStack held = null;
        TransportedItemStack result = transported.copy();
        result.stack = resultStack;
        if (!transported.stack.isEmpty()) {
            held = transported.copy();
        }
        outList.add(result);
        handler.handleProcessingOnItem(transported, TransportedResult.convertToAndLeaveHeld(outList, held));

        level.playSound(null, worldPosition.below(2), net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f + level.random.nextFloat() * 0.2f);

        clearItemFillingState();
        transferCooldown = SUCCESS_COOLDOWN;
        notifyUpdate();
        return HOLD;
    }

    private boolean consumePendingFluid() {
        if (sourceBlockPos == null || sourceDirection == null) {
            return false;
        }

        if (AbstractFaucetBlock.isInfiniteWaterSource(level.getBlockState(sourceBlockPos)) && pendingFluid.getFluid() == Fluids.WATER) {
            return true;
        }

        IFluidHandler sourceHandler = FaucetFluidSupport.getBlockFluidHandler(level, sourceBlockPos, sourceDirection);
        if (sourceHandler == null) {
            sourceHandler = FaucetFluidSupport.getBlockFluidHandler(level, sourceBlockPos, null);
        }
        if (sourceHandler == null) {
            return false;
        }

        FluidStack drained = sourceHandler.drain(pendingFluid.copy(), FluidAction.EXECUTE);
        return !drained.isEmpty() && drained.getAmount() >= pendingFluid.getAmount();
    }

    private void cancelItemFilling() {
        clearItemFillingState();
        notifyUpdate();
    }

    private void clearItemFillingState() {
        isFillingItem = false;
        processingTicks = 0;
        processingItem = ItemStack.EMPTY;
        pendingFluid = FluidStack.EMPTY;
        renderingFluid = FluidStack.EMPTY;
        sourceDirection = null;
        sourceBlockPos = null;
        processingTarget = ProcessingTarget.NONE;
    }

    private void spawnDripParticle() {
        if (!(level instanceof ServerLevel serverLevel) || dripFluid.isEmpty()) {
            return;
        }

        FluidLogisticsPackets.sendToNear(serverLevel, worldPosition, 32, new SmartFaucetDripParticlePacket(worldPosition,
            dripFluid.copy()));

        if (level.random.nextFloat() < 0.2f) {
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.2f, 0.8f + level.random.nextFloat() * 0.4f);
        }
    }

    private void resetVisualState() {
        boolean needsUpdate = !renderingFluid.isEmpty() || !pendingFluid.isEmpty() || shouldDrip || isFillingItem;
        renderingFluid = FluidStack.EMPTY;
        pendingFluid = FluidStack.EMPTY;
        processingItem = ItemStack.EMPTY;
        processingTicks = 0;
        transferCooldown = 0;
        isFillingItem = false;
        sourceDirection = null;
        sourceBlockPos = null;
        processingTarget = ProcessingTarget.NONE;
        clearDripState();
        cachedDripFluids.clear();
        if (needsUpdate) {
            notifyUpdate();
        }
    }

    private void clearFlowVisuals() {
        if (!renderingFluid.isEmpty() || shouldDrip) {
            renderingFluid = FluidStack.EMPTY;
            clearDripState();
            notifyUpdate();
        }
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        writeFluid(tag, TAG_RENDERING_FLUID, renderingFluid);
        writeFluid(tag, TAG_PENDING_FLUID, pendingFluid);
        writeFluid(tag, TAG_DRIP_FLUID, dripFluid);
        writeItem(tag, TAG_PROCESSING_ITEM, processingItem);
        writeDirection(tag, TAG_SOURCE_DIRECTION, sourceDirection);
        writeBlockPos(tag, TAG_SOURCE_POS, sourceBlockPos);
        tag.putBoolean(TAG_IS_FILLING_ITEM, isFillingItem);
        tag.putBoolean(TAG_SHOULD_DRIP, shouldDrip);
        tag.putInt(TAG_PROCESSING_TICKS, processingTicks);
        tag.putInt(TAG_PROCESSING_TARGET, processingTarget.ordinal());
        tag.putInt(TAG_TRANSFER_COOLDOWN, transferCooldown);
        tag.putInt(TAG_DRIP_TICK_COUNTER, dripTickCounter);
        tag.putInt(TAG_DRIP_CYCLE_INDEX, dripCycleIndex);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        renderingFluid = readFluid(tag, TAG_RENDERING_FLUID);
        pendingFluid = readFluid(tag, TAG_PENDING_FLUID);
        dripFluid = readFluid(tag, TAG_DRIP_FLUID);
        processingItem = readItem(tag, TAG_PROCESSING_ITEM);
        sourceDirection = readDirection(tag, TAG_SOURCE_DIRECTION);
        sourceBlockPos = readBlockPos(tag, TAG_SOURCE_POS);
        isFillingItem = tag.getBoolean(TAG_IS_FILLING_ITEM);
        shouldDrip = tag.getBoolean(TAG_SHOULD_DRIP);
        processingTicks = tag.getInt(TAG_PROCESSING_TICKS);
        processingTarget = ProcessingTarget.fromOrdinal(tag.getInt(TAG_PROCESSING_TARGET));
        transferCooldown = tag.getInt(TAG_TRANSFER_COOLDOWN);
        dripTickCounter = tag.getInt(TAG_DRIP_TICK_COUNTER);
        dripCycleIndex = tag.getInt(TAG_DRIP_CYCLE_INDEX);
    }

    private boolean isOpen() {
        return getBlockState().getValue(AbstractFaucetBlock.OPEN);
    }

    private void tickCooldown() {
        if (transferCooldown > 0) {
            transferCooldown--;
        }
    }

    private boolean tickActiveFill() {
        if (!isFillingItem) {
            return false;
        }
        if (processingTicks <= 0) {
            cancelItemFilling();
            return true;
        }

        processingTicks--;
        return switch (processingTarget) {
            case DEPOT -> tickDepotFill();
            case BELT -> tickBeltFill();
            case NONE -> {
                cancelItemFilling();
                yield true;
            }
        };
    }

    private boolean tickDepotFill() {
        if (!validateItemStillPresent()) {
            cancelItemFilling();
            return true;
        }
        if (processingTicks == 0) {
            finishDepotItemFilling();
        }
        return true;
    }

    private boolean tickBeltFill() {
        if (!validateBeltItemStillPresent()) {
            cancelItemFilling();
            return true;
        }
        if (processingTicks == 0) {
            cancelItemFilling();
        }
        return true;
    }

    private boolean validateBeltItemStillPresent() {
        if (processingItem.isEmpty()) {
            return false;
        }

        ItemStack currentItem = getCurrentStackInBeltSegment(worldPosition.below());
        return !currentItem.isEmpty()
            && currentItem.getCount() >= 1
            && ItemStack.isSameItemSameTags(currentItem, processingItem);
    }

    private ItemStack getCurrentStackInBeltSegment(BlockPos beltPos) {
        BlockEntity blockEntity = level.getBlockEntity(beltPos);
        if (blockEntity == null) {
            return ItemStack.EMPTY;
        }

        IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (handler == null || handler.getSlots() <= 0) {
            return ItemStack.EMPTY;
        }

        return handler.getStackInSlot(0);
    }

    private void tickDripEffect() {
        if (!shouldDrip) {
            return;
        }
        if (++dripTickCounter < DRIP_INTERVAL) {
            return;
        }
        dripTickCounter = 0;
        spawnDripParticle();
        advanceDripFluid();
    }

    private void tickDripCacheRefresh() {
        if (dripCacheRefreshCooldown > 0) {
            dripCacheRefreshCooldown--;
            return;
        }

        dripCacheRefreshCooldown = DRIP_CACHE_REFRESH_INTERVAL;
        if (shouldDrip || !cachedDripFluids.isEmpty()) {
            refreshDripCache();
        }
    }

    private boolean applyDripPreview(List<FluidStack> dripPreview) {
        boolean stateChanged = !shouldDrip;
        shouldDrip = true;
        if (!containsFluid(dripPreview, dripFluid)) {
            int index = Math.floorMod(dripCycleIndex, dripPreview.size());
            FluidStack nextDripFluid = FaucetFluidSupport.copyFluidWithAmount(dripPreview.get(index),
                Math.min(dripPreview.get(index).getAmount(), DRIP_AMOUNT));
            boolean fluidChanged = !FaucetFluidSupport.sameFluid(dripFluid, nextDripFluid);
            dripFluid = nextDripFluid;
            stateChanged |= fluidChanged;
            dripTickCounter = DRIP_INTERVAL - 1;
        }
        return stateChanged;
    }

    private void updateDripPreview(IFluidHandler sourceHandler) {
        List<FluidStack> dripPreview = FaucetFluidSupport.previewDripFluids(
            sourceHandler, this::testFluidFilter, DRIP_AMOUNT);
        cacheDripFluids(dripPreview);
        if (dripPreview.isEmpty()) {
            clearFlowVisuals();
            return;
        }

        if (applyDripPreview(dripPreview) || !renderingFluid.isEmpty()) {
            renderingFluid = FluidStack.EMPTY;
            notifyUpdate();
        }
    }

    private void primeDripCache(IFluidHandler sourceHandler) {
        if (shouldDrip) {
            return;
        }

        if (!cachedDripFluids.isEmpty()) {
            if (applyDripPreview(cachedDripFluids) || !renderingFluid.isEmpty()) {
                renderingFluid = FluidStack.EMPTY;
                notifyUpdate();
            }
            return;
        }

        updateDripPreview(sourceHandler);
    }

    private void refreshDripCache() {
        if (hasProcessableTarget(worldPosition.below())) {
            return;
        }

        Direction facing = getBlockState().getValue(AbstractFaucetBlock.FACING);
        IFluidHandler sourceHandler = FaucetFluidSupport.getSourceHandler(level,
            worldPosition.relative(facing.getOpposite()), facing);
        if (sourceHandler == null) {
            cachedDripFluids.clear();
            clearFlowVisuals();
            return;
        }

        List<FluidStack> dripPreview = FaucetFluidSupport.previewDripFluids(
            sourceHandler, this::testFluidFilter, DRIP_AMOUNT);
        cacheDripFluids(dripPreview);
        if (dripPreview.isEmpty()) {
            clearFlowVisuals();
            return;
        }

        if (applyDripPreview(cachedDripFluids) || !renderingFluid.isEmpty()) {
            renderingFluid = FluidStack.EMPTY;
            notifyUpdate();
        }
    }

    private void advanceDripFluid() {
        if (cachedDripFluids.isEmpty()) {
            clearDripState();
            return;
        }

        int currentIndex = indexOfFluid(cachedDripFluids, dripFluid);
        int nextIndex = currentIndex >= 0 ? (currentIndex + 1) % cachedDripFluids.size()
            : Math.floorMod(dripCycleIndex, cachedDripFluids.size());
        dripCycleIndex = nextIndex;
        FluidStack nextFluid = cachedDripFluids.get(nextIndex);
        dripFluid = FaucetFluidSupport.copyFluidWithAmount(nextFluid, Math.min(nextFluid.getAmount(), DRIP_AMOUNT));
    }

    private void clearDripState() {
        shouldDrip = false;
        dripTickCounter = 0;
        dripCycleIndex = 0;
        dripFluid = FluidStack.EMPTY;
    }

    private void cacheDripFluids(List<FluidStack> dripPreview) {
        cachedDripFluids.clear();
        for (FluidStack preview : dripPreview) {
            cachedDripFluids.add(FaucetFluidSupport.copyFluidWithAmount(preview, Math.min(preview.getAmount(), DRIP_AMOUNT)));
        }
    }

    private boolean containsFluid(List<FluidStack> fluids, FluidStack target) {
        return indexOfFluid(fluids, target) != -1;
    }

    private int indexOfFluid(List<FluidStack> fluids, FluidStack target) {
        if (target.isEmpty()) {
            return -1;
        }

        for (int index = 0; index < fluids.size(); index++) {
            if (FaucetFluidSupport.sameFluid(fluids.get(index), target)) {
                return index;
            }
        }

        return -1;
    }

    private boolean isFaucetFillable(BlockState state) {
        return state.is(FAUCET_FILLABLE) || state.is(LEGACY_SMART_FAUCET_FILLABLE);
    }

    private void writeFluid(CompoundTag tag, String key, FluidStack stack) {
        if (!stack.isEmpty()) {
            tag.put(key, stack.writeToNBT(new CompoundTag()));
        }
    }

    private void writeItem(CompoundTag tag, String key, ItemStack stack) {
        if (!stack.isEmpty()) {
            tag.put(key, stack.save(new CompoundTag()));
        }
    }

    private void writeDirection(CompoundTag tag, String key, @Nullable Direction direction) {
        if (direction != null) {
            tag.putInt(key, direction.get3DDataValue());
        }
    }

    private void writeBlockPos(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.putLong(key, pos.asLong());
        }
    }

    private FluidStack readFluid(CompoundTag tag, String key) {
        return tag.contains(key) ? FluidStack.loadFluidStackFromNBT(tag.getCompound(key)) : FluidStack.EMPTY;
    }

    private ItemStack readItem(CompoundTag tag, String key) {
        return tag.contains(key) ? ItemStack.of(tag.getCompound(key)) : ItemStack.EMPTY;
    }

    private @Nullable Direction readDirection(CompoundTag tag, String key) {
        return tag.contains(key) ? Direction.from3DDataValue(tag.getInt(key)) : null;
    }

    private @Nullable BlockPos readBlockPos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
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
