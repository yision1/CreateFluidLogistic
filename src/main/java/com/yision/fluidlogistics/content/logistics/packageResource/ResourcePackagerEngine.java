package com.yision.fluidlogistics.content.logistics.packageResource;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.api.packager.PackageInspection;
import com.yision.fluidlogistics.api.packager.PackageResource;
import com.yision.fluidlogistics.api.packager.PackageResourceType;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.ResourcePackager;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.advancement.AllAdvancements;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@ApiStatus.Internal
public final class ResourcePackagerEngine {
    private static final int STORAGE_SETTLE_TICKS = 65;
    private static final Map<PackagerBlockEntity, RuntimeState> STATES_BY_OWNER = new WeakHashMap<>();
    private static final AtomicLong SUMMARY_OBSERVATIONS = new AtomicLong();

    private ResourcePackagerEngine() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static Optional<ResourcePackager> of(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof ResourcePackager resourcePackager) {
            return Optional.of(remember(resourcePackager));
        }
        return Optional.empty();
    }

    public static Optional<ResourcePackager> ownerOf(@Nullable PackagerBlockEntity owner) {
        if (owner == null) {
            return Optional.empty();
        }
        Optional<ResourcePackager> direct = of(owner);
        if (direct.isPresent()) {
            return direct;
        }
        synchronized (STATES_BY_OWNER) {
            RuntimeState state = STATES_BY_OWNER.get(owner);
            ResourcePackager packager = state == null ? null : state.packager().get();
            if (packager == null || packager.owner() != owner) {
                STATES_BY_OWNER.remove(owner);
                return Optional.empty();
            }
            return Optional.of(packager);
        }
    }

    public static Optional<ResourcePackager> fromLink(LogisticallyLinkedBehaviour link) {
        Objects.requireNonNull(link, "link");
        if (link.redstonePower == 15 || !(link.blockEntity instanceof PackagerLinkBlockEntity linkBlockEntity)) {
            return Optional.empty();
        }
        Level level = linkBlockEntity.getLevel();
        if (level == null) {
            return Optional.empty();
        }
        BlockPos source = linkBlockEntity.getBlockPos().relative(
                PackagerLinkBlock.getConnectedDirection(linkBlockEntity.getBlockState()).getOpposite());
        if (!level.isLoaded(source)) {
            return Optional.empty();
        }
        return of(level.getBlockEntity(source));
    }

    public static Optional<ResourcePackager> fromLink(
            LogisticallyLinkedBehaviour link,
            PackageResourceType type,
            ItemStack normalizedKey) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(normalizedKey, "normalizedKey");
        return fromLink(link).filter(packager -> supports(packager, type, normalizedKey));
    }

    public static boolean supports(
            ResourcePackager packager, PackageResourceType type, ItemStack normalizedKey) {
        Objects.requireNonNull(packager, "packager");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(normalizedKey, "normalizedKey");
        return packager.resourceTypeId().equals(type.id()) && type.isValidCarrier(normalizedKey);
    }

    public static InventorySummary getAvailableResources(ResourcePackager packager) {
        RuntimeState state = state(packager);
        refreshAvailableResources(packager, state);
        synchronized (state) {
            return state.storageAvailable() && state.summary() != null
                    ? state.summary().copy()
                    : InventorySummary.EMPTY;
        }
    }

    public static InventorySummary getLastKnownResources(ResourcePackager packager) {
        return getLastKnownResources(packager, null);
    }

    public static InventorySummary getLastKnownResources(
            ResourcePackager packager, @Nullable InventorySummary lastKnown) {
        RuntimeState state = state(packager);
        synchronized (state) {
            if (state.summary() == null && lastKnown != null) {
                state.summary(lastKnown.copy());
                state.restoredSummaryPending(true);
            }
        }
        refreshAvailableResources(packager, state);
        synchronized (state) {
            return state.summary() == null ? InventorySummary.EMPTY : state.summary().copy();
        }
    }

    public static void restoreAvailableResources(
            ResourcePackager packager, @Nullable InventorySummary lastKnown) {
        RuntimeState state = state(packager);
        synchronized (state) {
            state.storageIdentity(null);
            state.storageAvailable(false);
            state.summary(lastKnown == null ? null : lastKnown.copy());
            state.restoredSummaryPending(lastKnown != null);
            state.scanTick(Long.MIN_VALUE);
            state.summaryObservation(0);
            state.clearPendingStorage();
        }
    }

    @Nullable
    public static Object storageIdentity(ResourcePackager packager) {
        RuntimeState state = state(packager);
        refreshAvailableResources(packager, state);
        synchronized (state) {
            return state.storageAvailable() ? state.storageIdentity() : null;
        }
    }

    public static void triggerStockCheck(ResourcePackager packager) {
        invalidateAvailableResources(packager);
        getAvailableResources(packager);
    }

    public static void attemptToSend(
            ResourcePackager packager, @Nullable List<PackagingRequest> queuedRequests) {
        if (queuedRequests == null) {
            attemptManualPackage(packager);
            return;
        }
        attemptQueuedPackage(packager, queuedRequests);
    }

    public static boolean unpack(ResourcePackager packager, ItemStack packageStack, boolean simulate) {
        PackagerBlockEntity owner = packager.owner();
        if (owner.animationTicks > 0 || !PackageResources.isBootstrapped()) {
            return false;
        }
        PackageInspection inspection = PackageResources.inspectPackage(packageStack);
        if (!inspection.canonical() || inspection.hasOrdinaryItems() || inspection.resources().size() != 1) {
            return false;
        }
        PackageResource resource = inspection.resources().getFirst();
        if (!packager.resourceTypeId().equals(resource.typeId())) {
            return false;
        }
        ItemStack key = resource.key();
        int amount = resource.amount();
        if (checkedTransfer(packager.insert(key.copy(), amount, true), amount, "insert simulation") != amount) {
            return false;
        }
        if (simulate) {
            return true;
        }
        int inserted = packager.insert(key.copy(), amount, false);
        invalidateAvailableResources(packager);
        inserted = checkedTransfer(inserted, amount, "insert execution");
        if (inserted != amount) {
            throw new IllegalStateException("insert execution accepted " + inserted
                    + " after simulation accepted " + amount);
        }
        owner.previouslyUnwrapped = packageStack.copyWithCount(1);
        owner.animationInward = true;
        owner.animationTicks = PackagerBlockEntity.CYCLE;
        owner.notifyUpdate();
        return true;
    }

    private static void attemptManualPackage(ResourcePackager packager) {
        PackagerBlockEntity owner = packager.owner();
        if (!owner.heldBox.isEmpty() || owner.animationTicks != 0 || owner.buttonCooldown > 0) {
            return;
        }
        for (BigItemStack entry : getAvailableResources(packager).getStacks()) {
            PackageResourceType type = matchingType(packager, entry.stack);
            if (type == null || entry.count <= 0) {
                continue;
            }
            int requested = Math.min(entry.count, type.maxPerPackage(entry.stack.copy()));
            ItemStack packageStack = extractPackage(packager, entry.stack, requested).stack();
            if (packageStack.isEmpty()) {
                continue;
            }
            if (!owner.signBasedAddress.isBlank()) {
                PackageItem.addAddress(packageStack, owner.signBasedAddress);
            }
            output(owner, packager, packageStack);
            return;
        }
    }

    private static void attemptQueuedPackage(
            ResourcePackager packager, List<PackagingRequest> queuedRequests) {
        if (queuedRequests.isEmpty()) {
            return;
        }
        PackagingRequest request = queuedRequests.getFirst();
        PackageResourceType type = matchingType(packager, request.item());
        if (type == null) {
            queuedRequests.removeFirst();
            return;
        }
        ItemStack key = type.normalizeKey(request.item().copy());
        int requested = Math.min(request.getCount(), type.maxPerPackage(key.copy()));
        ExtractedPackage extractedPackage = extractPackage(packager, key, requested);
        ItemStack packageStack = extractedPackage.stack();
        if (packageStack.isEmpty()) {
            queuedRequests.removeFirst();
            return;
        }
        ResourcePackagingRequestHelper.consumeFirst(queuedRequests, extractedPackage.amount()).applyTo(packageStack);
        output(packager.owner(), packager, packageStack);
    }

    private static ExtractedPackage extractPackage(
            ResourcePackager packager,
            ItemStack normalizedKey,
            int requested) {
        if (requested <= 0) {
            return ExtractedPackage.EMPTY;
        }
        int simulated = checkedTransfer(
                packager.extract(normalizedKey.copy(), requested, true), requested, "extract simulation");
        if (simulated <= 0) {
            return ExtractedPackage.EMPTY;
        }
        ItemStack prepared = PackageResources.createPackage(normalizedKey.copy(), simulated);
        int extracted = packager.extract(normalizedKey.copy(), simulated, false);
        invalidateAvailableResources(packager);
        extracted = checkedTransfer(extracted, simulated, "extract execution");
        if (extracted <= 0) {
            return ExtractedPackage.EMPTY;
        }
        invalidateAccurateSummary(packager.owner());
        ItemStack packageStack = extracted == simulated
                ? prepared
                : PackageResources.createPackage(normalizedKey.copy(), extracted);
        return new ExtractedPackage(packageStack, extracted);
    }

    private record ExtractedPackage(ItemStack stack, int amount) {
        private static final ExtractedPackage EMPTY = new ExtractedPackage(ItemStack.EMPTY, 0);
    }

    private static void output(
            PackagerBlockEntity owner, ResourcePackager packager, ItemStack packageStack) {
        if (owner.getLevel() != null) {
            AdvancementBehaviour.tryAward(
                    owner.getLevel(), owner.getBlockPos(), AllAdvancements.PACKAGER);
        }
        if (!owner.heldBox.isEmpty() || owner.animationTicks != 0) {
            owner.queuedExitingPackages.add(new BigItemStack(packageStack, 1));
            return;
        }
        owner.heldBox = packageStack;
        owner.animationInward = false;
        owner.animationTicks = PackagerBlockEntity.CYCLE;
        triggerStockCheck(packager);
        owner.notifyUpdate();
    }

    @Nullable
    private static PackageResourceType matchingType(ResourcePackager packager, ItemStack stack) {
        if (!PackageResources.isBootstrapped() || stack == null || stack.isEmpty()) {
            return null;
        }
        PackageResourceType type = PackageResources.findType(stack).orElse(null);
        if (type == null || !packager.resourceTypeId().equals(type.id())) {
            return null;
        }
        return type;
    }

    private static int checkedTransfer(int transferred, int requested, String operation) {
        if (transferred < 0 || transferred > requested) {
            throw new IllegalStateException(operation + " returned " + transferred
                    + " for requested amount " + requested);
        }
        return transferred;
    }

    @Nullable
    private static InventorySummary refreshAvailableResources(
            ResourcePackager packager, RuntimeState state) {
        PackagerBlockEntity owner = packager.owner();
        Level level = owner.getLevel();
        long scanTick = level == null ? Long.MIN_VALUE : level.getGameTime();
        synchronized (state) {
            if (scanTick != Long.MIN_VALUE && state.scanTick() == scanTick && state.summary() != null) {
                return null;
            }

            ResourcePackager.Snapshot snapshot = Objects.requireNonNull(
                    packager.scan(), "resource packager snapshot");
            if (snapshot.storageIdentity() == null) {
                state.storageAvailable(false);
                state.scanTick(scanTick);
                return null;
            }
            InventorySummary current = snapshot.resources();
            validateSummary(packager, current);
            if (state.storageIdentity() != snapshot.storageIdentity()
                    && current.getStacks().isEmpty()
                    && !state.storageSettled(snapshot.storageIdentity(), scanTick)) {
                state.storageAvailable(false);
                state.scanTick(scanTick);
                return null;
            }
            state.clearPendingStorage();

            InventorySummary previous =
                    state.storageIdentity() == snapshot.storageIdentity() || state.restoredSummaryPending()
                            ? state.summary()
                            : null;
            long previousObservation = state.summaryObservation();
            long currentObservation = SUMMARY_OBSERVATIONS.incrementAndGet();
            InventorySummary stored = current.copy();
            state.storageIdentity(snapshot.storageIdentity());
            state.storageAvailable(true);
            state.summary(stored);
            state.restoredSummaryPending(false);
            state.scanTick(scanTick);
            state.summaryObservation(currentObservation);
            ResourcePackagerPromiseHelper.notifyNewArrivals(
                    owner,
                    packager.resourceTypeId(),
                    snapshot.storageIdentity(),
                    previous,
                    previousObservation,
                    currentObservation,
                    stored);
            return current;
        }
    }

    private static void invalidateAvailableResources(ResourcePackager packager) {
        RuntimeState state = state(packager);
        synchronized (state) {
            state.scanTick(Long.MIN_VALUE);
        }
    }

    private static void invalidateAccurateSummary(PackagerBlockEntity owner) {
        Level level = owner.getLevel();
        if (level == null) {
            return;
        }
        for (Direction direction : Iterate.directions) {
            BlockPos linkPos = owner.getBlockPos().relative(direction);
            BlockState linkState = level.getBlockState(linkPos);
            if (!AllBlocks.STOCK_LINK.has(linkState)
                    || PackagerLinkBlock.getConnectedDirection(linkState) != direction) {
                continue;
            }
            if (level.getBlockEntity(linkPos) instanceof PackagerLinkBlockEntity link) {
                LogisticsManager.ACCURATE_SUMMARIES.invalidate(link.behaviour.freqId);
            }
            return;
        }
    }

    private static void validateSummary(ResourcePackager packager, InventorySummary summary) {
        ResourceLocation expectedType = Objects.requireNonNull(
                packager.resourceTypeId(), "resource packager type id");
        for (BigItemStack entry : summary.getStacks()) {
            PackageResourceType type = PackageResources.findType(entry.stack).orElseThrow(() ->
                    new IllegalStateException("resource packager summary contains an unregistered key"));
            if (!expectedType.equals(type.id()) || entry.count < 0) {
                throw new IllegalStateException(
                        "resource packager summary contains a key outside " + expectedType);
            }
        }
    }

    private static ResourcePackager remember(ResourcePackager packager) {
        PackagerBlockEntity owner = Objects.requireNonNull(packager.owner(), "resource packager owner");
        Objects.requireNonNull(packager.resourceTypeId(), "resource packager type id");
        synchronized (STATES_BY_OWNER) {
            RuntimeState existing = STATES_BY_OWNER.get(owner);
            ResourcePackager existingPackager = existing == null ? null : existing.packager().get();
            if (existingPackager != null && existingPackager != packager && !existingPackager.equals(packager)) {
                throw new IllegalStateException("multiple resource packagers use the same owner at "
                        + owner.getBlockPos());
            }
            if (existing == null || existingPackager == null) {
                STATES_BY_OWNER.put(owner, new RuntimeState(new WeakReference<>(packager)));
            }
        }
        return packager;
    }

    private static RuntimeState state(ResourcePackager packager) {
        remember(packager);
        synchronized (STATES_BY_OWNER) {
            return STATES_BY_OWNER.get(packager.owner());
        }
    }

    private static final class RuntimeState {
        private final WeakReference<ResourcePackager> packager;
        @Nullable
        private Object storageIdentity;
        @Nullable
        private InventorySummary summary;
        @Nullable
        private Object pendingStorageIdentity;
        private long pendingStorageSince = Long.MIN_VALUE;
        private boolean storageAvailable;
        private boolean restoredSummaryPending;
        private long scanTick = Long.MIN_VALUE;
        private long summaryObservation;

        private RuntimeState(WeakReference<ResourcePackager> packager) {
            this.packager = packager;
        }

        private WeakReference<ResourcePackager> packager() {
            return packager;
        }

        @Nullable
        private Object storageIdentity() {
            return storageIdentity;
        }

        private void storageIdentity(@Nullable Object storageIdentity) {
            this.storageIdentity = storageIdentity;
        }

        private boolean storageAvailable() {
            return storageAvailable;
        }

        private void storageAvailable(boolean storageAvailable) {
            this.storageAvailable = storageAvailable;
        }

        private boolean restoredSummaryPending() {
            return restoredSummaryPending;
        }

        private void restoredSummaryPending(boolean restoredSummaryPending) {
            this.restoredSummaryPending = restoredSummaryPending;
        }

        private boolean storageSettled(Object storageIdentity, long scanTick) {
            if (pendingStorageIdentity != storageIdentity) {
                pendingStorageIdentity = storageIdentity;
                pendingStorageSince = scanTick;
                return false;
            }
            return scanTick != Long.MIN_VALUE && scanTick - pendingStorageSince >= STORAGE_SETTLE_TICKS;
        }

        private void clearPendingStorage() {
            pendingStorageIdentity = null;
            pendingStorageSince = Long.MIN_VALUE;
        }

        @Nullable
        private InventorySummary summary() {
            return summary;
        }

        private void summary(@Nullable InventorySummary summary) {
            this.summary = summary;
        }

        private long scanTick() {
            return scanTick;
        }

        private void scanTick(long scanTick) {
            this.scanTick = scanTick;
        }

        private long summaryObservation() {
            return summaryObservation;
        }

        private void summaryObservation(long summaryObservation) {
            this.summaryObservation = summaryObservation;
        }
    }
}
