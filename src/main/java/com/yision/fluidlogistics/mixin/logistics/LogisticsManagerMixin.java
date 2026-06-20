package com.yision.fluidlogistics.mixin.logistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.yision.fluidlogistics.api.IFluidPackager;
import com.yision.fluidlogistics.item.CompressedTankItem;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(value = LogisticsManager.class, remap = false)
public abstract class LogisticsManagerMixin {

    @Inject(method = "broadcastPackageRequest", at = @At("HEAD"), cancellable = true)
    private static void fluidlogistics$broadcastFluidPackageRequest(UUID freqId, RequestType type,
        PackageOrderWithCrafts order, @Nullable IdentifiedInventory ignoredHandler, String address,
        CallbackInfoReturnable<Boolean> cir) {

        if (order.isEmpty() || !fluidlogistics$containsFluidRequest(order)) {
            return;
        }

        fluidlogistics$processMixedOrder(freqId, order, ignoredHandler, address, type, cir);
    }

    @Inject(method = "findPackagersForRequest", at = @At("HEAD"), cancellable = true)
    private static void fluidlogistics$findPackagersForFluidRequest(UUID freqId, PackageOrderWithCrafts order,
        @Nullable IdentifiedInventory ignoredHandler, String address,
        CallbackInfoReturnable<Multimap<PackagerBlockEntity, PackagingRequest>> cir) {

        if (order.isEmpty() || !fluidlogistics$containsFluidRequest(order)) {
            return;
        }

        fluidlogistics$processMixedOrderFindPackagers(freqId, order, ignoredHandler, address, cir);
    }

