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
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank;
import com.simibubi.create.compat.computercraft.AbstractComputerBehaviour;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.packagerLink.WiFiEffectPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.TankManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.fluidlogistics.api.IFluidPackager;
import com.yision.fluidlogistics.advancement.AllTriggers;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.goggle.PackagerGoggleInfo;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.item.FluidPackageItem;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.FluidInsertionHelper;
import com.yision.fluidlogistics.util.IPackagerOverrideData;

import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.BlockFace;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public class FluidPackagerBlockEntity extends SmartBlockEntity implements Clearable, IFluidPackager, IPackagerOverrideData, IHaveGoggleInformation {

    public static final int CYCLE = 20;

    public boolean redstonePowered;
    public int buttonCooldown;
    public String signBasedAddress;
    public String clipboardAddress;

    public TankManipulationBehaviour fluidTarget;
    public InvManipulationBehaviour itemTarget;
    public ItemStack heldBox;
    public ItemStack previouslyUnwrapped;

    public List<BigItemStack> queuedExitingPackages;
    public final FluidPackagerItemHandler inventory;

    public int animationTicks;
    public boolean animationInward;
    public List<FluidStack> pendingFluidsToInsert;

    public AbstractComputerBehaviour computerBehaviour;
    public Boolean hasCustomComputerAddress;
    public String customComputerAddress;
    private boolean manualOverrideLocked;

    private InventorySummary availableItems;
    private Map<FluidTypeKey, Integer> availableFluidSnapshot;
    private VersionedInventoryTrackerBehaviour invVersionTracker;
    private AdvancementBehaviour advancements;

    public FluidPackagerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        redstonePowered = state.getOptionalValue(FluidPackagerBlock.POWERED).orElse(false);
        heldBox = ItemStack.EMPTY;
        previouslyUnwrapped = ItemStack.EMPTY;
        inventory = new FluidPackagerItemHandler(this);
        animationTicks = 0;
        animationInward = true;
        queuedExitingPackages = new LinkedList<>();
        pendingFluidsToInsert = new LinkedList<>();
        signBasedAddress = "";
        clipboardAddress = "";
        customComputerAddress = "";
        hasCustomComputerAddress = false;
        manualOverrideLocked = false;
        availableFluidSnapshot = Map.of();
        buttonCooldown = 0;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            AllBlockEntities.FLUID_PACKAGER.get(),
            (be, context) -> be.inventory
        );
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(fluidTarget = new TankManipulationBehaviour(this, InterfaceProvider.oppositeOfBlockFacing())
            .withFilter(this::supportsBlockEntity));
        behaviours.add(itemTarget = new InvManipulationBehaviour(this, InterfaceProvider.oppositeOfBlockFacing())
            .withFilter(this::supportsBlockEntity));
        behaviours.add(invVersionTracker = new VersionedInventoryTrackerBehaviour(this));
        behaviours.add(advancements = new AdvancementBehaviour(this, AllAdvancements.PACKAGER));
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

    private boolean supportsBlockEntity(BlockEntity target) {
        return target != null && !(target instanceof PortableFluidInterfaceBlockEntity);
    }

    @Nullable
    private BlockEntity getFluidTargetBlockEntity() {
        if (level == null) {
            return null;
        }
        Direction facing = getBlockState().getOptionalValue(FluidPackagerBlock.FACING).orElse(Direction.UP);
        BlockPos targetPos = worldPosition.relative(facing.getOpposite());
        return level.getBlockEntity(targetPos);
    }

    @Override
    public void initialize() {
        super.initialize();
        recheckIfLinksPresent();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (computerBehaviour != null)
            computerBehaviour.removePeripheral();
    }

    @Override
    public void tick() {
        super.tick();

        if (buttonCooldown > 0)
            buttonCooldown--;

        if (animationTicks == 0) {
            previouslyUnwrapped = ItemStack.EMPTY;

            if (!level.isClientSide() && !queuedExitingPackages.isEmpty() && heldBox.isEmpty()) {
                BigItemStack entry = queuedExitingPackages.get(0);
                heldBox = entry.stack.copy();

                entry.count--;
                if (entry.count <= 0)
                    queuedExitingPackages.remove(0);

                animationInward = false;
                animationTicks = CYCLE;
                notifyUpdate();
            }

            return;
        }

        if (level.isClientSide) {
            if (animationTicks == CYCLE - (animationInward ? 5 : 1))
                AllSoundEvents.PACKAGER.playAt(level, worldPosition, 1, 1, true);
            if (animationTicks == (animationInward ? 1 : 5))
                level.playLocalSound(worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.25f, 0.75f, true);
        }

        animationTicks--;

        if (animationTicks == 0 && !level.isClientSide()) {
            if (animationInward && !pendingFluidsToInsert.isEmpty()) {
                IFluidHandler fluidHandler = fluidTarget.getInventory();
                BlockEntity targetBlockEntity = getFluidTargetBlockEntity();
                boolean insertedAll = fluidHandler != null
                    && FluidInsertionHelper.canAcceptAll(targetBlockEntity, fluidHandler, pendingFluidsToInsert);

                if (insertedAll) {
                    for (FluidStack fluid : pendingFluidsToInsert) {
                        int filled = fluidHandler.fill(fluid.copy(), FluidAction.EXECUTE);
                        if (filled != fluid.getAmount()) {
                            insertedAll = false;
                            break;
                        }
                    }
                }

                if (!insertedAll && !previouslyUnwrapped.isEmpty()) {
                    queuedExitingPackages.add(0, new BigItemStack(previouslyUnwrapped.copy(), 1));
                }

                pendingFluidsToInsert.clear();
                triggerStockCheck();
            }
            wakeTheFrogs();
            setChanged();
        }
    }

    public void triggerStockCheck() {
        getAvailableItems();
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

        boolean isCreativeHandler = fluidHandler instanceof CreativeSmartFluidTank;
        int tankCount = safeGetTanks(fluidHandler);
        if (tankCount == 0)
            return Map.of();

        Map<FluidTypeKey, Integer> scannedSnapshot = new LinkedHashMap<>();
        for (int tank = 0; tank < tankCount; tank++) {
            FluidStack fluid = safeGetFluidInTank(fluidHandler, tank);
            if (fluid.isEmpty())
                continue;

            FluidTypeKey key = FluidTypeKey.of(fluid);
            int amountToAdd = isCreativeHandler ? BigItemStack.INF : fluid.getAmount();
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
    public void lazyTick() {
        super.lazyTick();
        if (level.isClientSide())
            return;
        recheckIfLinksPresent();
        if (!redstonePowered)
            return;
        redstonePowered = getBlockState().getOptionalValue(FluidPackagerBlock.POWERED).orElse(false);
        if (!redstoneModeActive())
            return;
        updateSignAddress();
        attemptToPackageFluid();
    }

    public void recheckIfLinksPresent() {
        if (level.isClientSide())
            return;
        BlockState blockState = getBlockState();
        if (!blockState.hasProperty(FluidPackagerBlock.LINKED))
            return;
        boolean shouldBeLinked = getLinkPos() != null;
        boolean isLinked = blockState.getValue(FluidPackagerBlock.LINKED);
        if (shouldBeLinked == isLinked)
            return;
        level.setBlockAndUpdate(worldPosition, blockState.cycle(FluidPackagerBlock.LINKED));
    }

    public boolean redstoneModeActive() {
        return !getBlockState().getOptionalValue(FluidPackagerBlock.LINKED).orElse(false);
    }

    private BlockPos getLinkPos() {
        for (Direction d : Iterate.directions) {
            BlockState adjacentState = level.getBlockState(worldPosition.relative(d));
            if (!AllBlocks.STOCK_LINK.has(adjacentState))
                continue;
            if (PackagerLinkBlock.getConnectedDirection(adjacentState) != d)
                continue;
            return worldPosition.relative(d);
        }
        return null;
    }

    public void flashLink() {
        for (Direction d : Iterate.directions) {
            BlockState adjacentState = level.getBlockState(worldPosition.relative(d));
            if (!AllBlocks.STOCK_LINK.has(adjacentState))
                continue;
            if (PackagerLinkBlock.getConnectedDirection(adjacentState) != d)
                continue;
            WiFiEffectPacket.send(level, worldPosition.relative(d));
            return;
        }
    }

    public boolean isTooBusyFor(RequestType type) {
        int queue = queuedExitingPackages.size();
        return queue >= switch (type) {
            case PLAYER -> 50;
            case REDSTONE -> 20;
            case RESTOCK -> 10;
        };
    }

    public void activate() {
        redstonePowered = true;
        setChanged();

        recheckIfLinksPresent();
        if (!redstoneModeActive())
            return;

        updateSignAddress();
        attemptToPackageFluid();

        if (buttonCooldown <= 0) {
            buttonCooldown = 40;
        }
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
        awardFluidPackageCreatedAdvancement();
        triggerStockCheck();
        notifyUpdate();
    }

    private void awardFluidPackageCreatedAdvancement() {
        if (level == null || level.isClientSide())
            return;
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, 
            new AABB(worldPosition).inflate(16));
        for (ServerPlayer player : players) {
            AllTriggers.FLUID_PACKAGE_CREATED.trigger(player);
        }
    }

    private FluidStack extractFluidFromTank(IFluidHandler handler, int maxAmount) {
        boolean isCreativeHandler = handler instanceof CreativeSmartFluidTank;
        
        int tankCount = safeGetTanks(handler);
        for (int tank = 0; tank < tankCount; tank++) {
            FluidStack fluidInTank = safeGetFluidInTank(handler, tank);
            if (fluidInTank.isEmpty())
                continue;

            if (isCreativeHandler) {
                return fluidInTank.copyWithAmount(maxAmount);
            }
            
            int drainAmount = Math.min(maxAmount, fluidInTank.getAmount());
            FluidStack toDrain = fluidInTank.copyWithAmount(drainAmount);
            FluidStack drained = handler.drain(toDrain, FluidAction.EXECUTE);
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
            FluidStack tankFluid = remainingFluid.copyWithAmount(fluidForTank);

            ItemStack compressedTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluid(compressedTank, tankFluid);
            ItemHandlerHelper.insertItemStacked(packageContents, compressedTank, false);

            remainingFluid.shrink(fluidForTank);
            tanksCreated++;
        }

        ItemStack fluidPackage = AllItems.getRandomFluidPackage();
        fluidPackage.set(com.simibubi.create.AllDataComponents.PACKAGE_CONTENTS, 
            com.simibubi.create.foundation.item.ItemHelper.containerContentsFromHandler(packageContents));
        return fluidPackage;
    }

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
        BlockEntity targetBlockEntity = getFluidTargetBlockEntity();

        List<FluidStack> packageFluids = collectPackageFluids(items);

        if (!packageFluids.isEmpty() && !FluidInsertionHelper.canAcceptAll(targetBlockEntity, fluidHandler, packageFluids)) {
            return false;
        }

        if (simulate) {
            return true;
        }

        pendingFluidsToInsert.clear();
        pendingFluidsToInsert.addAll(packageFluids);

        sendComputerEvent(box, "package_received");
        previouslyUnwrapped = box.copyWithCount(1);
        animationInward = true;
        animationTicks = CYCLE;
        notifyUpdate();

        return true;
    }

    private static List<FluidStack> collectPackageFluids(List<ItemStack> items) {
        List<FluidStack> packageFluids = new LinkedList<>();
        for (ItemStack item : items) {
            FluidStack fluid = CompressedTankItem.getFluid(item);
            if (!fluid.isEmpty()) {
                packageFluids.add(fluid.copy());
            }
        }
        return packageFluids;
    }

    public void updateSignAddress() {
        signBasedAddress = "";
        for (Direction side : Iterate.directions) {
            String address = getSign(side);
            if (address == null || address.isBlank())
                continue;
            signBasedAddress = address;
        }
        if (signBasedAddress.isBlank() && !clipboardAddress.isBlank()) {
            signBasedAddress = clipboardAddress;
        }
        if (hasAttachedComputer() && hasCustomComputerAddress) {
            signBasedAddress = customComputerAddress;
        } else {
            hasCustomComputerAddress = false;
        }
    }

    protected String getSign(Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(side));
        if (!(blockEntity instanceof SignBlockEntity sign))
            return null;
        for (boolean front : Iterate.trueAndFalse) {
            SignText text = sign.getText(front);
            String address = "";
            for (Component component : text.getMessages(false)) {
                String string = component.getString();
                if (!string.isBlank())
                    address += string.trim() + " ";
            }
            if (!address.isBlank())
                return address.trim();
        }
        return null;
    }

    protected void wakeTheFrogs() {
        if (level.getBlockEntity(worldPosition.relative(Direction.UP)) instanceof FrogportBlockEntity port)
            port.tryPullingFromOwnAndAdjacentInventories();
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        redstonePowered = compound.getBoolean("Active");
        animationInward = compound.getBoolean("AnimationInward");
        animationTicks = compound.getInt("AnimationTicks");
        signBasedAddress = compound.getString("SignAddress");
        clipboardAddress = compound.getString("FluidLogisticsClipboardAddress");
        customComputerAddress = compound.getString("ComputerAddress");
        hasCustomComputerAddress = compound.getBoolean("HasComputerAddress");
        manualOverrideLocked = compound.getBoolean("FluidLogisticsManualOverrideLocked");
        heldBox = ItemStack.parseOptional(registries, compound.getCompound("HeldBox"));
        previouslyUnwrapped = ItemStack.parseOptional(registries, compound.getCompound("InsertedBox"));
        if (clientPacket)
            return;
        queuedExitingPackages = NBTHelper.readCompoundList(compound.getList("QueuedExitingPackages", Tag.TAG_COMPOUND),
                c -> CatnipCodecUtils.decode(BigItemStack.CODEC, registries, c).orElseThrow());
        pendingFluidsToInsert = NBTHelper.readCompoundList(compound.getList("PendingFluids", Tag.TAG_COMPOUND),
                c -> CatnipCodecUtils.decode(FluidStack.OPTIONAL_CODEC, registries, c).orElse(FluidStack.EMPTY));
        if (compound.contains("LastSummary"))
            availableItems = CatnipCodecUtils.decodeOrNull(InventorySummary.CODEC, registries, compound.getCompound("LastSummary"));
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putBoolean("Active", redstonePowered);
        compound.putBoolean("AnimationInward", animationInward);
        compound.putInt("AnimationTicks", animationTicks);
        compound.putString("SignAddress", signBasedAddress);
        compound.putString("FluidLogisticsClipboardAddress", clipboardAddress);
        compound.putString("ComputerAddress", customComputerAddress);
        compound.putBoolean("HasComputerAddress", hasCustomComputerAddress);
        compound.putBoolean("FluidLogisticsManualOverrideLocked", manualOverrideLocked);
        compound.put("HeldBox", heldBox.saveOptional(registries));
        compound.put("InsertedBox", previouslyUnwrapped.saveOptional(registries));
        if (clientPacket)
            return;
        compound.put("QueuedExitingPackages", NBTHelper.writeCompoundList(queuedExitingPackages, bis -> {
            if (CatnipCodecUtils.encode(BigItemStack.CODEC, registries, bis).orElse(new CompoundTag()) instanceof CompoundTag ct)
                return ct;
            return new CompoundTag();
        }));
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
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inventory);
        queuedExitingPackages.forEach(bigStack -> {
            for (int i = 0; i < bigStack.count; i++)
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), bigStack.stack.copy());
        });
        queuedExitingPackages.clear();
    }

    public float getTrayOffset(float partialTicks) {
        float tickCycle = animationInward ? animationTicks - partialTicks : animationTicks - 5 - partialTicks;
        float progress = Mth.clamp(tickCycle / (CYCLE - 5) * 2 - 1, -1, 1);
        progress = 1 - progress * progress;
        return progress * progress;
    }

    public ItemStack getRenderedBox() {
        if (animationInward)
            return animationTicks <= CYCLE / 2 ? ItemStack.EMPTY : previouslyUnwrapped;
        return animationTicks >= CYCLE / 2 ? ItemStack.EMPTY : heldBox;
    }

    @Override
    public boolean isTargetingSameInventory(@Nullable com.simibubi.create.content.logistics.packager.IdentifiedInventory inventory) {
        if (inventory == null)
            return false;

        IItemHandler targetHandler = this.itemTarget.getInventory();
        if (targetHandler == null)
            return false;

        if (inventory.identifier() != null) {
            BlockFace face = this.itemTarget.getTarget().getOpposite();
            return inventory.identifier().contains(face);
        } else {
            return isSameInventoryFallback(targetHandler, inventory.handler());
        }
    }

    private static boolean isSameInventoryFallback(IItemHandler first, IItemHandler second) {
        if (first == second)
            return true;

        for (int i = 0; i < second.getSlots(); i++) {
            ItemStack stackInSlot = second.getStackInSlot(i);
            if (stackInSlot.isEmpty())
                continue;
            for (int j = 0; j < first.getSlots(); j++)
                if (stackInSlot == first.getStackInSlot(j))
                    return true;
            break;
        }

        return false;
    }

    @Override
    public net.createmod.catnip.data.Pair<IFluidPackager, com.simibubi.create.content.logistics.packager.PackagingRequest> processFluidRequest(
            ItemStack stack, int amount, String address, int linkIndex,
            org.apache.commons.lang3.mutable.MutableBoolean finalLink, int orderId,
            @Nullable PackageOrderWithCrafts context,
            @Nullable com.simibubi.create.content.logistics.packager.IdentifiedInventory ignoredHandler) {
        
        if (isTargetingSameInventory(ignoredHandler))
            return null;

        if (!(stack.getItem() instanceof CompressedTankItem))
            return null;

        FluidStack requestedFluid = CompressedTankItem.getFluid(stack);
        if (requestedFluid.isEmpty())
            return null;

        getAvailableItems();
        int availableAmount = availableFluidSnapshot.getOrDefault(FluidTypeKey.of(requestedFluid), 0);
        if (availableAmount == 0)
            return null;

        int toWithdraw = availableAmount >= BigItemStack.INF ? amount : Math.min(amount, availableAmount);
        
        return net.createmod.catnip.data.Pair.of(this,
            com.simibubi.create.content.logistics.packager.PackagingRequest.create(
                stack, toWithdraw, address, linkIndex, finalLink, 0, orderId, context));
    }

    @Override
    public void attemptToSendFluidRequest(java.util.List<com.simibubi.create.content.logistics.packager.PackagingRequest> queuedRequests) {
        if (queuedRequests == null || queuedRequests.isEmpty())
            return;

        IFluidHandler fluidHandler = fluidTarget.getInventory();
        if (fluidHandler == null) {
            queuedRequests.remove(0);
            return;
        }

        com.simibubi.create.content.logistics.packager.PackagingRequest nextRequest = queuedRequests.get(0);
        ItemStack requestedStack = nextRequest.item();
        
        if (!(requestedStack.getItem() instanceof CompressedTankItem)) {
            queuedRequests.remove(0);
            return;
        }

        FluidStack requestedFluid = CompressedTankItem.getFluid(requestedStack);
        if (requestedFluid.isEmpty()) {
            queuedRequests.remove(0);
            return;
        }

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
        awardFluidPackageCreatedAdvancement();
        triggerStockCheck();
        notifyUpdate();
    }

    private void sendComputerEvent(ItemStack itemStack, String eventName) {
        if (computerBehaviour != null)
            computerBehaviour.prepareComputerEvent(new PackageEvent(itemStack, eventName));
    }

    private boolean hasAttachedComputer() {
        return computerBehaviour != null && computerBehaviour.hasAttachedComputer();
    }

    private FluidStack extractSpecificFluidFromTank(IFluidHandler handler, FluidStack targetFluid, int maxAmount) {
        boolean isCreativeHandler = handler instanceof CreativeSmartFluidTank;
        
        int tankCount = safeGetTanks(handler);
        for (int tank = 0; tank < tankCount; tank++) {
            FluidStack fluidInTank = safeGetFluidInTank(handler, tank);
            if (fluidInTank.isEmpty())
                continue;
            if (!FluidStack.isSameFluidSameComponents(fluidInTank, targetFluid))
                continue;

            if (isCreativeHandler) {
                return fluidInTank.copyWithAmount(maxAmount);
            }
            
            int drainAmount = Math.min(maxAmount, fluidInTank.getAmount());
            FluidStack toDrain = fluidInTank.copyWithAmount(drainAmount);
            FluidStack drained = handler.drain(toDrain, FluidAction.EXECUTE);
            if (!drained.isEmpty())
                return drained;
        }
        return FluidStack.EMPTY;
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
    public com.simibubi.create.content.logistics.packager.IdentifiedInventory getIdentifiedInventory() {
        if (itemTarget == null)
            return null;
        return itemTarget.getIdentifiedInventory();
    }
}
