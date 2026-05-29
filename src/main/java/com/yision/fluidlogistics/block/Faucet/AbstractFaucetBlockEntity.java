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
import com.yision.fluidlogistics.network.FaucetDripParticlePacket;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

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
    private static final InfiniteWaterSourceHandler INFINITE_WATER_SOURCE = new InfiniteWaterSourceHandler();
    private static Field depotOutputBufferField;

    protected BeltProcessingBehaviour beltProcessing;
    protected FilteringBehaviour filtering;
    protected FluidStack renderingFluid = FluidStack.EMPTY;
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

    protected AbstractFaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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

        return hasFluidCapability(sourcePos, sourceSide.getOpposite()) || hasFluidCapability(sourcePos, null);
    }

    public void onTargetChanged() {
        if (isFillingItem) {
            cancelItemFilling();
        }
        transferCooldown = 0;
        clearDripState();
        notifyUpdate();
    }

    private void tryTransferFluid() {
        BlockPos targetPos = worldPosition.below();
        Direction facing = getBlockState().getValue(AbstractFaucetBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(facing.getOpposite());
        IFluidHandler sourceHandler = getSourceHandler(sourcePos, facing);

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

        if (targetEntity != null && isDepot(targetEntity)) {
            ItemStack itemOnDepot = getItemOnDepot(targetEntity);
            return !itemOnDepot.isEmpty() && FaucetFilling.canItemBeFilled(level, itemOnDepot);
        }

        if (targetState.is(Blocks.CAULDRON) || targetState.is(Blocks.WATER_CAULDRON)) {
            return true;
        }

        return targetEntity != null && targetState.is(FAUCET_FILLABLE);
    }

    private boolean tryProcess(IFluidHandler sourceHandler, BlockPos targetPos, Direction sourceDir, BlockPos sourcePos) {
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        BlockState targetState = level.getBlockState(targetPos);

        if (targetEntity != null && isDepot(targetEntity)) {
            ItemStack itemOnDepot = getItemOnDepot(targetEntity);
            if (!itemOnDepot.isEmpty() && FaucetFilling.canItemBeFilled(level, itemOnDepot)) {
                FluidStack fillableFluid = findFillableFluidForItem(sourceHandler, itemOnDepot);
                if (!fillableFluid.isEmpty()) {
                    return startItemFilling(sourceHandler, itemOnDepot, sourceDir, sourcePos, fillableFluid);
                }
            }
            return false;
        }

        if (targetState.is(Blocks.CAULDRON) || targetState.is(Blocks.WATER_CAULDRON)) {
            FluidStack fillableFluid = findFillableFluidForCauldron(sourceHandler, targetState);
            return !fillableFluid.isEmpty() && tryFillCauldron(sourceHandler, targetPos, targetState, fillableFluid);
        }

        if (targetEntity == null || !targetState.is(FAUCET_FILLABLE)) {
            return false;
        }

        return tryFillContainer(sourceHandler, targetEntity);
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

        FluidStack simulatedDrain = sourceHandler.drain(availableFluid.copyWithAmount(requiredAmount), FluidAction.SIMULATE);
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

    private FluidStack findFillableFluidForItem(IFluidHandler sourceHandler, ItemStack item) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || filtering != null && !filtering.test(candidate)) {
                continue;
            }

            int requiredAmount = FaucetFilling.getRequiredAmountForItem(level, item, candidate.copy());
            if (requiredAmount > 0 && requiredAmount <= candidate.getAmount()) {
                return candidate.copy();
            }
        }

        FluidStack fallback = previewFluid(sourceHandler, Integer.MAX_VALUE);
        if (fallback.isEmpty()) {
            return FluidStack.EMPTY;
        }

        int requiredAmount = FaucetFilling.getRequiredAmountForItem(level, item, fallback.copy());
        return requiredAmount > 0 && requiredAmount <= fallback.getAmount() ? fallback : FluidStack.EMPTY;
    }

    private boolean hasPotentialFluidForItem(IFluidHandler sourceHandler, ItemStack item) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || filtering != null && !filtering.test(candidate)) {
                continue;
            }

            if (FaucetFilling.getRequiredAmountForItem(level, item, candidate.copy()) > 0) {
                return true;
            }
        }

        FluidStack fallback = previewFluid(sourceHandler, Integer.MAX_VALUE);
        return !fallback.isEmpty() && FaucetFilling.getRequiredAmountForItem(level, item, fallback.copy()) > 0;
    }

    private @Nullable ItemFillContext getItemFillContext(ItemStack item) {
        Direction facing = getBlockState().getValue(AbstractFaucetBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(facing.getOpposite());
        IFluidHandler sourceHandler = getSourceHandler(sourcePos, facing);
        if (sourceHandler == null) {
            return null;
        }

        FluidStack fillableFluid = findFillableFluidForItem(sourceHandler, item);
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
        IFluidHandler sourceHandler = getSourceHandler(worldPosition.relative(facing.getOpposite()), facing);
        return sourceHandler != null && hasPotentialFluidForItem(sourceHandler, transported.stack) ? HOLD : PASS;
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
                || !ItemStack.isSameItemSameComponents(transported.stack.copyWithCount(1), processingItem)) {
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
            IFluidHandler sourceHandler = getSourceHandler(worldPosition.relative(facing.getOpposite()), facing);
            return sourceHandler != null && hasPotentialFluidForItem(sourceHandler, transported.stack) ? HOLD : PASS;
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
        if (!isDepot(targetEntity)) {
            return false;
        }

        ItemStack currentItem = getItemOnDepot(targetEntity);
        return ItemStack.isSameItemSameComponents(currentItem, processingItem) && currentItem.getCount() >= 1;
    }

    private void finishDepotItemFilling() {
        if (!isFillingItem || processingTarget != ProcessingTarget.DEPOT || processingItem.isEmpty() || pendingFluid.isEmpty()) {
            return;
        }

        BlockPos targetPos = worldPosition.below();
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        if (!isDepot(targetEntity)) {
            cancelItemFilling();
            return;
        }

        ItemStack currentItem = getItemOnDepot(targetEntity);
        if (!ItemStack.isSameItemSameComponents(currentItem, processingItem) || currentItem.getCount() < 1) {
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

        ItemStack singleItem = currentItem.copyWithCount(1);
        ItemStack result = FaucetFilling.fillItem(level, pendingFluid.getAmount(), singleItem, pendingFluid.copy());
        if (result.isEmpty()) {
            cancelItemFilling();
            return;
        }

        ItemStack remaining = currentItem.copy();
        remaining.shrink(1);
        if (remaining.isEmpty()) {
            behaviour.setHeldItem(new TransportedItemStack(result));
        } else {
            behaviour.setHeldItem(new TransportedItemStack(remaining));
            storeDepotOutput(behaviour, result, targetPos);
        }

        targetEntity.setChanged();
        notifyTargetUpdate(targetEntity);
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

        IFluidHandler sourceHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, sourceBlockPos, sourceDirection);
        if (sourceHandler == null) {
            sourceHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, sourceBlockPos, null);
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

    private boolean tryFillCauldron(IFluidHandler sourceHandler, BlockPos targetPos, BlockState targetState,
        FluidStack availableFluid) {
        if (availableFluid.getFluid() == Fluids.WATER) {
            if (targetState.is(Blocks.CAULDRON)) {
                return fillWaterCauldronLevel(sourceHandler, targetPos, 1);
            }

            if (targetState.is(Blocks.WATER_CAULDRON) && targetState.hasProperty(LayeredCauldronBlock.LEVEL)) {
                int currentLevel = targetState.getValue(LayeredCauldronBlock.LEVEL);
                if (currentLevel < LayeredCauldronBlock.MAX_FILL_LEVEL) {
                    return fillWaterCauldronLevel(sourceHandler, targetPos, currentLevel + 1);
                }
            }
            return false;
        }

        if (!targetState.is(Blocks.CAULDRON)) {
            return false;
        }

        var cauldronInfo = com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior.CAULDRON_INFO
            .get(availableFluid.getFluid());
        if (cauldronInfo == null || availableFluid.getAmount() < cauldronInfo.amount()) {
            return false;
        }

        FluidStack drained = sourceHandler.drain(availableFluid.copyWithAmount(cauldronInfo.amount()), FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < cauldronInfo.amount()) {
            return false;
        }

        level.setBlockAndUpdate(targetPos, cauldronInfo.cauldron());
        renderingFluid = drained.copy();
        level.playSound(null, targetPos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
        notifyUpdate();
        return true;
    }

    private boolean fillWaterCauldronLevel(IFluidHandler sourceHandler, BlockPos targetPos, int targetLevel) {
        FluidStack drained = sourceHandler.drain(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < 250) {
            return false;
        }

        level.setBlockAndUpdate(targetPos,
            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, targetLevel));
        renderingFluid = drained.copy();
        level.playSound(null, targetPos, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 0.8f + targetLevel * 0.1f);
        notifyUpdate();
        return true;
    }

    private boolean tryFillContainer(IFluidHandler sourceHandler, BlockEntity targetEntity) {
        IFluidHandler targetHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetEntity.getBlockPos(),
            Direction.UP);
        if (targetHandler == null) {
            targetHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetEntity.getBlockPos(), null);
        }
        if (targetHandler == null) {
            return false;
        }

        FluidStack availableFluid = findFillableFluidForContainer(sourceHandler, targetHandler);
        if (availableFluid.isEmpty()) {
            return false;
        }

        FluidStack toTransfer = availableFluid.copyWithAmount(Math.min(availableFluid.getAmount(), TRANSFER_RATE));
        int filled = targetHandler.fill(toTransfer, FluidAction.SIMULATE);
        if (filled <= 0) {
            return false;
        }

        FluidStack actualDrain = sourceHandler.drain(toTransfer.copyWithAmount(filled), FluidAction.EXECUTE);
        if (actualDrain.isEmpty()) {
            return false;
        }

        targetHandler.fill(actualDrain, FluidAction.EXECUTE);
        renderingFluid = actualDrain.copy();
        if (level.random.nextFloat() < 0.1f) {
            AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.3f, 0.9f + 0.2f * level.random.nextFloat());
        }
        notifyUpdate();
        return true;
    }

    private FluidStack findFillableFluidForCauldron(IFluidHandler sourceHandler, BlockState targetState) {
        return findMatchingFluid(sourceHandler, candidate -> canFillCauldron(targetState, candidate), Integer.MAX_VALUE);
    }

    private FluidStack findFillableFluidForContainer(IFluidHandler sourceHandler, IFluidHandler targetHandler) {
        return findMatchingFluid(sourceHandler, candidate -> {
            FluidStack preview = candidate.copyWithAmount(Math.min(candidate.getAmount(), TRANSFER_RATE));
            return targetHandler.fill(preview, FluidAction.SIMULATE) > 0;
        }, TRANSFER_RATE);
    }

    private FluidStack findMatchingFluid(IFluidHandler sourceHandler, Predicate<FluidStack> predicate, int maxAmount) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || filtering != null && !filtering.test(candidate)) {
                continue;
            }

            FluidStack preview = candidate.copyWithAmount(Math.min(candidate.getAmount(), maxAmount));
            if (predicate.test(preview)) {
                return preview;
            }
        }

        FluidStack drained = sourceHandler.drain(maxAmount, FluidAction.SIMULATE);
        if (!drained.isEmpty() && (filtering == null || filtering.test(drained)) && predicate.test(drained)) {
            return drained;
        }
        return FluidStack.EMPTY;
    }

    private boolean canFillCauldron(BlockState targetState, FluidStack availableFluid) {
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

        var cauldronInfo = com.simibubi.create.api.behaviour.spouting.CauldronSpoutingBehavior.CAULDRON_INFO
            .get(availableFluid.getFluid());
        return cauldronInfo != null && availableFluid.getAmount() >= cauldronInfo.amount();
    }

    private void spawnDripParticle() {
        if (!(level instanceof ServerLevel serverLevel) || dripFluid.isEmpty()) {
            return;
        }

        Vec3 spoutPos = Vec3.atCenterOf(worldPosition).add(0, -0.3, 0);
        PacketDistributor.sendToPlayersNear(serverLevel, null, spoutPos.x, spoutPos.y, spoutPos.z, 32.0,
            new FaucetDripParticlePacket(worldPosition, dripFluid.copy()));

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

    private void storeDepotOutput(DepotBehaviour behaviour, ItemStack result, BlockPos targetPos) {
        try {
            if (depotOutputBufferField == null) {
                depotOutputBufferField = DepotBehaviour.class.getDeclaredField("processingOutputBuffer");
                depotOutputBufferField.setAccessible(true);
            }
            ItemStackHandler outputBuffer = (ItemStackHandler) depotOutputBufferField.get(behaviour);
            ItemStack remainder = result.copy();
            for (int slot = 0; slot < outputBuffer.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = outputBuffer.insertItem(slot, remainder, false);
            }
            if (!remainder.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, targetPos.getX() + 0.5, targetPos.getY() + 0.75,
                    targetPos.getZ() + 0.5, remainder);
            }
        } catch (ReflectiveOperationException exception) {
            net.minecraft.world.Containers.dropItemStack(level, targetPos.getX() + 0.5, targetPos.getY() + 0.75,
                targetPos.getZ() + 0.5, result);
        }
    }

    private void notifyTargetUpdate(BlockEntity targetEntity) {
        level.sendBlockUpdated(targetEntity.getBlockPos(), targetEntity.getBlockState(), targetEntity.getBlockState(), 3);
        if (targetEntity instanceof SmartBlockEntity smartBlockEntity) {
            smartBlockEntity.notifyUpdate();
        }
    }

    private boolean isDepot(@Nullable BlockEntity entity) {
        return entity != null && DepotBehaviour.get(entity, DepotBehaviour.TYPE) != null;
    }

    private ItemStack getItemOnDepot(BlockEntity depot) {
        DepotBehaviour behaviour = DepotBehaviour.get(depot, DepotBehaviour.TYPE);
        return behaviour == null ? ItemStack.EMPTY : behaviour.getHeldItemStack();
    }

    private @Nullable IFluidHandler getSourceHandler(BlockPos sourcePos, Direction side) {
        if (AbstractFaucetBlock.isInfiniteWaterSource(level.getBlockState(sourcePos))) {
            return INFINITE_WATER_SOURCE;
        }
        IFluidHandler sidedHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, side);
        if (sidedHandler != null) {
            return sidedHandler;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, sourcePos, null);
    }

    private FluidStack previewFluid(IFluidHandler sourceHandler, int maxAmount) {
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || filtering != null && !filtering.test(candidate)) {
                continue;
            }
            return candidate.copyWithAmount(Math.min(candidate.getAmount(), maxAmount));
        }

        FluidStack drained = sourceHandler.drain(maxAmount, FluidAction.SIMULATE);
        if (!drained.isEmpty() && (filtering == null || filtering.test(drained))) {
            return drained;
        }
        return FluidStack.EMPTY;
    }

    private List<FluidStack> previewDripFluids(IFluidHandler sourceHandler) {
        List<FluidStack> dripFluids = new ArrayList<>();
        for (int tank = 0; tank < sourceHandler.getTanks(); tank++) {
            FluidStack candidate = sourceHandler.getFluidInTank(tank);
            if (candidate.isEmpty() || filtering != null && !filtering.test(candidate)) {
                continue;
            }

            FluidStack preview = candidate.copyWithAmount(Math.min(candidate.getAmount(), DRIP_AMOUNT));
            boolean duplicate = false;
            for (FluidStack existing : dripFluids) {
                if (FluidStack.isSameFluidSameComponents(existing, preview)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                dripFluids.add(preview);
            }
        }

        if (!dripFluids.isEmpty()) {
            return dripFluids;
        }

        FluidStack drained = sourceHandler.drain(DRIP_AMOUNT, FluidAction.SIMULATE);
        if (!drained.isEmpty() && (filtering == null || filtering.test(drained))) {
            dripFluids.add(drained.copyWithAmount(Math.min(drained.getAmount(), DRIP_AMOUNT)));
        }
        return dripFluids;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeFluid(tag, registries, TAG_RENDERING_FLUID, renderingFluid);
        writeFluid(tag, registries, TAG_PENDING_FLUID, pendingFluid);
        writeFluid(tag, registries, TAG_DRIP_FLUID, dripFluid);
        writeItem(tag, registries, TAG_PROCESSING_ITEM, processingItem);
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
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        renderingFluid = readFluid(tag, registries, TAG_RENDERING_FLUID);
        pendingFluid = readFluid(tag, registries, TAG_PENDING_FLUID);
        dripFluid = readFluid(tag, registries, TAG_DRIP_FLUID);
        processingItem = readItem(tag, registries, TAG_PROCESSING_ITEM);
        sourceDirection = readDirection(tag, TAG_SOURCE_DIRECTION);
        sourceBlockPos = readBlockPos(tag, TAG_SOURCE_POS);
        isFillingItem = tag.getBoolean(TAG_IS_FILLING_ITEM);
        shouldDrip = tag.getBoolean(TAG_SHOULD_DRIP);
        processingTicks = tag.getInt(TAG_PROCESSING_TICKS);
        processingTarget = ProcessingTarget.fromOrdinal(tag.getInt(TAG_PROCESSING_TARGET));
        transferCooldown = tag.getInt(TAG_TRANSFER_COOLDOWN);
        dripTickCounter = tag.getInt(TAG_DRIP_TICK_COUNTER);
        dripCycleIndex = tag.getInt(TAG_DRIP_CYCLE_INDEX);

        if (!supportsBeltFilling() && processingTarget == ProcessingTarget.BELT) {
            clearItemFillingState();
        }
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
        if (processingTicks == 0) {
            cancelItemFilling();
        }
        return true;
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
            FluidStack nextDripFluid = dripPreview.get(index).copyWithAmount(Math.min(dripPreview.get(index).getAmount(), DRIP_AMOUNT));
            boolean fluidChanged = !FluidStack.isSameFluidSameComponents(dripFluid, nextDripFluid);
            dripFluid = nextDripFluid;
            stateChanged |= fluidChanged;
            dripTickCounter = DRIP_INTERVAL - 1;
        }
        return stateChanged;
    }

    private void updateDripPreview(IFluidHandler sourceHandler) {
        List<FluidStack> dripPreview = previewDripFluids(sourceHandler);
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
        IFluidHandler sourceHandler = getSourceHandler(worldPosition.relative(facing.getOpposite()), facing);
        if (sourceHandler == null) {
            cachedDripFluids.clear();
            clearFlowVisuals();
            return;
        }

        List<FluidStack> dripPreview = previewDripFluids(sourceHandler);
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
        dripFluid = nextFluid.copyWithAmount(Math.min(nextFluid.getAmount(), DRIP_AMOUNT));
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
            cachedDripFluids.add(preview.copyWithAmount(Math.min(preview.getAmount(), DRIP_AMOUNT)));
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
            if (FluidStack.isSameFluidSameComponents(fluids.get(index), target)) {
                return index;
            }
        }
        return -1;
    }

    private boolean hasFluidCapability(BlockPos pos, @Nullable Direction side) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side) != null;
    }

    private void writeFluid(CompoundTag tag, HolderLookup.Provider registries, String key, FluidStack stack) {
        if (!stack.isEmpty()) {
            tag.put(key, stack.save(registries));
        }
    }

    private void writeItem(CompoundTag tag, HolderLookup.Provider registries, String key, ItemStack stack) {
        if (!stack.isEmpty()) {
            tag.put(key, stack.save(registries));
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

    private FluidStack readFluid(CompoundTag tag, HolderLookup.Provider registries, String key) {
        return tag.contains(key) ? FluidStack.parse(registries, tag.getCompound(key)).orElse(FluidStack.EMPTY)
            : FluidStack.EMPTY;
    }

    private ItemStack readItem(CompoundTag tag, HolderLookup.Provider registries, String key) {
        return tag.contains(key) ? ItemStack.parse(registries, tag.getCompound(key)).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
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

    private static class InfiniteWaterSourceHandler implements IFluidHandler {
        private static final FluidStack WATER = new FluidStack(Fluids.WATER, 1000);

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return WATER.copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) {
                return FluidStack.EMPTY;
            }
            return WATER.copyWithAmount(Math.min(resource.getAmount(), WATER.getAmount()));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return maxDrain <= 0 ? FluidStack.EMPTY : WATER.copyWithAmount(Math.min(maxDrain, WATER.getAmount()));
        }
    }
}
