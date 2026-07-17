package com.yision.fluidlogistics.content.logistics.packageResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

@ApiStatus.Internal
final class ResourcePackagerPromiseHelper {
    private ResourcePackagerPromiseHelper() {
    }

    public static List<BigItemStack> findNewArrivals(
            @Nullable InventorySummary before, InventorySummary after) {
        if (before == null || after == null || after.isEmpty()) {
            return List.of();
        }

        List<BigItemStack> arrivals = new ArrayList<>();
        for (BigItemStack entry : after.getStacks()) {
            int increase = entry.count - before.getCountOf(entry.stack);
            if (increase > 0) {
                arrivals.add(new BigItemStack(entry.stack.copy(), increase));
            }
        }
        return List.copyOf(arrivals);
    }

    public static void notifyNewArrivals(
            PackagerBlockEntity packager, @Nullable InventorySummary before, InventorySummary after) {
        List<BigItemStack> arrivals = findNewArrivals(before, after);
        Level level = packager.getLevel();
        if (arrivals.isEmpty() || level == null) {
            return;
        }

        Set<RequestPromiseQueue> promiseQueues = findPromiseQueues(level, packager.getBlockPos());
        for (RequestPromiseQueue queue : promiseQueues) {
            for (BigItemStack arrival : arrivals) {
                queue.itemEnteredSystem(arrival.stack, arrival.count);
            }
        }
    }

    private static Set<RequestPromiseQueue> findPromiseQueues(Level level, BlockPos packagerPos) {
        Set<RequestPromiseQueue> promiseQueues = new HashSet<>();
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
                        promiseQueues.add(behaviour.restockerPromises);
                    }
                }
            }

            if (AllBlocks.STOCK_LINK.has(adjacentState)
                    && PackagerLinkBlock.getConnectedDirection(adjacentState) == direction
                    && level.getBlockEntity(adjacentPos) instanceof PackagerLinkBlockEntity link) {
                UUID frequencyId = link.behaviour.freqId;
                if (Create.LOGISTICS.hasQueuedPromises(frequencyId)) {
                    promiseQueues.add(Create.LOGISTICS.getQueuedPromises(frequencyId));
                }
            }
        }
        return promiseQueues;
    }
}
