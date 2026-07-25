package com.yision.fluidlogistics.api.packager;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts.CraftingEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public record PackageUnpackContext(
        Level level,
        BlockPos targetPos,
        BlockState targetState,
        @Nullable BlockEntity targetBlockEntity,
        @Nullable Direction side,
        ItemStack packageStack,
        @Nullable PackageOrderWithCrafts orderContext) {
    public PackageUnpackContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(targetPos, "targetPos");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(packageStack, "packageStack");
        packageStack = packageStack.copy();
        orderContext = copyOrderContext(orderContext);
    }

    @Override
    public ItemStack packageStack() {
        return packageStack.copy();
    }

    @Override
    @Nullable
    public PackageOrderWithCrafts orderContext() {
        return copyOrderContext(orderContext);
    }

    private static PackageOrderWithCrafts copyOrderContext(@Nullable PackageOrderWithCrafts context) {
        if (context == null) {
            return null;
        }
        PackageOrder orderedStacks = copyOrder(context.orderedStacks());
        List<CraftingEntry> orderedCrafts = context.orderedCrafts().stream()
                .map(entry -> new CraftingEntry(copyOrder(entry.pattern()), entry.count()))
                .toList();
        return new PackageOrderWithCrafts(orderedStacks, orderedCrafts);
    }

    private static PackageOrder copyOrder(PackageOrder order) {
        return new PackageOrder(order.stacks().stream()
                .map(PackageUnpackContext::copyStack)
                .toList());
    }

    private static BigItemStack copyStack(BigItemStack stack) {
        return new BigItemStack(stack.stack.copy(), stack.count);
    }
}
