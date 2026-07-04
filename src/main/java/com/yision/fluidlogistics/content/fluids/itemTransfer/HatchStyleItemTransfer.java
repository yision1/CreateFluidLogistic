package com.yision.fluidlogistics.content.fluids.itemTransfer;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.yision.fluidlogistics.content.fluids.itemTransfer.ItemFluidCapabilityTransfer.TransferResult;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class HatchStyleItemTransfer {
    private HatchStyleItemTransfer() {
    }

    public static FluidStack tryEmptyItem(Level level, Player player, InteractionHand hand, ItemStack stack,
            IFluidHandler tank, FilteringBehaviour filter, boolean tankIsCreative, Runnable onChanged) {
        ItemStack transferredStack = stack.copy();
        TransferResult transfer = ItemFluidCapabilityTransfer.tryDrainItemToTank(transferredStack, tank, filter);
        if (!transfer.isEmpty()) {
            onChanged.run();

            if (!player.isCreative() && !tankIsCreative)
                replaceItem(player, hand, transferredStack, transfer.result());
            return transfer.fluidStack();
        }

        if (!GenericItemEmptying.canItemBeEmptied(level, stack))
            return FluidStack.EMPTY;

        Pair<FluidStack, ItemStack> emptying = GenericItemEmptying.emptyItem(level, stack, true);
        FluidStack fluidStack = emptying.getFirst();

        if (!filter.test(fluidStack))
            return FluidStack.EMPTY;

        if (fluidStack.getAmount() != tank.fill(fluidStack, FluidAction.SIMULATE))
            return FluidStack.EMPTY;
        if (level.isClientSide)
            return fluidStack;

        ItemStack copy = stack.copy();
        emptying = GenericItemEmptying.emptyItem(level, copy, false);

        int realFill = tank.fill(fluidStack.copy(), FluidAction.SIMULATE);
        if (realFill == 0) return fluidStack;
        tank.fill(fluidStack.copy(), FluidAction.EXECUTE);
        onChanged.run();

        if (!player.isCreative() && !tankIsCreative) {
            replaceItem(player, hand, copy, emptying.getSecond());
        }
        return fluidStack;
    }

    public static FluidStack tryFillItem(Level level, Player player, InteractionHand hand, ItemStack stack,
            IFluidHandler tank, FilteringBehaviour filter, boolean tankIsCreative, Runnable onChanged) {
        FluidStack fluidStack = tryFillItemWithExtraHandler(level, player, hand, stack, tank, filter, tankIsCreative, onChanged);
        if (!fluidStack.isEmpty())
            return fluidStack;

        fluidStack = tryFillItemWithFillingRecipe(level, player, hand, stack, tank, filter, tankIsCreative, onChanged);
        if (!fluidStack.isEmpty())
            return fluidStack;

        ItemStack transferredStack = stack.copy();
        TransferResult transfer = ItemFluidCapabilityTransfer.tryFillItemFromTank(transferredStack, tank, filter);
        if (!transfer.isEmpty()) {
            onChanged.run();

            if (!player.isCreative())
                replaceItem(player, hand, transferredStack, transfer.result());
            return transfer.fluidStack();
        }

        if (!GenericItemFilling.canItemBeFilled(level, stack))
            return FluidStack.EMPTY;

        for (int i = 0; i < tank.getTanks(); i++) {
            fluidStack = tank.getFluidInTank(i);
            if (fluidStack.isEmpty() || !filter.test(fluidStack))
                continue;
            int requiredAmountForItem = ItemFilling.getRequiredAmountForItem(level, stack, fluidStack.copy());
            if (requiredAmountForItem == -1)
                continue;
            if (requiredAmountForItem > fluidStack.getAmount())
                continue;

            FluidStack fluidCopy = fluidStack.copy();
            fluidCopy.setAmount(requiredAmountForItem);

            FluidStack realDraw = tank.drain(fluidCopy, FluidAction.SIMULATE);
            if (realDraw.isEmpty() || realDraw.getAmount() != requiredAmountForItem)
                continue;

            if (level.isClientSide)
                return fluidCopy;

            ItemStack workingStack = player.isCreative() || tankIsCreative
                    ? stack.copy()
                    : stack;
            ItemStack result = ItemFilling.fillItem(level, requiredAmountForItem, workingStack, fluidStack.copy());
            if (result.isEmpty())
                continue;
            tank.drain(fluidCopy, FluidAction.EXECUTE);

            if (!player.isCreative())
                replaceItem(player, hand, workingStack, result);
            onChanged.run();
            return fluidCopy;
        }
        return FluidStack.EMPTY;
    }

    private static FluidStack tryFillItemWithFillingRecipe(Level level, Player player, InteractionHand hand,
            ItemStack stack, IFluidHandler tank, FilteringBehaviour filter, boolean tankIsCreative, Runnable onChanged) {
        for (int i = 0; i < tank.getTanks(); i++) {
            FluidStack fluidStack = tank.getFluidInTank(i);
            if (fluidStack.isEmpty() || !filter.test(fluidStack))
                continue;

            var requiredAmount = FillingRecipeTransfer.getRequiredAmountForItem(level, stack, fluidStack.copy());
            if (requiredAmount.isEmpty())
                continue;
            int requiredAmountForItem = requiredAmount.getAsInt();
            if (requiredAmountForItem > fluidStack.getAmount())
                continue;

            FluidStack fluidCopy = fluidStack.copy();
            fluidCopy.setAmount(requiredAmountForItem);

            FluidStack realDraw = tank.drain(fluidCopy, FluidAction.SIMULATE);
            if (realDraw.isEmpty() || realDraw.getAmount() != requiredAmountForItem)
                continue;

            if (level.isClientSide)
                return fluidCopy;

            ItemStack workingStack = player.isCreative() || tankIsCreative
                    ? stack.copy()
                    : stack;
            var result = FillingRecipeTransfer.fillItem(level, requiredAmountForItem, workingStack, fluidStack.copy());
            if (result.isEmpty())
                continue;

            tank.drain(fluidCopy, FluidAction.EXECUTE);

            if (!player.isCreative())
                replaceItem(player, hand, workingStack, result.get());
            onChanged.run();
            return fluidCopy;
        }
        return FluidStack.EMPTY;
    }

    private static FluidStack tryFillItemWithExtraHandler(Level level, Player player, InteractionHand hand,
            ItemStack stack, IFluidHandler tank, FilteringBehaviour filter, boolean tankIsCreative, Runnable onChanged) {
        for (int i = 0; i < tank.getTanks(); i++) {
            FluidStack fluidStack = tank.getFluidInTank(i);
            if (fluidStack.isEmpty() || !filter.test(fluidStack))
                continue;

            var requiredAmount = ItemFilling.getRequiredAmountForExtraHandler(stack, fluidStack.copy());
            if (requiredAmount.isEmpty())
                continue;
            int requiredAmountForItem = requiredAmount.getAsInt();
            if (requiredAmountForItem > fluidStack.getAmount())
                continue;

            FluidStack fluidCopy = fluidStack.copy();
            fluidCopy.setAmount(requiredAmountForItem);

            FluidStack realDraw = tank.drain(fluidCopy, FluidAction.SIMULATE);
            if (realDraw.isEmpty() || realDraw.getAmount() != requiredAmountForItem)
                continue;

            if (level.isClientSide)
                return fluidCopy;

            ItemStack workingStack = player.isCreative() || tankIsCreative
                    ? stack.copy()
                    : stack;
            var result = ItemFilling.fillItemWithExtraHandler(requiredAmountForItem, workingStack, fluidStack.copy());
            if (result.isEmpty())
                continue;
            tank.drain(fluidCopy, FluidAction.EXECUTE);

            if (!player.isCreative())
                replaceItem(player, hand, workingStack, result.get());
            onChanged.run();
            return fluidCopy;
        }
        return FluidStack.EMPTY;
    }

    private static void replaceItem(Player player, InteractionHand hand, ItemStack stack, ItemStack result) {
        if (stack.isEmpty()) {
            player.setItemInHand(hand, result);
        } else {
            player.setItemInHand(hand, stack);
            player.getInventory().placeItemBackInInventory(result);
        }
    }

    public static boolean canItemBeEmptied(Level level, ItemStack stack) {
        return GenericItemEmptying.canItemBeEmptied(level, stack)
                || ItemFluidCapabilityTransfer.canItemBeEmptied(stack);
    }

    public static boolean canItemBeFilled(Level level, ItemStack stack) {
        return FillingRecipeTransfer.canItemBeFilled(level, stack)
                || ItemFluidCapabilityTransfer.canItemBeFilled(stack)
                || GenericItemFilling.canItemBeFilled(level, stack);
    }
}
