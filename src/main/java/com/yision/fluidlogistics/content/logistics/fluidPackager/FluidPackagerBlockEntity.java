package com.yision.fluidlogistics.content.logistics.fluidPackager;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.compat.computercraft.ComputerCraftProxy;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.TankManipulationBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.fluidlogistics.api.IFluidPackager;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackager.PackagerGoggleInfo;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidHandlerHelper;
import com.yision.fluidlogistics.util.IPackagerOverrideData;

import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemStackHandler;

public class FluidPackagerBlockEntity extends PackagerBlockEntity
    implements Clearable, IFluidPackager, IPackagerOverrideData, IHaveGoggleInformation {

    public String clipboardAddress;
    public TankManipulationBehaviour fluidTarget;
    public List<FluidStack> pendingFluidsToInsert;

    private boolean manualOverrideLocked;
    private InventorySummary availableItems;
    private Map<FluidTypeKey, Integer> availableFluidSnapshot;
    private AdvancementBehaviour advancements;

    public FluidPackagerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        clipboardAddress = "";
        manualOverrideLocked = false;
        pendingFluidsToInsert = new LinkedList<>();
        availableFluidSnapshot = Map.of();
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            AllBlockEntities.FLUID_PACKAGER.get(),
            (be, context) -> {
                return be.inventory;
            }
        );
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(fluidTarget = new TankManipulationBehaviour(this, InterfaceProvider.oppositeOfBlockFacing())
            .withFilter(this::supportsFluidTarget));
        targetInventory = new InvManipulationBehaviour(this, InterfaceProvider.oppositeOfBlockFacing())
            .withFilter(this::supportsItemTarget);
        behaviours.add(targetInventory);
        behaviours.add(advancements = new AdvancementBehaviour(this, AllAdvancements.PACKAGER));
        behaviours.add(computerBehaviour = ComputerCraftProxy.behaviour(this));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        String address = signBasedAddress;
        if (level != null && level.isClientSide) {
            String scannedAddress = findSignAddress();
            if (!scannedAddress.isBlank()) {
                address = scannedAddress;
            } else if (address.isBlank()) {
                address = fluidlogistics$getClipboardAddress();
            }
        } else if (address.isBlank()) {
            address = fluidlogistics$getClipboardAddress();
        }

        BlockState state = getBlockState();
        boolean isLinkedToNetwork = state.hasProperty(FluidPackagerBlock.LINKED) && state.getValue(FluidPackagerBlock.LINKED);
        PackagerGoggleInfo.addFluidPackagerToTooltip(tooltip, address, fluidlogistics$isManualOverrideLocked(), isLinkedToNetwork);
        return true;
    }

    private String findSignAddress() {
        if (level == null) {
            return "";
        }
        for (Direction side : Iterate.directions) {
            String address = getSign(side);
            if (address != null && !address.isBlank()) {
                return address;
            }
        }
        return "";
    }

    private boolean supportsFluidTarget(@Nullable BlockEntity target) {
        return !(target instanceof PortableFluidInterfaceBlockEntity);
    }

    private boolean supportsItemTarget(@Nullable BlockEntity target) {
        return target != null && !(target instanceof PortableFluidInterfaceBlockEntity);
    }

    @Override
    public void tick() {

        boolean fluidInsertionNeeded = animationTicks == 1 && animationInward
            && !level.isClientSide() && !pendingFluidsToInsert.isEmpty();

        super.tick();

        if (fluidInsertionNeeded) {
            performFluidInsertion();
            setChanged();
        }
    }

    private void performFluidInsertion() {
        IFluidHandler fluidHandler = fluidTarget.getInventory();

        if (fluidHandler == null || pendingFluidsToInsert.isEmpty()) {
            if (!previouslyUnwrapped.isEmpty()) {
                queuedExitingPackages.add(0, new BigItemStack(previouslyUnwrapped.copy(), 1));
            }
            pendingFluidsToInsert.clear();
            triggerStockCheck();
            return;
        }

        FluidStack fluid = pendingFluidsToInsert.getFirst();

        int accepted = fluidHandler.fill(fluid.copy(), FluidAction.SIMULATE);
        if (accepted != fluid.getAmount()) {
            if (!previouslyUnwrapped.isEmpty()) {
                queuedExitingPackages.add(0, new BigItemStack(previouslyUnwrapped.copy(), 1));
            }
            pendingFluidsToInsert.clear();
            triggerStockCheck();
            return;
        }

        fluidHandler.fill(fluid.copy(), FluidAction.EXECUTE);

        pendingFluidsToInsert.clear();
        triggerStockCheck();
    }

    @Override
    public InventorySummary getAvailableItems() {

        IFluidHandler fluidHandler = fluidTarget.getInventory();

        Map<FluidTypeKey, Integer> scannedSnapshot = scanAvailableFluids(fluidHandler);
        if (availableItems != null && scannedSnapshot.equals(availableFluidSnapshot))
            return availableItems;

        InventorySummary availableItems = new InventorySummary();
        for (Map.Entry<FluidTypeKey, Integer> entry : scannedSnapshot.entrySet()) {
            availableItems.add(createFluidDisplayItem(entry.getKey().template()), entry.getValue());
        }

        submitNewArrivals(this.availableItems, availableItems);
        availableFluidSnapshot = scannedSnapshot;
        this.availableItems = availableItems;
        return availableItems;
    }

    private Map<FluidTypeKey, Integer> scanAvailableFluids(@Nullable IFluidHandler fluidHandler) {
        if (fluidHandler == null)
            return Map.of();

        int tankCount = safeGetTanks(fluidHandler);
        if (tankCount == 0)
            return Map.of();

        Map<FluidTypeKey, Integer> scannedSnapshot = new LinkedHashMap<>();
        for (int tank = 0; tank < tankCount; tank++) {
            FluidStack fluid = safeGetFluidInTank(fluidHandler, tank);
            if (fluid.isEmpty())
                continue;

            FluidTypeKey key = FluidTypeKey.of(fluid);
            int amountToAdd = InfiniteFluidHandlerHelper.getStockAmount(fluidHandler, fluid);
            scannedSnapshot.merge(key, amountToAdd, FluidPackagerBlockEntity::mergeFluidAmounts);
        }

        return scannedSnapshot.isEmpty() ? Map.of() : scannedSnapshot;
    }

    private static int mergeFluidAmounts(int existingAmount, int addedAmount) {
        if (existingAmount >= BigItemStack.INF || addedAmount >= BigItemStack.INF)
            return BigItemStack.INF;
        return existingAmount + addedAmount;
    }

    private record FluidTypeKey(FluidStack template) {
        private static FluidTypeKey of(FluidStack stack) {
            return new FluidTypeKey(stack.copyWithAmount(1));
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof FluidTypeKey other
                && FluidStack.isSameFluidSameComponents(template, other.template);
        }

        @Override
        public int hashCode() {
            return Objects.hash(template.getFluid(), template.getComponentsPatch());
        }
    }

    private static int safeGetTanks(IFluidHandler handler) {
        try {
            return handler.getTanks();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    private static FluidStack safeGetFluidInTank(IFluidHandler handler, int tank) {
        try {
            return handler.getFluidInTank(tank);
        } catch (IllegalStateException e) {
            return FluidStack.EMPTY;
        }
    }

    private ItemStack createFluidDisplayItem(FluidStack fluid) {
        ItemStack tankStack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluidVirtual(tankStack, fluid.copyWithAmount(1));
        return tankStack;
    }

    private void submitNewArrivals(InventorySummary before, InventorySummary after) {
        if (after.isEmpty())
            return;

        Set<RequestPromiseQueue> promiseQueues = new HashSet<>();

        for (Direction d : Iterate.directions) {
            if (!level.isLoaded(worldPosition.relative(d)))
                continue;

            BlockState adjacentState = level.getBlockState(worldPosition.relative(d));
            if (AllBlocks.FACTORY_GAUGE.has(adjacentState)) {
                if (FactoryPanelBlock.connectedDirection(adjacentState) != d)
                    continue;
                if (!(level.getBlockEntity(worldPosition.relative(d)) instanceof FactoryPanelBlockEntity fpbe))
                    continue;
                if (!fpbe.restocker)
                    continue;
                for (FactoryPanelBehaviour behaviour : fpbe.panels.values()) {
                    if (!behaviour.isActive())
                        continue;
                    promiseQueues.add(behaviour.restockerPromises);
                }
            }

            if (AllBlocks.STOCK_LINK.has(adjacentState)) {
                if (PackagerLinkBlock.getConnectedDirection(adjacentState) != d)
                    continue;
                if (!(level.getBlockEntity(worldPosition.relative(d)) instanceof PackagerLinkBlockEntity plbe))
                    continue;
                UUID freqId = plbe.behaviour.freqId;
                if (!Create.LOGISTICS.hasQueuedPromises(freqId))
                    continue;
                promiseQueues.add(Create.LOGISTICS.getQueuedPromises(freqId));
            }
        }

        if (promiseQueues.isEmpty())
            return;

        if (before == null || before.isEmpty()) {
            for (RequestPromiseQueue queue : promiseQueues) {
                for (BigItemStack entry : after.getStacks()) {
                    queue.itemEnteredSystem(entry.stack, entry.count);
                }
            }
            return;
        }

        for (BigItemStack entry : after.getStacks())
            before.add(entry.stack, -entry.count);
        for (RequestPromiseQueue queue : promiseQueues) {
            for (BigItemStack entry : before.getStacks()) {
                if (entry.count < 0) {
                    queue.itemEnteredSystem(entry.stack, -entry.count);
                }
            }
        }
    }

    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {

        if (queuedRequests == null) {
            attemptToPackageFluid();
            return;
        }
        attemptToSendFluidRequest(queuedRequests);
    }

    public void attemptToPackageFluid() {
        if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
            return;

        IFluidHandler fluidHandler = fluidTarget.getInventory();
        if (fluidHandler == null)
            return;

        FluidStack extractedFluid = extractFluidFromTank(fluidHandler, Config.getFluidPerPackage());
        if (extractedFluid.isEmpty())
            return;

        ItemStack fluidPackage = createFluidPackage(extractedFluid);
        if (fluidPackage.isEmpty())
            return;

        sendComputerEvent(fluidPackage, "package_created");

        if (!signBasedAddress.isBlank()) {
            PackageItem.addAddress(fluidPackage, signBasedAddress);
        }

        heldBox = fluidPackage;
        animationInward = false;
        animationTicks = CYCLE;

        advancements.awardPlayer(AllAdvancements.PACKAGER);
        triggerStockCheck();
        notifyUpdate();
    }

    private FluidStack extractFluidFromTank(IFluidHandler handler, int maxAmount) {
        int tankCount = safeGetTanks(handler);
        for (int tank = 0; tank < tankCount; tank++) {
            FluidStack fluidInTank = safeGetFluidInTank(handler, tank);
            if (fluidInTank.isEmpty())
                continue;

            FluidStack infiniteDrain = InfiniteFluidHandlerHelper.drainFromInfiniteSource(handler, fluidInTank, maxAmount);
            if (!infiniteDrain.isEmpty())
                return infiniteDrain;

            int drainAmount = Math.min(maxAmount, fluidInTank.getAmount());
            FluidStack toDrain = fluidInTank.copyWithAmount(drainAmount);

            FluidStack simulated = handler.drain(toDrain, FluidAction.SIMULATE);
            if (simulated.isEmpty())
                continue;

            FluidStack drained = handler.drain(simulated, FluidAction.EXECUTE);
            if (!drained.isEmpty())
                return drained;
        }
        return FluidStack.EMPTY;
    }

    private ItemStack createFluidPackage(FluidStack fluid) {
        ItemStackHandler packageContents = new ItemStackHandler(PackageItem.SLOTS);
        ItemStack compressedTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(compressedTank, fluid.copy());
        packageContents.setStackInSlot(0, compressedTank);

        ItemStack fluidPackage = AllItems.createFluidPackage();
        fluidPackage.set(AllDataComponents.PACKAGE_CONTENTS,
            ItemHelper.containerContentsFromHandler(packageContents));
        return fluidPackage;
    }

    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {

        if (animationTicks > 0)
            return false;
        if (!FluidPackageItem.isFluidPackage(box))
            return false;

        Objects.requireNonNull(this.level);

        ItemStackHandler contents = PackageItem.getContents(box);
        List<ItemStack> items = ItemHelper.getNonEmptyStacks(contents);
        if (items.isEmpty())
            return true;

        IFluidHandler fluidHandler = fluidTarget.getInventory();
        if (fluidHandler == null) {
            return false;
        }

        FluidStack fluid = collectSinglePackageFluid(items);
        if (fluid.isEmpty())
            return true;

        int accepted = fluidHandler.fill(fluid.copy(), FluidAction.SIMULATE);
        if (accepted != fluid.getAmount()) {
            return false;
        }

        if (simulate) {
            return true;
        }

        pendingFluidsToInsert.clear();
        pendingFluidsToInsert.add(fluid);

        sendComputerEvent(box, "package_received");
        previouslyUnwrapped = box.copyWithCount(1);
        animationInward = true;
        animationTicks = CYCLE;
        notifyUpdate();

        return true;
    }

    private static FluidStack collectSinglePackageFluid(List<ItemStack> items) {
        FluidStack result = FluidStack.EMPTY;
        for (ItemStack item : items) {
            FluidStack fluid = CompressedTankItem.getFluid(item);
            if (fluid.isEmpty())
                continue;
            if (result.isEmpty()) {
                result = fluid.copy();
            } else {
                result.grow(fluid.getAmount());
            }
        }
        return result;
    }

    @Override
    public void updateSignAddress() {
        super.updateSignAddress();
        if (signBasedAddress.isBlank() && !clipboardAddress.isBlank()) {
            signBasedAddress = clipboardAddress;
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        clipboardAddress = compound.getString("FluidLogisticsClipboardAddress");
        manualOverrideLocked = compound.getBoolean("FluidLogisticsManualOverrideLocked");
        if (clientPacket)
            return;
        pendingFluidsToInsert = NBTHelper.readCompoundList(compound.getList("PendingFluids", Tag.TAG_COMPOUND),
                c -> CatnipCodecUtils.decode(FluidStack.OPTIONAL_CODEC, registries, c).orElse(FluidStack.EMPTY));
        if (compound.contains("LastSummary"))
            availableItems = CatnipCodecUtils.decodeOrNull(InventorySummary.CODEC, registries, compound.getCompound("LastSummary"));
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putString("FluidLogisticsClipboardAddress", clipboardAddress);
        compound.putBoolean("FluidLogisticsManualOverrideLocked", manualOverrideLocked);
        if (clientPacket)
            return;
        compound.put("PendingFluids", NBTHelper.writeCompoundList(pendingFluidsToInsert, fs -> {
            if (CatnipCodecUtils.encode(FluidStack.OPTIONAL_CODEC, registries, fs).orElse(new CompoundTag()) instanceof CompoundTag ct)
                return ct;
            return new CompoundTag();
        }));
        if (availableItems != null)
            compound.put("LastSummary", CatnipCodecUtils.encode(InventorySummary.CODEC, registries, availableItems).orElseThrow());
    }

    @Override
    public void clearContent() {
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        queuedExitingPackages.clear();
        pendingFluidsToInsert.clear();
    }

    @Override
    public net.createmod.catnip.data.Pair<PackagerBlockEntity, PackagingRequest> processFluidRequest(
            ItemStack stack, int amount, String address, int linkIndex,
            org.apache.commons.lang3.mutable.MutableBoolean finalLink, int orderId,
            @Nullable PackageOrderWithCrafts context,
            @Nullable IdentifiedInventory ignoredHandler) {


        if (isTargetingSameInventory(ignoredHandler))
            return null;

        if (!(stack.getItem() instanceof CompressedTankItem))
            return null;

        FluidStack requestedFluid = CompressedTankItem.getFluid(stack);

        getAvailableItems();
        int availableAmount = availableFluidSnapshot.getOrDefault(FluidTypeKey.of(requestedFluid), 0);
        if (availableAmount == 0)
            return null;

        int toWithdraw = availableAmount >= BigItemStack.INF ? amount : Math.min(amount, availableAmount);

        return net.createmod.catnip.data.Pair.of(this,
            PackagingRequest.create(
                stack, toWithdraw, address, linkIndex, finalLink, 0, orderId, context));
    }

    @Override
    public void attemptToSendFluidRequest(List<PackagingRequest> queuedRequests) {
        if (queuedRequests == null || queuedRequests.isEmpty())
            return;

        IFluidHandler fluidHandler = fluidTarget.getInventory();
        if (fluidHandler == null) {
            queuedRequests.remove(0);
            return;
        }

        PackagingRequest nextRequest = queuedRequests.get(0);
        ItemStack requestedStack = nextRequest.item();

        if (!(requestedStack.getItem() instanceof CompressedTankItem)) {
            queuedRequests.remove(0);
            return;
        }

        FluidStack requestedFluid = CompressedTankItem.getFluid(requestedStack);

        int remainingCount = nextRequest.getCount();
        String fixedAddress = nextRequest.address();
        int fixedOrderId = nextRequest.orderId();
        int linkIndexInOrder = nextRequest.linkIndex();
        boolean finalLinkInOrder = nextRequest.finalLink().booleanValue();
        int packageIndexAtLink = nextRequest.packageCounter().getAndIncrement();
        boolean finalPackageAtLink = false;
        PackageOrderWithCrafts orderContext = nextRequest.context();

        int toExtract = Math.min(remainingCount, Config.getFluidPerPackage());

        FluidStack extractedFluid = extractSpecificFluidFromTank(fluidHandler, requestedFluid, toExtract);
        if (extractedFluid.isEmpty()) {
            queuedRequests.remove(0);
            return;
        }

        ItemStack fluidPackage = createFluidPackage(extractedFluid);
        if (fluidPackage.isEmpty()) {
            queuedRequests.remove(0);
            return;
        }

        sendComputerEvent(fluidPackage, "package_created");

        PackageItem.clearAddress(fluidPackage);
        if (fixedAddress != null)
            PackageItem.addAddress(fluidPackage, fixedAddress);

        int extractedAmount = extractedFluid.getAmount();
        nextRequest.subtract(extractedAmount);

        if (nextRequest.isEmpty()) {
            finalPackageAtLink = true;
            queuedRequests.remove(0);
            if (!queuedRequests.isEmpty()) {
                PackagingRequest followingRequest = queuedRequests.get(0);
                if (sameFragmentSequence(fixedAddress, fixedOrderId, linkIndexInOrder, followingRequest)) {
                    followingRequest.packageCounter()
                        .setValue(packageIndexAtLink + 1);
                    finalPackageAtLink = false;
                }
            }
        }

        PackageItem.setOrder(fluidPackage, fixedOrderId, linkIndexInOrder, finalLinkInOrder,
            packageIndexAtLink, finalPackageAtLink, orderContext);

        if (!heldBox.isEmpty() || animationTicks != 0) {
            queuedExitingPackages.add(new BigItemStack(fluidPackage, 1));
            return;
        }

        heldBox = fluidPackage;
        animationInward = false;
        animationTicks = CYCLE;

        advancements.awardPlayer(AllAdvancements.PACKAGER);
        triggerStockCheck();
        notifyUpdate();
    }

    private void sendComputerEvent(ItemStack itemStack, String eventName) {
        if (computerBehaviour != null)
            computerBehaviour.prepareComputerEvent(new PackageEvent(itemStack, eventName));
    }

    private FluidStack extractSpecificFluidFromTank(IFluidHandler handler, FluidStack targetFluid, int maxAmount) {
        int tankCount = safeGetTanks(handler);
        for (int tank = 0; tank < tankCount; tank++) {
            FluidStack fluidInTank = safeGetFluidInTank(handler, tank);
            if (fluidInTank.isEmpty())
                continue;
            if (!FluidStack.isSameFluidSameComponents(fluidInTank, targetFluid))
                continue;

            FluidStack infiniteDrain = InfiniteFluidHandlerHelper.drainFromInfiniteSource(handler, fluidInTank, maxAmount);
            if (!infiniteDrain.isEmpty())
                return infiniteDrain;

            int drainAmount = Math.min(maxAmount, fluidInTank.getAmount());
            FluidStack toDrain = fluidInTank.copyWithAmount(drainAmount);

            FluidStack simulated = handler.drain(toDrain, FluidAction.SIMULATE);
            if (simulated.isEmpty())
                continue;

            FluidStack drained = handler.drain(simulated, FluidAction.EXECUTE);
            if (!drained.isEmpty())
                return drained;
        }
        return FluidStack.EMPTY;
    }

    private static boolean sameFragmentSequence(@Nullable String address, int orderId, int linkIndex,
            PackagingRequest request) {
        return Objects.equals(address, request.address())
            && orderId == request.orderId()
            && linkIndex == request.linkIndex();
    }

    @Override
    public void flashFluidLink() {
        flashLink();
    }

    @Override
    public boolean fluidlogistics$isManualOverrideLocked() {
        return manualOverrideLocked;
    }

    @Override
    public void fluidlogistics$setManualOverrideLocked(boolean locked) {
        manualOverrideLocked = locked;
    }

    @Override
    public String fluidlogistics$getClipboardAddress() {
        return clipboardAddress;
    }

    @Override
    public void fluidlogistics$setClipboardAddress(String address) {
        clipboardAddress = address == null ? "" : address;
    }

    @Override
    public boolean isFluidPackagerTooBusy(RequestType type) {
        return isTooBusyFor(type);
    }

    @Override
    @Nullable
    public IdentifiedInventory getIdentifiedInventory() {
        if (targetInventory == null)
            return null;
        return targetInventory.getIdentifiedInventory();
    }
}
