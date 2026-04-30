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
import com.yision.fluidlogistics.api.IFluidPackager;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(LogisticsManager.class)
public abstract class LogisticsManagerMixin {

    @Inject(
        method = "broadcastPackageRequest",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void fluidlogistics$broadcastFluidPackageRequest(UUID freqId, RequestType type, 
            PackageOrderWithCrafts order, @Nullable IdentifiedInventory ignoredHandler, String address,
            CallbackInfoReturnable<Boolean> cir) {
        
        if (order.isEmpty() || !fluidlogistics$hasVirtualFluidRequest(order)) {
            return;
        }

        fluidlogistics$processMixedOrder(freqId, order, ignoredHandler, address, type, cir);
    }

    @Inject(
        method = "findPackagersForRequest",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void fluidlogistics$findPackagersForFluidRequest(UUID freqId,
            PackageOrderWithCrafts order, @Nullable IdentifiedInventory ignoredHandler, String address,
            CallbackInfoReturnable<Multimap<PackagerBlockEntity, PackagingRequest>> cir) {
        
        if (order.isEmpty() || !fluidlogistics$hasVirtualFluidRequest(order)) {
            return;
        }

        fluidlogistics$processMixedOrderFindPackagers(freqId, order, ignoredHandler, address, cir);
    }

    @Unique
    private static void fluidlogistics$processMixedOrder(UUID freqId, PackageOrderWithCrafts order,
            @Nullable IdentifiedInventory ignoredHandler, String address, RequestType type,
            CallbackInfoReturnable<Boolean> cir) {
        
        List<BigItemStack> allStacksInOrder = fluidlogistics$getRelevantOrderStacks(order);
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
            
            boolean isFluidStack = fluidlogistics$isVirtualFluidRequest(entry.stack);

            for (LogisticallyLinkedBehaviour link : availableLinks) {
                if (isFluidStack) {
                    IFluidPackager fluidPackager = fluidlogistics$getFluidPackagerFromLink(link);
                    if (fluidPackager == null) {
                        continue;
                    }

                    if (fluidPackager.isTargetingSameInventory(ignoredHandler)) {
                        continue;
                    }

                    Integer usedIndex = usedPackagers.get(fluidPackager);
                    int linkIndex = usedIndex == null ? usedPackagers.size() : usedIndex;
                    MutableBoolean isFinalLink = new MutableBoolean(false);
                    if (linkIndex == usedPackagers.size() - 1)
                        isFinalLink = finalLinkTracker;

                    PackageOrderWithCrafts contextToSend = context;
                    Pair<IFluidPackager, PackagingRequest> request = fluidPackager.processFluidRequest(
                        entry.stack, remainingCount, address, linkIndex, isFinalLink, unifiedOrderId, contextToSend, ignoredHandler);
                    
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
                    if (remainingCount > 0)
                        continue;
                    if (isLastStack)
                        finalLinkTracker.setTrue();
                    break;
                } else {
                    PackagerBlockEntity packager = fluidlogistics$getPackagerFromLink(link);
                    if (packager == null) {
                        continue;
                    }

                    Integer usedIndex = usedPackagers.get(packager);
                    int linkIndex = usedIndex == null ? usedPackagers.size() : usedIndex;
                    MutableBoolean isFinalLink = new MutableBoolean(false);
                    if (linkIndex == usedPackagers.size() - 1)
                        isFinalLink = finalLinkTracker;

                    PackageOrderWithCrafts contextToSend = context;
                    Pair<PackagerBlockEntity, PackagingRequest> request = link.processRequest(
                        entry.stack, remainingCount, address, linkIndex, isFinalLink, unifiedOrderId, contextToSend, ignoredHandler);
                    
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
                    if (remainingCount > 0)
                        continue;
                    if (isLastStack)
                        finalLinkTracker.setTrue();
                    break;
                }
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
        
        List<BigItemStack> allStacksInOrder = fluidlogistics$getRelevantOrderStacks(order);
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
            
            boolean isFluidStack = fluidlogistics$isVirtualFluidRequest(entry.stack);

            for (LogisticallyLinkedBehaviour link : availableLinks) {
                if (isFluidStack) {
                    IFluidPackager fluidPackager = fluidlogistics$getFluidPackagerFromLink(link);
                    if (fluidPackager == null) {
                        continue;
                    }

                    if (fluidPackager.isTargetingSameInventory(ignoredHandler)) {
                        continue;
                    }

                    Integer usedIndex = usedPackagers.get(fluidPackager);
                    int linkIndex = usedIndex == null ? usedPackagers.size() : usedIndex;
                    MutableBoolean isFinalLink = new MutableBoolean(false);
                    if (linkIndex == usedPackagers.size() - 1)
                        isFinalLink = finalLinkTracker;

                    PackageOrderWithCrafts contextToSend = !contextUsed ? context : null;
                    Pair<IFluidPackager, PackagingRequest> request = fluidPackager.processFluidRequest(
                        entry.stack, remainingCount, address, linkIndex, isFinalLink, unifiedOrderId, contextToSend, ignoredHandler);
                    
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
                } else {
                    PackagerBlockEntity packager = fluidlogistics$getPackagerFromLink(link);
                    if (packager == null) {
                        continue;
                    }

                    Integer usedIndex = usedPackagers.get(packager);
                    int linkIndex = usedIndex == null ? usedPackagers.size() : usedIndex;
                    MutableBoolean isFinalLink = new MutableBoolean(false);
                    if (linkIndex == usedPackagers.size() - 1)
                        isFinalLink = finalLinkTracker;

                    PackageOrderWithCrafts contextToSend = !contextUsed ? context : null;
                    Pair<PackagerBlockEntity, PackagingRequest> request = link.processRequest(
                        entry.stack, remainingCount, address, linkIndex, isFinalLink, unifiedOrderId, contextToSend, ignoredHandler);
                    
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
        }

        fluidlogistics$performFluidPackageRequests(fluidRequests);
        
        cir.setReturnValue(regularRequests);
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
                LogisticallyLinkedBehaviour selectedLink = linkGroup.get(ThreadLocalRandom.current().nextInt(linkGroup.size()));
                availableLinks.add(selectedLink);
            }
        }

        return availableLinks;
    }

    @Unique
    private static boolean fluidlogistics$hasVirtualFluidRequest(PackageOrderWithCrafts order) {
        for (BigItemStack stack : order.stacks()) {
            if (stack.count > 0 && fluidlogistics$isVirtualFluidRequest(stack.stack)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static List<BigItemStack> fluidlogistics$getRelevantOrderStacks(PackageOrderWithCrafts order) {
        List<BigItemStack> stacks = new ArrayList<>();
        for (BigItemStack stack : order.stacks()) {
            if (!stack.stack.isEmpty() && stack.count > 0) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    @Unique
    private static boolean fluidlogistics$isVirtualFluidRequest(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() instanceof com.yision.fluidlogistics.item.CompressedTankItem
            && com.yision.fluidlogistics.item.CompressedTankItem.isVirtual(stack);
    }

    @Unique
    private static void fluidlogistics$performFluidPackageRequests(Multimap<IFluidPackager, PackagingRequest> requests) {
        Map<IFluidPackager, Collection<PackagingRequest>> asMap = requests.asMap();
        for (Map.Entry<IFluidPackager, Collection<PackagingRequest>> entry : asMap.entrySet()) {
            ArrayList<PackagingRequest> queuedRequests = new ArrayList<>(entry.getValue());
            IFluidPackager packager = entry.getKey();

            if (!queuedRequests.isEmpty()) {
                packager.flashFluidLink();
            }
            for (int i = 0; i < 100; i++) {
                if (queuedRequests.isEmpty()) {
                    break;
                }
                packager.attemptToSendFluidRequest(queuedRequests);
            }

            packager.triggerStockCheck();
        }
    }

    @Unique
    private static void fluidlogistics$performRegularPackageRequests(Multimap<PackagerBlockEntity, PackagingRequest> requests) {
        Map<PackagerBlockEntity, Collection<PackagingRequest>> asMap = requests.asMap();
        for (Map.Entry<PackagerBlockEntity, Collection<PackagingRequest>> entry : asMap.entrySet()) {
            ArrayList<PackagingRequest> queuedRequests = new ArrayList<>(entry.getValue());
            PackagerBlockEntity packager = entry.getKey();

            if (!queuedRequests.isEmpty()) {
                packager.flashLink();
            }
            for (int i = 0; i < 100; i++) {
                if (queuedRequests.isEmpty()) {
                    break;
                }
                packager.attemptToSend(queuedRequests);
            }

            packager.triggerStockCheck();
            packager.notifyUpdate();
        }
    }

    @Unique
    @Nullable
    private static InventoryIdentifier fluidlogistics$getInventoryIdentifierFromLink(LogisticallyLinkedBehaviour link) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe)) {
            return null;
        }

        PackagerBlockEntity packager = plbe.getPackager();
        if (packager == null || !packager.targetInventory.hasInventory()) {
            return null;
        }

        IdentifiedInventory identifiedInventory = packager.targetInventory.getIdentifiedInventory();
        return identifiedInventory != null ? identifiedInventory.identifier() : null;
    }

    @Unique
    @Nullable
    private static IFluidPackager fluidlogistics$getFluidPackagerFromLink(LogisticallyLinkedBehaviour link) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe)) {
            return null;
        }

        if (link.redstonePower == 15) {
            return null;
        }

        BlockPos source = plbe.getBlockPos().relative(
            PackagerLinkBlock.getConnectedDirection(plbe.getBlockState()).getOpposite());
        BlockEntity blockEntity = plbe.getLevel().getBlockEntity(source);

        if (blockEntity instanceof IFluidPackager fluidPackager) {
            return fluidPackager;
        }
        return null;
    }

    @Unique
    @Nullable
    private static PackagerBlockEntity fluidlogistics$getPackagerFromLink(LogisticallyLinkedBehaviour link) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe)) {
            return null;
        }

        if (link.redstonePower == 15) {
            return null;
        }

        return plbe.getPackager();
    }
}
