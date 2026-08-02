package com.yision.fluidlogistics.content.logistics.packageResource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

@ApiStatus.Internal
final class ResourcePackagerPromiseHelper {
    private static final Cache<Object, Map<ResourceLocation, Cache<RequestPromiseQueue, ObservedSummary>>>
            SUMMARIES_BY_STORAGE = CacheBuilder.newBuilder().weakKeys().build();

    private ResourcePackagerPromiseHelper() {
    }

    public static void notifyNewArrivals(
            PackagerBlockEntity packager,
            ResourceLocation resourceType,
            @Nullable Object storageIdentity,
            @Nullable InventorySummary before,
            long beforeObservation,
            long currentObservation,
            InventorySummary after) {
        Level level = packager.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        Set<PromiseTarget> promiseTargets = findPromiseTargets(level, packager.getBlockPos());
        for (PromiseTarget target : promiseTargets) {
            RequestPromiseQueue queue = target.queue();
            InventorySummary queueBefore = storageIdentity == null
                    ? before
                    : updateAndGetPrevious(
                            queue,
                            resourceType,
                            storageIdentity,
                            before,
                            beforeObservation,
                            after,
                            currentObservation);
            if (queueBefore == null) {
                continue;
            }
            boolean summariesInvalidated = false;
            for (BigItemStack entry : after.getStacks()) {
                int increase = entry.count - queueBefore.getCountOf(entry.stack);
                if (increase > 0) {
                    if (!summariesInvalidated && target.networkId() != null) {
                        invalidateNetworkSummaries(target.networkId());
                        summariesInvalidated = true;
                    }
                    queue.itemEnteredSystem(entry.stack, increase);
                }
            }
        }
    }

    private static Set<PromiseTarget> findPromiseTargets(Level level, BlockPos packagerPos) {
        Set<PromiseTarget> promiseTargets = new HashSet<>();
        for (Direction direction : Iterate.directions) {
            BlockPos adjacentPos = packagerPos.relative(direction);
            if (!level.isLoaded(adjacentPos)) {
                continue;
            }

            BlockState adjacentState = level.getBlockState(adjacentPos);
            if (AllBlocks.FACTORY_GAUGE.has(adjacentState)
                    && FactoryPanelBlock.connectedDirection(adjacentState) == direction
                    && level.getBlockEntity(adjacentPos) instanceof FactoryPanelBlockEntity panel
                    && panel.restocker) {
                for (FactoryPanelBehaviour behaviour : panel.panels.values()) {
                    if (behaviour.isActive()) {
                        promiseTargets.add(new PromiseTarget(behaviour.restockerPromises, null));
                    }
                }
            }

            if (AllBlocks.STOCK_LINK.has(adjacentState)
                    && PackagerLinkBlock.getConnectedDirection(adjacentState) == direction
                    && level.getBlockEntity(adjacentPos) instanceof PackagerLinkBlockEntity link) {
                UUID frequencyId = link.behaviour.freqId;
                RequestPromiseQueue queue = Create.LOGISTICS.getQueuedPromises(frequencyId);
                if (queue != null) {
                    promiseTargets.add(new PromiseTarget(queue, frequencyId));
                }
            }
        }
        return promiseTargets;
    }

    private static void invalidateNetworkSummaries(UUID networkId) {
        LogisticsManager.SUMMARIES.invalidate(networkId);
        LogisticsManager.ACCURATE_SUMMARIES.invalidate(networkId);
    }

    @Nullable
    private static synchronized InventorySummary updateAndGetPrevious(
            RequestPromiseQueue queue,
            ResourceLocation resourceType,
            Object storageIdentity,
            @Nullable InventorySummary fallback,
            long fallbackObservation,
            InventorySummary current,
            long currentObservation) {
        Map<ResourceLocation, Cache<RequestPromiseQueue, ObservedSummary>> summariesByType =
                SUMMARIES_BY_STORAGE.getIfPresent(storageIdentity);
        if (summariesByType == null) {
            summariesByType = new HashMap<>();
            SUMMARIES_BY_STORAGE.put(storageIdentity, summariesByType);
        }
        Cache<RequestPromiseQueue, ObservedSummary> summariesByQueue = summariesByType.computeIfAbsent(
                resourceType, ignored -> CacheBuilder.newBuilder().weakKeys().build());
        ObservedSummary previous = summariesByQueue.getIfPresent(queue);
        if (previous != null && previous.observation() >= currentObservation) {
            return current;
        }
        if (fallback == null && previous != null) {
            return null;
        }
        summariesByQueue.put(queue, new ObservedSummary(current, currentObservation));
        if (fallback == null
                || previous == null
                || sameSummary(fallback, current)
                || fallbackObservation >= previous.observation()) {
            return fallback;
        }
        return previous.summary();
    }

    private static boolean sameSummary(InventorySummary first, InventorySummary second) {
        for (BigItemStack entry : first.getStacks()) {
            if (first.getCountOf(entry.stack) != second.getCountOf(entry.stack)) {
                return false;
            }
        }
        for (BigItemStack entry : second.getStacks()) {
            if (first.getCountOf(entry.stack) != second.getCountOf(entry.stack)) {
                return false;
            }
        }
        return true;
    }

    private record ObservedSummary(InventorySummary summary, long observation) {
    }

    private record PromiseTarget(RequestPromiseQueue queue, @Nullable UUID networkId) {
    }
}