    @Unique
    private static boolean fluidlogistics$containsFluidRequest(PackageOrderWithCrafts order) {
        for (BigItemStack stack : order.stacks()) {
            if (!stack.stack.isEmpty()
                && stack.count > 0
                && stack.stack.getItem() instanceof CompressedTankItem
                && CompressedTankItem.isVirtual(stack.stack)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static void fluidlogistics$processMixedOrder(UUID freqId, PackageOrderWithCrafts order,
        @Nullable IdentifiedInventory ignoredHandler, String address, RequestType type,
        CallbackInfoReturnable<Boolean> cir) {

        List<BigItemStack> allStacksInOrder = fluidlogistics$getValidStacks(order);
        int unifiedOrderId = ThreadLocalRandom.current().nextInt();
        PackageOrderWithCrafts context = order;

        Map<Object, Integer> usedPackagers = new IdentityHashMap<>();

        Multimap<IFluidPackager, PackagingRequest> fluidRequests = HashMultimap.create();
        Multimap<PackagerBlockEntity, PackagingRequest> regularRequests = HashMultimap.create();
        MutableBoolean finalLinkTracker = new MutableBoolean(false);

        Iterable<LogisticallyLinkedBehaviour> allAvailableLinks = LogisticallyLinkedBehaviour.getAllPresent(freqId, true);
        List<LogisticallyLinkedBehaviour> availableLinks = fluidlogistics$collectAndDeduplicateLinks(allAvailableLinks);

        for (int i = 0; i < allStacksInOrder.size(); i++) {
            BigItemStack entry = allStacksInOrder.get(i);
            int remainingCount = entry.count;
            boolean isLastStack = i == allStacksInOrder.size() - 1;
            boolean isFluidStack = entry.stack.getItem() instanceof CompressedTankItem
                && CompressedTankItem.isVirtual(entry.stack);

            for (LogisticallyLinkedBehaviour link : availableLinks) {
                if (isFluidStack) {
                    IFluidPackager fluidPackager = fluidlogistics$getFluidPackagerFromLink(link);
                    if (fluidPackager == null || fluidPackager.isTargetingSameInventory(ignoredHandler)) {
                        continue;
                    }

                    Integer usedIndex = usedPackagers.get(fluidPackager);
                    int linkIndex = usedIndex == null ? usedPackagers.size() : usedIndex;
                    MutableBoolean isFinalLink = new MutableBoolean(false);
                    if (linkIndex == usedPackagers.size() - 1) {
                        isFinalLink = finalLinkTracker;
                    }

                    Pair<IFluidPackager, PackagingRequest> request = fluidPackager.processFluidRequest(entry.stack,
                        remainingCount, address, linkIndex, isFinalLink, unifiedOrderId, context, ignoredHandler);
                    if (request == null) {
                        continue;
                    }

                    fluidRequests.put(request.getFirst(), request.getSecond());

                    int processedCount = request.getSecond().getCount();
                    if (processedCount > 0 && usedIndex == null) {
                        context = null;
                        usedPackagers.put(fluidPackager, linkIndex);
                        finalLinkTracker = isFinalLink;
                    }

                    remainingCount -= processedCount;
                    if (remainingCount > 0) {
                        continue;
                    }
                    if (isLastStack) {
                        finalLinkTracker.setTrue();
                    }
                    break;
                }

                PackagerBlockEntity packager = fluidlogistics$getPackagerFromLink(link);
                if (packager == null) {
                    continue;
                }

                Integer usedIndex = usedPackagers.get(packager);
                int linkIndex = usedIndex == null ? usedPackagers.size() : usedIndex;
                MutableBoolean isFinalLink = new MutableBoolean(false);
                if (linkIndex == usedPackagers.size() - 1) {
                    isFinalLink = finalLinkTracker;
                }

                Pair<PackagerBlockEntity, PackagingRequest> request = link.processRequest(entry.stack, remainingCount,
                    address, linkIndex, isFinalLink, unifiedOrderId, context, ignoredHandler);
                if (request == null) {
                    continue;
                }

                regularRequests.put(request.getFirst(), request.getSecond());

                int processedCount = request.getSecond().getCount();
                if (processedCount > 0 && usedIndex == null) {
                    context = null;
                    usedPackagers.put(packager, linkIndex);
                    finalLinkTracker = isFinalLink;
                }

                remainingCount -= processedCount;
                if (remainingCount > 0) {
                    continue;
                }
                if (isLastStack) {
                    finalLinkTracker.setTrue();
                }
                break;
            }
        }

        for (IFluidPackager packager : fluidRequests.keySet()) {
            if (packager.isFluidPackagerTooBusy(type)) {
                cir.setReturnValue(false);
                return;
            }
        }

        for (PackagerBlockEntity packager : regularRequests.keySet()) {
            if (packager.isTooBusyFor(type)) {
                cir.setReturnValue(false);
                return;
            }
        }

        fluidlogistics$performFluidPackageRequests(fluidRequests);
        fluidlogistics$performRegularPackageRequests(regularRequests);
        cir.setReturnValue(true);
    }

    @Unique
    private static void fluidlogistics$processMixedOrderFindPackagers(UUID freqId, PackageOrderWithCrafts order,
        @Nullable IdentifiedInventory ignoredHandler, String address,
        CallbackInfoReturnable<Multimap<PackagerBlockEntity, PackagingRequest>> cir) {

        List<BigItemStack> allStacksInOrder = fluidlogistics$getValidStacks(order);
        int unifiedOrderId = ThreadLocalRandom.current().nextInt();
        PackageOrderWithCrafts context = order;
        boolean contextUsed = false;

        Map<Object, Integer> usedPackagers = new IdentityHashMap<>();

        Multimap<IFluidPackager, PackagingRequest> fluidRequests = HashMultimap.create();
        Multimap<PackagerBlockEntity, PackagingRequest> regularRequests = HashMultimap.create();
        MutableBoolean finalLinkTracker = new MutableBoolean(false);

        Iterable<LogisticallyLinkedBehaviour> allAvailableLinks = LogisticallyLinkedBehaviour.getAllPresent(freqId, true);
        List<LogisticallyLinkedBehaviour> availableLinks = fluidlogistics$collectAndDeduplicateLinks(allAvailableLinks);

        for (int i = 0; i < allStacksInOrder.size(); i++) {
            BigItemStack entry = allStacksInOrder.get(i);
            int remainingCount = entry.count;
            boolean isLastStack = i == allStacksInOrder.size() - 1;
            boolean isFluidStack = entry.stack.getItem() instanceof CompressedTankItem
                && CompressedTankItem.isVirtual(entry.stack);

            for (LogisticallyLinkedBehaviour link : availableLinks) {
                if (isFluidStack) {
                    IFluidPackager fluidPackager = fluidlogistics$getFluidPackagerFromLink(link);
                    if (fluidPackager == null || fluidPackager.isTargetingSameInventory(ignoredHandler)) {
                        continue;
                    }

                    Integer usedIndex = usedPackagers.get(fluidPackager);
                    int linkIndex = usedIndex == null ? usedPackagers.size() : usedIndex;
                    MutableBoolean isFinalLink = new MutableBoolean(false);
                    if (linkIndex == usedPackagers.size() - 1) {
                        isFinalLink = finalLinkTracker;
                    }

                    PackageOrderWithCrafts contextToSend = !contextUsed ? context : null;
                    Pair<IFluidPackager, PackagingRequest> request = fluidPackager.processFluidRequest(entry.stack,
                        remainingCount, address, linkIndex, isFinalLink, unifiedOrderId, contextToSend,
                        ignoredHandler);
                    if (request == null) {
                        continue;
                    }

                    fluidRequests.put(request.getFirst(), request.getSecond());

                    int processedCount = request.getSecond().getCount();
                    if (processedCount > 0) {
                        if (!contextUsed) {
                            contextUsed = true;
                        }
                        if (usedIndex == null) {
                            usedPackagers.put(fluidPackager, linkIndex);
                            finalLinkTracker = isFinalLink;
                        }
                    }

                    remainingCount -= processedCount;
                    if (remainingCount > 0) {
                        continue;
                    }
                    if (isLastStack) {
                        finalLinkTracker.setTrue();
                    }
                    break;
                }

                PackagerBlockEntity packager = fluidlogistics$getPackagerFromLink(link);
                if (packager == null) {
                    continue;
                }

                Integer usedIndex = usedPackagers.get(packager);
                int linkIndex = usedIndex == null ? usedPackagers.size() : usedIndex;
                MutableBoolean isFinalLink = new MutableBoolean(false);
                if (linkIndex == usedPackagers.size() - 1) {
                    isFinalLink = finalLinkTracker;
                }

                PackageOrderWithCrafts contextToSend = !contextUsed ? context : null;
                Pair<PackagerBlockEntity, PackagingRequest> request = link.processRequest(entry.stack, remainingCount,
                    address, linkIndex, isFinalLink, unifiedOrderId, contextToSend, ignoredHandler);
                if (request == null) {
                    continue;
                }

                regularRequests.put(request.getFirst(), request.getSecond());

                int processedCount = request.getSecond().getCount();
                    if (processedCount > 0) {
                        if (!contextUsed) {
                            contextUsed = true;
                        }
                    if (usedIndex == null) {
                        usedPackagers.put(packager, linkIndex);
                        finalLinkTracker = isFinalLink;
                    }
                }

                remainingCount -= processedCount;
                if (remainingCount > 0) {
                    continue;
                }
                if (isLastStack) {
                    finalLinkTracker.setTrue();
                }
                break;
            }
        }

        fluidlogistics$performFluidPackageRequests(fluidRequests);
        cir.setReturnValue(regularRequests);
    }

    @Unique
    private static List<BigItemStack> fluidlogistics$getValidStacks(PackageOrderWithCrafts order) {
        List<BigItemStack> allStacksInOrder = new ArrayList<>();
        for (BigItemStack stack : order.stacks()) {
            if (!stack.stack.isEmpty() && stack.count > 0) {
                allStacksInOrder.add(stack);
            }
        }
        return allStacksInOrder;
    }

    @Unique
    private static List<LogisticallyLinkedBehaviour> fluidlogistics$collectAndDeduplicateLinks(
        Iterable<LogisticallyLinkedBehaviour> allAvailableLinks) {
        Map<InventoryIdentifier, List<LogisticallyLinkedBehaviour>> linksByInventory = new HashMap<>();
        List<LogisticallyLinkedBehaviour> availableLinks = new ArrayList<>();

        for (LogisticallyLinkedBehaviour link : allAvailableLinks) {
            InventoryIdentifier inventoryId = fluidlogistics$getInventoryIdentifierFromLink(link);
            if (inventoryId != null) {
                linksByInventory.computeIfAbsent(inventoryId, k -> new ArrayList<>()).add(link);
            } else {
                availableLinks.add(link);
            }
        }

        for (List<LogisticallyLinkedBehaviour> linkGroup : linksByInventory.values()) {
            if (!linkGroup.isEmpty()) {
                availableLinks.add(linkGroup.get(ThreadLocalRandom.current().nextInt(linkGroup.size())));
            }
        }

        return availableLinks;
    }

    @Unique
    private static void fluidlogistics$performFluidPackageRequests(Multimap<IFluidPackager, PackagingRequest> requests) {
        for (Map.Entry<IFluidPackager, Collection<PackagingRequest>> entry : requests.asMap().entrySet()) {
            ArrayList<PackagingRequest> queuedRequests = new ArrayList<>(entry.getValue());
            IFluidPackager packager = entry.getKey();

            if (!queuedRequests.isEmpty()) {
                packager.flashFluidLink();
            }

            for (int i = 0; i < 100 && !queuedRequests.isEmpty(); i++) {
                packager.attemptToSendFluidRequest(queuedRequests);
            }

            packager.triggerStockCheck();
            if (packager instanceof SmartBlockEntity smartBlockEntity) {
                smartBlockEntity.notifyUpdate();
            }
        }
    }

    @Unique
    private static void fluidlogistics$performRegularPackageRequests(
        Multimap<PackagerBlockEntity, PackagingRequest> requests) {
        for (Map.Entry<PackagerBlockEntity, Collection<PackagingRequest>> entry : requests.asMap().entrySet()) {
            ArrayList<PackagingRequest> queuedRequests = new ArrayList<>(entry.getValue());
            PackagerBlockEntity packager = entry.getKey();

            if (!queuedRequests.isEmpty()) {
                packager.flashLink();
            }

            for (int i = 0; i < 100 && !queuedRequests.isEmpty(); i++) {
                packager.attemptToSend(queuedRequests);
            }

            packager.triggerStockCheck();
            packager.notifyUpdate();
        }
    }

    @Unique
    @Nullable
    private static InventoryIdentifier fluidlogistics$getInventoryIdentifierFromLink(LogisticallyLinkedBehaviour link) {
        IFluidPackager fluidPackager = fluidlogistics$getFluidPackagerFromLink(link);
        if (fluidPackager != null) {
            IdentifiedInventory identifiedInventory = fluidPackager.getIdentifiedInventory();
            return identifiedInventory != null ? identifiedInventory.identifier() : null;
        }

        if (!(link.blockEntity instanceof PackagerLinkBlockEntity packagerLink)) {
            return null;
        }

        PackagerBlockEntity packager = packagerLink.getPackager();
        if (packager == null || !packager.targetInventory.hasInventory()) {
            return null;
        }

        IdentifiedInventory identifiedInventory = packager.targetInventory.getIdentifiedInventory();
        return identifiedInventory != null ? identifiedInventory.identifier() : null;
    }

    @Unique
    @Nullable
    private static IFluidPackager fluidlogistics$getFluidPackagerFromLink(LogisticallyLinkedBehaviour link) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity packagerLink)) {
            return null;
        }

        if (link.redstonePower == 15) {
            return null;
        }

        BlockPos source = packagerLink.getBlockPos()
            .relative(PackagerLinkBlock.getConnectedDirection(packagerLink.getBlockState()).getOpposite());
        BlockEntity blockEntity = packagerLink.getLevel().getBlockEntity(source);
        return blockEntity instanceof IFluidPackager fluidPackager ? fluidPackager : null;
    }

    @Unique
    @Nullable
    private static PackagerBlockEntity fluidlogistics$getPackagerFromLink(LogisticallyLinkedBehaviour link) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity packagerLink)) {
            return null;
        }

        if (link.redstonePower == 15) {
            return null;
        }

        PackagerBlockEntity packager = packagerLink.getPackager();
        if (packager instanceof IFluidPackager) {
            return null;
        }
        return packager;
    }
}
