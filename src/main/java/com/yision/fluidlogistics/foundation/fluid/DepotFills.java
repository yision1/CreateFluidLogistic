package com.yision.fluidlogistics.foundation.fluid;

import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class DepotFills {

    private DepotFills() {
    }

    public static boolean isDepot(@Nullable BlockEntity entity) {
        return entity != null && DepotBehaviour.get(entity, DepotBehaviour.TYPE) != null;
    }

    public static ItemStack getItemOnDepot(@Nullable BlockEntity depot) {
        DepotBehaviour behaviour = depot == null ? null : DepotBehaviour.get(depot, DepotBehaviour.TYPE);
        return behaviour == null ? ItemStack.EMPTY : behaviour.getHeldItemStack();
    }

    public static boolean completeItemFill(TransportedItemStackHandlerBehaviour handler,
        TransportedItemStack transported, ItemStack resultStack) {
        if (resultStack.isEmpty()) {
            return false;
        }

        transported.clearFanProcessingData();
        handler.handleProcessingOnItem(transported, createResult(transported, resultStack));
        return true;
    }

    public static boolean fillFirstMatchingItem(TransportedItemStackHandlerBehaviour handler,
        Predicate<TransportedItemStack> matcher, Function<ItemStack, ItemStack> filler) {
        boolean[] completed = {false};
        handler.handleProcessingOnAllItems(transported -> {
            if (completed[0] || !matcher.test(transported)) {
                return TransportedResult.doNothing();
            }
            ItemStack resultStack = filler.apply(transported.stack);
            if (resultStack.isEmpty()) {
                return TransportedResult.doNothing();
            }
            transported.clearFanProcessingData();
            completed[0] = true;
            return createResult(transported, resultStack);
        });
        return completed[0];
    }

    private static TransportedResult createResult(TransportedItemStack transported, ItemStack resultStack) {
        List<TransportedItemStack> outList = new ArrayList<>();
        TransportedItemStack held = null;
        TransportedItemStack result = transported.copy();
        result.stack = resultStack;
        if (!transported.stack.isEmpty()) {
            held = transported.copy();
        }
        outList.add(result);
        return TransportedResult.convertToAndLeaveHeld(outList, held);
    }

    public static @Nullable TransportedItemStackHandlerBehaviour getTransportedHandler(Level level, BlockEntity depot) {
        return BlockEntityBehaviour.get(level, depot.getBlockPos(), TransportedItemStackHandlerBehaviour.TYPE);
    }

    public static void notifyTargetUpdate(Level level, BlockEntity targetEntity) {
        level.sendBlockUpdated(targetEntity.getBlockPos(), targetEntity.getBlockState(), targetEntity.getBlockState(), 3);
        if (targetEntity instanceof SmartBlockEntity smartBlockEntity) {
            smartBlockEntity.notifyUpdate();
        }
    }
}
