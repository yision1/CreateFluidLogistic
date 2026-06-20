package com.yision.fluidlogistics.block.FluidHatch;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import net.createmod.catnip.data.Pair;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalInt;

public final class FluidHatchItemFluidTransfer {

	private FluidHatchItemFluidTransfer() {
	}

	public static boolean tryPourContainerIntoTank(Level level, Player player, InteractionHand hand,
												   IFluidHandler targetHandler, BlockPos targetPos,
												   @Nullable FilteringBehaviour filter) {
		ItemStack transferredStack = player.getItemInHand(hand).copy();
		if (transferredStack.isEmpty())
			return false;

		IFluidHandlerItem itemCapability = getItemFluidHandler(transferredStack, false);
		if (itemCapability == null)
			return tryEmptyItemGenerically(level, player, hand, targetHandler, filter);

		for (int tank = 0; tank < itemCapability.getTanks(); tank++) {
			FluidStack fluidInItem = itemCapability.getFluidInTank(tank);
			if (fluidInItem.isEmpty())
				continue;

			if (filter != null && !filter.test(fluidInItem))
				continue;

			FluidStack toTransfer = fluidInItem.copy();
			int accepted = targetHandler.fill(toTransfer, IFluidHandler.FluidAction.SIMULATE);
			if (accepted <= 0)
				continue;

			FluidStack request = fluidInItem.copy();
			request.setAmount(accepted);
			FluidStack drained = itemCapability.drain(request, IFluidHandler.FluidAction.EXECUTE);
			if (drained.isEmpty())
				continue;

			int filled = targetHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
			if (filled <= 0) {
				itemCapability.fill(drained, IFluidHandler.FluidAction.EXECUTE);
				continue;
			}

			if (!player.isCreative()) {
				transferredStack.shrink(1);
				replaceItem(player, hand, transferredStack, itemCapability.getContainer().copy());
			}
			return true;
		}

		return tryEmptyItemGenerically(level, player, hand, targetHandler, filter);
	}

	public static boolean tryFillContainerFromTank(Level level, Player player, InteractionHand hand,
												   IFluidHandler targetHandler, BlockPos targetPos,
												   @Nullable FilteringBehaviour filter) {
		ItemStack transferredStack = player.getItemInHand(hand).copy();
		if (transferredStack.isEmpty())
			return false;

		if (tryFillItemWithExtraHandler(level, player, hand, targetHandler, filter))
			return true;

		if (FluidHatchFillingRecipeTransfer.tryFillItemWithRecipe(level, player, hand, targetHandler, filter))
			return true;

		IFluidHandlerItem itemCapability = getItemFluidHandler(transferredStack, true);
		if (itemCapability == null)
			return tryFillItemGenerically(level, player, hand, targetHandler, filter);

		for (int tank = 0; tank < targetHandler.getTanks(); tank++) {
			FluidStack fluidInTank = targetHandler.getFluidInTank(tank);
			if (fluidInTank.isEmpty())
				continue;

			if (filter != null && !filter.test(fluidInTank))
				continue;

			FluidStack preview = fluidInTank.copy();
			preview.setAmount(Math.min(fluidInTank.getAmount(), 1000));
			int accepted = itemCapability.fill(preview, IFluidHandler.FluidAction.SIMULATE);
			if (accepted <= 0)
				continue;

			FluidStack request = fluidInTank.copy();
			request.setAmount(accepted);
			FluidStack drained = targetHandler.drain(request, IFluidHandler.FluidAction.EXECUTE);
			if (drained.isEmpty())
				continue;

			int filled = itemCapability.fill(drained, IFluidHandler.FluidAction.EXECUTE);
			if (filled <= 0) {
				targetHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
				continue;
			}

			if (filled < drained.getAmount()) {
				FluidStack surplus = drained.copy();
				surplus.setAmount(drained.getAmount() - filled);
				targetHandler.fill(surplus, IFluidHandler.FluidAction.EXECUTE);
			}

			if (!player.isCreative()) {
				transferredStack.shrink(1);
				replaceItem(player, hand, transferredStack, itemCapability.getContainer().copy());
			}
			return true;
		}

		return tryFillItemGenerically(level, player, hand, targetHandler, filter);
	}

	public static boolean canItemBeEmptied(Level level, ItemStack stack) {
		return GenericItemEmptying.canItemBeEmptied(level, stack) || canItemBeEmptied(stack);
	}

	private static boolean canItemBeEmptied(ItemStack stack) {
		IFluidHandlerItem itemCapability = getItemFluidHandler(stack, false);
		if (itemCapability == null)
			return false;
		for (int tank = 0; tank < itemCapability.getTanks(); tank++) {
			if (!itemCapability.getFluidInTank(tank).isEmpty())
				return true;
		}
		return false;
	}

	public static boolean canItemBeFilled(Level level, ItemStack stack) {
		return FluidHatchFillingRecipeTransfer.canItemBeFilled(level, stack)
			|| canItemBeFilled(stack)
			|| GenericItemFilling.canItemBeFilled(level, stack);
	}

	private static boolean canItemBeFilled(ItemStack stack) {
		IFluidHandlerItem itemCapability = getItemFluidHandler(stack, true);
		if (itemCapability == null)
			return false;
		for (int tank = 0; tank < itemCapability.getTanks(); tank++) {
			if (itemCapability.getFluidInTank(tank).getAmount() < itemCapability.getTankCapacity(tank))
				return true;
		}
		return false;
	}

