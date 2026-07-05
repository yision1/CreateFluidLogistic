package com.yision.fluidlogistics.content.fluids.faucet;

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
import com.yision.fluidlogistics.content.fluids.faucet.SmartFaucetFilterSlotPositioning;
import com.yision.fluidlogistics.compat.CompatMods;
import com.yision.fluidlogistics.compat.kaleidoscopetavern.KaleidoscopeTavernCompat;
import com.yision.fluidlogistics.compat.sable.SableSublevelTargetHelper;
import com.yision.fluidlogistics.content.fluids.infiniteWater.InfiniteWaterSource;
import com.yision.fluidlogistics.content.fluids.faucet.network.FaucetDripParticlePacket;
import com.yision.fluidlogistics.util.MergedFluidDisplayHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
    private static final int KALEIDOSCOPE_TAP_TIME = 30;
    private static final int KALEIDOSCOPE_TAP_PARTICLE_TIME = 5;
    private static final String TAG_KALEIDOSCOPE_TAP_TICKS = "KaleidoscopeTapTicks";
    private static final String TAG_KALEIDOSCOPE_TAP_PARTICLE = "KaleidoscopeTapParticle";
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
    private int kaleidoscopeTapTicks;
    private @Nullable ParticleOptions kaleidoscopeTapParticle;
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
        if (tickKaleidoscopeTap()) {
            return;
        }
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
        if (InfiniteWaterSource.isWaterSourceBlock(sourceState)) {
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
        if (InfiniteWaterSource.isWaterSourceBlock(level.getBlockState(sourcePos))) {
            return null;
        }

        IFluidHandler source = FaucetFluidSupport.getSourceHandler(level, sourcePos, facing);
        if (source == null) {
            return null;
        }

        MergedFluidDisplayHandler display = new MergedFluidDisplayHandler(source, this::testFluidFilter);
        return display.getTanks() == 0 ? null : display;
    }

    public void onTargetChanged() {
        if (isFillingItem) {
            cancelItemFilling();
        }
        clearKaleidoscopeTapState();
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

        if (tryStartKaleidoscopeTap()) {
            return;
        }

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

    private boolean tryStartKaleidoscopeTap() {
        if (!CompatMods.kaleidoscopeTavernLoaded()) {
            return false;
        }
        KaleidoscopeTavernCompat.TapOperation operation = KaleidoscopeTavernCompat.prepare(
            level, worldPosition, getBlockState(), this::testFluidFilter);
        if (operation == null) {
            return false;
        }

        kaleidoscopeTapTicks = KALEIDOSCOPE_TAP_TIME;
        kaleidoscopeTapParticle = operation.particle();
        FluidStack mappedFluid = operation.mappedFluid();
        if (!mappedFluid.isEmpty()) {
            renderingFluid = mappedFluid.copyWithAmount(250);
        } else {
            renderingFluid = FluidStack.EMPTY;
        }
        AllSoundEvents.SPOUTING.playOnServer(level, worldPosition, 0.75f, 0.9f + 0.2f * level.random.nextFloat());
        notifyUpdate();
        return true;
    }

    private boolean hasProcessableTarget(BlockPos targetPos) {
        if (CompatMods.kaleidoscopeTavernLoaded()
            && KaleidoscopeTavernCompat.canStart(level, worldPosition, getBlockState(), this::testFluidFilter)) {
            return true;
        }

        BlockState directState = level.getBlockState(targetPos);
        if (isCauldronTarget(directState)) {
            return true;
        }

        BlockEntity directEntity = level.getBlockEntity(targetPos);
        if (directEntity != null) {
            return hasProcessableBlockEntityTarget(directEntity, directState);
        }

        var resolved = SableSublevelTargetHelper.resolveBlockEntity(level, targetPos);
        BlockEntity targetEntity = resolved.blockEntity();
        BlockState targetState = level.getBlockState(resolved.resolvedPos());
        return hasProcessableBlockEntityTarget(targetEntity, targetState);
    }

    private boolean hasProcessableBlockEntityTarget(@Nullable BlockEntity targetEntity, BlockState targetState) {
        if (targetEntity != null && FaucetTargetSupport.isDepot(targetEntity)) {
            ItemStack itemOnDepot = FaucetTargetSupport.getItemOnDepot(targetEntity);
            return !itemOnDepot.isEmpty() && FaucetFilling.canItemBeFilled(level, itemOnDepot);
        }

        return targetEntity != null && targetState.is(FAUCET_FILLABLE);
    }

    private boolean tryProcess(IFluidHandler sourceHandler, BlockPos targetPos, Direction sourceDir, BlockPos sourcePos) {
        BlockState directState = level.getBlockState(targetPos);
        if (isCauldronTarget(directState)) {
            return tryProcessTarget(sourceHandler, targetPos, level.getBlockEntity(targetPos), directState, sourceDir, sourcePos);
        }

        BlockEntity directEntity = level.getBlockEntity(targetPos);
        if (directEntity != null) {
            return tryProcessTarget(sourceHandler, targetPos, directEntity, directState, sourceDir, sourcePos);
        }

        var resolved = SableSublevelTargetHelper.resolveBlockEntity(level, targetPos);
        BlockPos resolvedPos = resolved.resolvedPos();
        return tryProcessTarget(sourceHandler, resolvedPos, resolved.blockEntity(), level.getBlockState(resolvedPos),
            sourceDir, sourcePos);
    }

    private boolean tryProcessTarget(IFluidHandler sourceHandler, BlockPos resolvedPos,
        @Nullable BlockEntity targetEntity, BlockState targetState, Direction sourceDir, BlockPos sourcePos) {
        if (targetEntity != null && FaucetTargetSupport.isDepot(targetEntity)) {
            ItemStack itemOnDepot = FaucetTargetSupport.getItemOnDepot(targetEntity);
            if (!itemOnDepot.isEmpty() && FaucetFilling.canItemBeFilled(level, itemOnDepot)) {
                FluidStack fillableFluid = FaucetFluidSupport.findFillableFluidForItem(level, sourceHandler,
                    this::testFluidFilter, itemOnDepot);
                if (!fillableFluid.isEmpty()) {
                    return startItemFilling(sourceHandler, itemOnDepot, sourceDir, sourcePos, fillableFluid);
                }
            }
            return false;
        }

        if (isCauldronTarget(targetState)) {
            FluidStack fillableFluid = FaucetFluidSupport.findFillableFluidForCauldron(sourceHandler,
                this::testFluidFilter, targetState);
            if (fillableFluid.isEmpty()) {
                return false;
            }
            FluidStack transferred = FaucetTargetSupport.fillCauldron(level, sourceHandler, resolvedPos, targetState, fillableFluid);
            if (transferred.isEmpty()) {
                return false;
            }
            renderingFluid = transferred.copy();
            notifyUpdate();
            return true;
        }

        if (targetEntity == null || !targetState.is(FAUCET_FILLABLE)) {
            return false;
        }

        IFluidHandler targetHandler = FaucetTargetSupport.getTargetHandler(level, targetEntity);
        if (targetHandler == null) {
            return false;
        }

        FluidStack transferred = FaucetTargetSupport.fillContainer(level, worldPosition, sourceHandler, targetHandler,
            this::testFluidFilter, TRANSFER_RATE);
        if (transferred.isEmpty()) {
            return false;
        }
        renderingFluid = transferred.copy();
        notifyUpdate();
        return true;
    }

    private static boolean isCauldronTarget(BlockState targetState) {
        return targetState.is(Blocks.CAULDRON) || targetState.is(Blocks.WATER_CAULDRON);
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

    private @Nullable ItemFillContext getItemFillContext(ItemStack item) {
        Direction facing = getBlockState().getValue(AbstractFaucetBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(facing.getOpposite());
        IFluidHandler sourceHandler = FaucetFluidSupport.getSourceHandler(level, sourcePos, facing);
        if (sourceHandler == null) {
            return null;
        }

        FluidStack fillableFluid = FaucetFluidSupport.findFillableFluidForItem(level, sourceHandler,
            this::testFluidFilter, item);
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
        IFluidHandler sourceHandler = FaucetFluidSupport.getSourceHandler(level, worldPosition.relative(facing.getOpposite()), facing);
        return sourceHandler != null && FaucetFluidSupport.hasPotentialFluidForItem(level, sourceHandler,
            this::testFluidFilter, transported.stack) ? HOLD : PASS;
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
            IFluidHandler sourceHandler = FaucetFluidSupport.getSourceHandler(level, worldPosition.relative(facing.getOpposite()), facing);
            return sourceHandler != null && FaucetFluidSupport.hasPotentialFluidForItem(level, sourceHandler,
                this::testFluidFilter, transported.stack) ? HOLD : PASS;
        }

        return startBeltFilling(fillContext, transported.stack) ? HOLD : PASS;
    }

    private boolean isDirectlyAbove(TransportedItemStackHandlerBehaviour handler) {
        if (handler.blockEntity == null) {
            return false;
        }
        return SableSublevelTargetHelper.isSameBlockAcrossSublevels(level, worldPosition.below(), handler.blockEntity.getBlockPos());
    }

    private boolean validateItemStillPresent() {
        if (processingItem.isEmpty()) {
            return false;
        }

        var resolved = SableSublevelTargetHelper.resolveBlockEntity(level, worldPosition.below());
        BlockEntity targetEntity = resolved.blockEntity();
        if (!FaucetTargetSupport.isDepot(targetEntity)) {
            return false;
        }

        ItemStack currentItem = FaucetTargetSupport.getItemOnDepot(targetEntity);
        return ItemStack.isSameItemSameComponents(currentItem, processingItem) && currentItem.getCount() >= 1;
    }

    private void finishDepotItemFilling() {
        if (!isFillingItem || processingTarget != ProcessingTarget.DEPOT || processingItem.isEmpty() || pendingFluid.isEmpty()) {
            return;
        }

        var resolved = SableSublevelTargetHelper.resolveBlockEntity(level, worldPosition.below());
        BlockPos targetPos = resolved.resolvedPos();
        BlockEntity targetEntity = resolved.blockEntity();
        if (!FaucetTargetSupport.isDepot(targetEntity)) {
            cancelItemFilling();
            return;
        }

        ItemStack currentItem = FaucetTargetSupport.getItemOnDepot(targetEntity);
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

        if (InfiniteWaterSource.isActiveSourceFor(InfiniteWaterSource.Consumer.FAUCET,
                level.getBlockState(sourceBlockPos))
            && pendingFluid.getFluid() == Fluids.WATER) {
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
        boolean needsUpdate = !renderingFluid.isEmpty() || !pendingFluid.isEmpty() || shouldDrip || isFillingItem
            || kaleidoscopeTapTicks > 0;
        renderingFluid = FluidStack.EMPTY;
        pendingFluid = FluidStack.EMPTY;
        processingItem = ItemStack.EMPTY;
        processingTicks = 0;
        transferCooldown = 0;
        isFillingItem = false;
        sourceDirection = null;
        sourceBlockPos = null;
        processingTarget = ProcessingTarget.NONE;
        clearKaleidoscopeTapState();
        clearDripState();
        cachedDripFluids.clear();
        if (needsUpdate) {
            notifyUpdate();
        }
    }

    private void clearKaleidoscopeTapState() {
        kaleidoscopeTapTicks = 0;
        kaleidoscopeTapParticle = null;
    }

    private void clearFlowVisuals() {
        if (!renderingFluid.isEmpty() || shouldDrip) {
            renderingFluid = FluidStack.EMPTY;
            clearDripState();
            notifyUpdate();
        }
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
        tag.putInt(TAG_KALEIDOSCOPE_TAP_TICKS, kaleidoscopeTapTicks);
        writeParticleOptions(tag, TAG_KALEIDOSCOPE_TAP_PARTICLE, kaleidoscopeTapParticle);
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
        kaleidoscopeTapTicks = tag.getInt(TAG_KALEIDOSCOPE_TAP_TICKS);
        kaleidoscopeTapParticle = readParticleOptions(tag, TAG_KALEIDOSCOPE_TAP_PARTICLE);

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

    private boolean tickKaleidoscopeTap() {
        if (kaleidoscopeTapTicks <= 0) {
            return false;
        }

        int elapsed = KALEIDOSCOPE_TAP_TIME - kaleidoscopeTapTicks + 1;
        kaleidoscopeTapTicks--;

        if (elapsed <= KALEIDOSCOPE_TAP_PARTICLE_TIME
            && kaleidoscopeTapParticle != null
            && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(kaleidoscopeTapParticle,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.25, worldPosition.getZ() + 0.5,
                1, 0, 0, 0, 0);
        }

        if (kaleidoscopeTapTicks > 0) {
            return true;
        }

        if (CompatMods.kaleidoscopeTavernLoaded()) {
            KaleidoscopeTavernCompat.finish(level, worldPosition, getBlockState());
        }
        kaleidoscopeTapParticle = null;
        renderingFluid = FluidStack.EMPTY;
        notifyUpdate();
        return true;
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
            && ItemStack.isSameItemSameComponents(currentItem.copyWithCount(1), processingItem);
    }

    private ItemStack getCurrentStackInBeltSegment(BlockPos beltPos) {
        var resolved = SableSublevelTargetHelper.resolveBlockEntity(level, beltPos);
        BlockEntity blockEntity = resolved.blockEntity();
        if (blockEntity == null) {
            return ItemStack.EMPTY;
        }
        BlockPos resolvedPos = resolved.resolvedPos();
        var state = level.getBlockState(resolvedPos);
        var handler = level.getCapability(
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
        List<FluidStack> dripPreview = FaucetFluidSupport.previewDripFluids(sourceHandler, this::testFluidFilter, DRIP_AMOUNT);
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
        IFluidHandler sourceHandler = FaucetFluidSupport.getSourceHandler(level, worldPosition.relative(facing.getOpposite()), facing);
        if (sourceHandler == null) {
            cachedDripFluids.clear();
            clearFlowVisuals();
            return;
        }

        List<FluidStack> dripPreview = FaucetFluidSupport.previewDripFluids(sourceHandler, this::testFluidFilter, DRIP_AMOUNT);
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

    private void writeParticleOptions(CompoundTag tag, String key, @Nullable ParticleOptions particle) {
        if (particle == null) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType());
        if (id == null) {
            return;
        }
        tag.putString(key, id.toString());
    }

    private @Nullable ParticleOptions readParticleOptions(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.parse(tag.getString(key));
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id);
        if (!(type instanceof SimpleParticleType simpleParticleType)) {
            return null;
        }
        return simpleParticleType;
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
