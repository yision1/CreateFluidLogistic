package com.yision.fluidlogistics.block.FluidPackager;

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
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import com.simibubi.create.compat.computercraft.AbstractComputerBehaviour;
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
import com.simibubi.create.content.logistics.packagerLink.WiFiEffectPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.TankManipulationBehaviour;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.nbt.NBTHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import com.yision.fluidlogistics.api.IFluidPackager;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.item.FluidPackageItem;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.InfiniteFluidHandlerHelper;
import com.yision.fluidlogistics.util.IPackagerOverrideData;

import org.apache.commons.lang3.mutable.MutableBoolean;

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

    private boolean supportsFluidTarget(@Nullable BlockEntity target) {
        return !(target instanceof PortableFluidInterfaceBlockEntity);
    }

    private boolean supportsItemTarget(@Nullable BlockEntity target) {
        return target != null && !(target instanceof PortableFluidInterfaceBlockEntity);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (!Config.isAdvancedLogisticsNetworkEnabled()) {
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }

    // ---- Tick: only supplement fluid insertion at animation end ----

    @Override
    public void tick() {
        if (!Config.isAdvancedLogisticsNetworkEnabled()) {
            return;
        }

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

        FluidStack fluid = pendingFluidsToInsert.get(0);

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

    // ---- Stock check ----

    @Override
    public void triggerStockCheck() {
        getAvailableItems();
    }

    @Override
    public InventorySummary getAvailableItems() {
        return getAvailableFluidItems();
    }

    @Override
    public InventorySummary getAvailableItems(boolean scanInputSlots) {
        return getAvailableFluidItems();
    }

    private InventorySummary getAvailableFluidItems() {
        if (!Config.isAdvancedLogisticsNetworkEnabled()) return new InventorySummary();
        IFluidHandler fluidHandler = fluidTarget.getInventory();

        Map<FluidTypeKey, Integer> scannedSnapshot = scanAvailableFluids(fluidHandler);
        if (availableItems != null && scannedSnapshot.equals(availableFluidSnapshot))
            return availableItems;

        InventorySummary newAvailableItems = new InventorySummary();
        for (Map.Entry<FluidTypeKey, Integer> entry : scannedSnapshot.entrySet()) {
            newAvailableItems.add(createFluidDisplayItem(entry.getKey().template()), entry.getValue());
        }

        submitNewArrivals(availableItems, newAvailableItems);
        availableFluidSnapshot = scannedSnapshot;
        availableItems = newAvailableItems;
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
            return new FluidTypeKey(FluidHelper.copyStackWithAmount(stack, 1));
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof FluidTypeKey other
                && template.isFluidEqual(other.template)
                && FluidStack.areFluidStackTagsEqual(template, other.template);
        }

        @Override
        public int hashCode() {
            return Objects.hash(template.getFluid(), template.getTag());
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
        CompressedTankItem.setFluidVirtual(tankStack, FluidHelper.copyStackWithAmount(fluid, 1));
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

    // ---- Packaging & unpacking ----

    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        if (queuedRequests == null) {
            attemptToPackageFluid();
            return;
        }
        attemptToSendFluidRequest(queuedRequests);
    }

    public void attemptToPackageFluid() {
        if (!Config.isAdvancedLogisticsNetworkEnabled()) return;
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
            FluidStack toDrain = FluidHelper.copyStackWithAmount(fluidInTank, drainAmount);
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
        int tankCapacity = CompressedTankItem.getCapacity();
        int maxTanks = PackageItem.SLOTS;
        int tanksCreated = 0;

        FluidStack remainingFluid = fluid.copy();

        while (!remainingFluid.isEmpty() && tanksCreated < maxTanks) {
            int fluidForTank = Math.min(remainingFluid.getAmount(), tankCapacity);
            FluidStack tankFluid = FluidHelper.copyStackWithAmount(remainingFluid, fluidForTank);

            ItemStack compressedTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluid(compressedTank, tankFluid);
            ItemHandlerHelper.insertItemStacked(packageContents, compressedTank, false);

            remainingFluid.shrink(fluidForTank);
            tanksCreated++;
        }

        ItemStack fluidPackage = AllItems.getRandomFluidPackage();
        CompoundTag compound = new CompoundTag();
        compound.put("Items", packageContents.serializeNBT());
        fluidPackage.setTag(compound);
        return fluidPackage;
    }

    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        if (!Config.isAdvancedLogisticsNetworkEnabled()) return false;
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
        previouslyUnwrapped = box.copy();
        previouslyUnwrapped.setCount(1);
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

    // ---- NBT read/write ----

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        if (!Config.isAdvancedLogisticsNetworkEnabled()) {
            clipboardAddress = "";
            manualOverrideLocked = false;
            pendingFluidsToInsert.clear();
            availableItems = null;
            availableFluidSnapshot = Map.of();
            return;
        }
        clipboardAddress = compound.getString("FluidLogisticsClipboardAddress");
        manualOverrideLocked = compound.getBoolean("FluidLogisticsManualOverrideLocked");
        if (clientPacket)
            return;
        pendingFluidsToInsert = NBTHelper.readCompoundList(
            compound.getList("PendingFluids", Tag.TAG_COMPOUND),
            FluidStack::loadFluidStackFromNBT);
        if (compound.contains("LastSummary"))
            availableItems = InventorySummary.read(compound.getCompound("LastSummary"));
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        if (!Config.isAdvancedLogisticsNetworkEnabled()) {
            return;
        }
        compound.putString("FluidLogisticsClipboardAddress", clipboardAddress);
        compound.putBoolean("FluidLogisticsManualOverrideLocked", manualOverrideLocked);
        if (clientPacket)
            return;
        compound.put("PendingFluids", NBTHelper.writeCompoundList(
            pendingFluidsToInsert, fs -> fs.writeToNBT(new CompoundTag())));
        if (availableItems != null)
            compound.put("LastSummary", availableItems.write());
    }

    @Override
    public void clearContent() {
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        queuedExitingPackages.clear();
        pendingFluidsToInsert.clear();
    }

    // ---- Sign address with clipboard fallback ----

    @Override
    public void updateSignAddress() {
        super.updateSignAddress();
        if (signBasedAddress.isBlank() && !clipboardAddress.isBlank()) {
            signBasedAddress = clipboardAddress;
        }
    }

    // ---- Goggle tooltip ----

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!Config.isAdvancedLogisticsNetworkEnabled()) {
            return false;
        }

        String address = signBasedAddress;
        if (level != null && level.isClientSide) {
            String scannedAddress = findSignAddress();
            if (!scannedAddress.isBlank())
                address = scannedAddress;
            else if (address.isBlank())
                address = clipboardAddress;
        } else if (address.isBlank()) {
            address = clipboardAddress;
        }

        BlockState state = getBlockState();
        boolean isLinkedToNetwork = state.hasProperty(FluidPackagerBlock.LINKED)
            && state.getValue(FluidPackagerBlock.LINKED);
        com.yision.fluidlogistics.goggle.PackagerGoggleInfo.addFluidPackagerToTooltip(
            tooltip, address, manualOverrideLocked, isLinkedToNetwork);
        return true;
    }

    private String findSignAddress() {
        for (Direction side : Iterate.directions) {
            String address = readSignAddress(side);
            if (!address.isBlank()) {
                return address;
            }
        }
        return "";
    }

    private String readSignAddress(Direction side) {
        if (level == null) {
            return "";
        }

        BlockEntity blockEntity = level.getBlockEntity(getBlockPos().relative(side));
        if (!(blockEntity instanceof SignBlockEntity sign)) {
            return "";
        }

        for (boolean front : Iterate.trueAndFalse) {
            SignText text = sign.getText(front);
            StringBuilder address = new StringBuilder();
            for (Component component : text.getMessages(false)) {
                String string = component.getString();
                if (!string.isBlank()) {
                    address.append(string.trim()).append(' ');
                }
            }
            if (address.length() > 0) {
                return address.toString().trim();
            }
        }

        return "";
    }

    // ---- Computer events ----

    private void sendComputerEvent(ItemStack itemStack, String eventName) {
        if (computerBehaviour != null)
            computerBehaviour.prepareComputerEvent(new PackageEvent(itemStack, eventName));
    }

    // ---- IFluidPackager implementations ----

    @Override
    public Pair<IFluidPackager, PackagingRequest> processFluidRequest(
            ItemStack stack, int amount, String address, int linkIndex,
            MutableBoolean finalLink, int orderId,
            @Nullable PackageOrderWithCrafts context,
            @Nullable IdentifiedInventory ignoredHandler) {
        if (!Config.isAdvancedLogisticsNetworkEnabled()) return null;

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

        return Pair.of(this,
            PackagingRequest.create(
                stack, toWithdraw, address, linkIndex, finalLink, 0, orderId, context));
    }

    @Override
    public void attemptToSendFluidRequest(java.util.List<PackagingRequest> queuedRequests) {
        if (!Config.isAdvancedLogisticsNetworkEnabled()) return;
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

    private FluidStack extractSpecificFluidFromTank(IFluidHandler handler, FluidStack targetFluid, int maxAmount) {
        int tankCount = safeGetTanks(handler);
        for (int tank = 0; tank < tankCount; tank++) {
            FluidStack fluidInTank = safeGetFluidInTank(handler, tank);
            if (fluidInTank.isEmpty())
                continue;
            if (!(fluidInTank.isFluidEqual(targetFluid) && FluidStack.areFluidStackTagsEqual(fluidInTank, targetFluid)))
                continue;

            FluidStack infiniteDrain = InfiniteFluidHandlerHelper.drainFromInfiniteSource(handler, fluidInTank, maxAmount);
            if (!infiniteDrain.isEmpty())
                return infiniteDrain;

            int drainAmount = Math.min(maxAmount, fluidInTank.getAmount());
            FluidStack toDrain = FluidHelper.copyStackWithAmount(fluidInTank, drainAmount);
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

    // ---- IPackagerOverrideData ----

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
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(1);
    }
}