	private static boolean tryEmptyItemGenerically(Level level, Player player, InteractionHand hand,
												   IFluidHandler targetHandler, @Nullable FilteringBehaviour filter) {
		ItemStack heldItem = player.getItemInHand(hand);
		if (!GenericItemEmptying.canItemBeEmptied(level, heldItem))
			return false;

		Pair<FluidStack, ItemStack> emptying = GenericItemEmptying.emptyItem(level, heldItem, true);
		FluidStack fluidStack = emptying.getFirst();
		if (fluidStack.isEmpty())
			return false;
		if (filter != null && !filter.test(fluidStack))
			return false;
		if (fluidStack.getAmount() != targetHandler.fill(fluidStack.copy(), IFluidHandler.FluidAction.SIMULATE))
			return false;

		ItemStack transferredStack = heldItem.copy();
		emptying = GenericItemEmptying.emptyItem(level, transferredStack, false);
		FluidStack transferredFluid = emptying.getFirst();
		if (transferredFluid.isEmpty())
			return false;

		int realFill = targetHandler.fill(transferredFluid.copy(), IFluidHandler.FluidAction.SIMULATE);
		if (realFill == 0)
			return false;
		targetHandler.fill(transferredFluid.copy(), IFluidHandler.FluidAction.EXECUTE);
		if (!player.isCreative())
			replaceItem(player, hand, transferredStack, emptying.getSecond());
		return true;
	}

	private static boolean tryFillItemWithExtraHandler(Level level, Player player, InteractionHand hand,
													   IFluidHandler targetHandler, @Nullable FilteringBehaviour filter) {
		ItemStack heldItem = player.getItemInHand(hand);
		for (int tank = 0; tank < targetHandler.getTanks(); tank++) {
			FluidStack fluidInTank = targetHandler.getFluidInTank(tank);
			if (fluidInTank.isEmpty())
				continue;
			if (filter != null && !filter.test(fluidInTank))
				continue;

			OptionalInt requiredAmount = FluidHatchItemFilling.getRequiredAmountForExtraHandler(heldItem, fluidInTank.copy());
			if (requiredAmount.isEmpty() || requiredAmount.getAsInt() > fluidInTank.getAmount())
				continue;

			FluidStack toDrain = fluidInTank.copy();
			toDrain.setAmount(requiredAmount.getAsInt());
			FluidStack simulatedDrain = targetHandler.drain(toDrain.copy(), IFluidHandler.FluidAction.SIMULATE);
			if (simulatedDrain.isEmpty() || simulatedDrain.getAmount() != requiredAmount.getAsInt())
				continue;

			ItemStack transferredStack = heldItem.copy();
			Optional<ItemStack> result = FluidHatchItemFilling.fillItemWithExtraHandler(
				requiredAmount.getAsInt(), transferredStack, fluidInTank.copy());
			if (result.isEmpty() || result.get().isEmpty())
				continue;

			targetHandler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
			if (!player.isCreative())
				replaceItem(player, hand, transferredStack, result.get());
			return true;
		}
		return false;
	}

	private static boolean tryFillItemGenerically(Level level, Player player, InteractionHand hand,
												  IFluidHandler targetHandler, @Nullable FilteringBehaviour filter) {
		ItemStack heldItem = player.getItemInHand(hand);
		if (!GenericItemFilling.canItemBeFilled(level, heldItem))
			return false;

		for (int tank = 0; tank < targetHandler.getTanks(); tank++) {
			FluidStack fluidInTank = targetHandler.getFluidInTank(tank);
			if (fluidInTank.isEmpty())
				continue;
			if (filter != null && !filter.test(fluidInTank))
				continue;

			int requiredAmount = FluidHatchItemFilling.getRequiredAmountForItem(level, heldItem, fluidInTank.copy());
			if (requiredAmount == -1 || requiredAmount > fluidInTank.getAmount())
				continue;

			FluidStack toDrain = fluidInTank.copy();
			toDrain.setAmount(requiredAmount);
			FluidStack simulatedDrain = targetHandler.drain(toDrain.copy(), IFluidHandler.FluidAction.SIMULATE);
			if (simulatedDrain.isEmpty() || simulatedDrain.getAmount() != requiredAmount)
				continue;

			ItemStack transferredStack = heldItem.copy();
			ItemStack result = FluidHatchItemFilling.fillItem(level, requiredAmount, transferredStack, fluidInTank.copy());
			if (result.isEmpty())
				continue;

			targetHandler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
			if (!player.isCreative())
				replaceItem(player, hand, transferredStack, result);
			return true;
		}
		return false;
	}

	private static IFluidHandlerItem getItemFluidHandler(ItemStack stack, boolean forFilling) {
		ItemStack split = stack.copy();
		split.setCount(1);
		IFluidHandlerItem itemCapability = split.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
		if (itemCapability == null)
			return null;
		if (forFilling && !GenericItemFilling.isFluidHandlerValid(split, itemCapability))
			return null;
		return itemCapability;
	}

	private static void replaceItem(Player player, InteractionHand hand, ItemStack stack, ItemStack result) {
		if (stack.isEmpty()) {
			player.setItemInHand(hand, result);
		} else {
			player.setItemInHand(hand, stack);
			player.getInventory().placeItemBackInInventory(result);
		}
	}
}
