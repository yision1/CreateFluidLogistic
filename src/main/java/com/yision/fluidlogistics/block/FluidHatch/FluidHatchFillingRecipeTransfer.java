package com.yision.fluidlogistics.block.FluidHatch;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.fluid.FluidIngredient;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

public final class FluidHatchFillingRecipeTransfer {

	private static final RecipeWrapper WRAPPER = new RecipeWrapper(new ItemStackHandler(1));

	private FluidHatchFillingRecipeTransfer() {
	}

	public static boolean canItemBeFilled(Level level, ItemStack stack) {
		WRAPPER.setItem(0, stack);
		if (SequencedAssemblyRecipe.getRecipe(level, WRAPPER, AllRecipeTypes.FILLING.getType(), FillingRecipe.class)
			.isPresent())
			return true;
		return AllRecipeTypes.FILLING.find(WRAPPER, level).isPresent();
	}

	/**
	 * Try to fill the player's held item using a Filling Recipe or Sequenced Assembly
	 * from the available fluids in the target handler.
	 */
	public static boolean tryFillItemWithRecipe(Level level, Player player, InteractionHand hand,
												IFluidHandler targetHandler,
												@Nullable FilteringBehaviour filter) {
		ItemStack heldItem = player.getItemInHand(hand);
		if (heldItem.isEmpty())
			return false;

		for (int tank = 0; tank < targetHandler.getTanks(); tank++) {
			FluidStack available = targetHandler.getFluidInTank(tank);
			if (available.isEmpty())
				continue;

			if (filter != null && !filter.test(available))
				continue;

			OptionalInt requiredAmount = getRequiredAmountForItem(level, heldItem, available.copy());
			if (requiredAmount.isEmpty() || requiredAmount.getAsInt() > available.getAmount())
				continue;

			FluidStack toDrain = available.copy();
			toDrain.setAmount(requiredAmount.getAsInt());
			FluidStack simulatedDrain = targetHandler.drain(toDrain.copy(), IFluidHandler.FluidAction.SIMULATE);
			if (simulatedDrain.isEmpty() || simulatedDrain.getAmount() != requiredAmount.getAsInt())
				continue;

			ItemStack transferredStack = heldItem.copy();
			ItemStack result = fillItem(level, requiredAmount.getAsInt(), transferredStack, available.copy());
			if (!result.isEmpty()) {
				targetHandler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
				if (!player.isCreative())
					replaceItem(player, hand, transferredStack, result);
				return true;
			}
		}

		return false;
	}

	private static OptionalInt getRequiredAmountForItem(Level level, ItemStack stack, FluidStack availableFluid) {
		WRAPPER.setItem(0, stack);

		Optional<FillingRecipe> assemblyRecipe = SequencedAssemblyRecipe.getRecipe(
			level, WRAPPER, AllRecipeTypes.FILLING.getType(), FillingRecipe.class,
			matchItemAndFluid(level, availableFluid));
		if (assemblyRecipe.isPresent()) {
			FluidIngredient requiredFluid = assemblyRecipe.get().getRequiredFluid();
			if (requiredFluid.test(availableFluid)) {
				return OptionalInt.of(requiredFluid.getRequiredAmount());
			}
		}

		for (Recipe<RecipeWrapper> recipe : level.getRecipeManager()
			.getRecipesFor(AllRecipeTypes.FILLING.getType(), WRAPPER, level)) {
			FillingRecipe fillingRecipe = (FillingRecipe) recipe;
			FluidIngredient requiredFluid = fillingRecipe.getRequiredFluid();
			if (requiredFluid.test(availableFluid)) {
				return OptionalInt.of(requiredFluid.getRequiredAmount());
			}
		}

		return OptionalInt.empty();
	}

	private static ItemStack fillItem(Level level, int requiredAmount, ItemStack stack,
									  FluidStack availableFluid) {
		FluidStack toFill = availableFluid.copy();
		toFill.setAmount(requiredAmount);
		WRAPPER.setItem(0, stack);

		FillingRecipe fillingRecipe = SequencedAssemblyRecipe
			.getRecipe(level, WRAPPER, AllRecipeTypes.FILLING.getType(), FillingRecipe.class,
				matchItemAndFluid(level, availableFluid))
			.filter(recipe -> recipe.getRequiredFluid().test(toFill))
			.orElseGet(() -> {
				for (Recipe<RecipeWrapper> recipe : level.getRecipeManager()
					.getRecipesFor(AllRecipeTypes.FILLING.getType(), WRAPPER, level)) {
					FillingRecipe candidate = (FillingRecipe) recipe;
					if (candidate.getRequiredFluid().test(toFill)) {
						return candidate;
					}
				}
				return null;
			});

		if (fillingRecipe != null) {
			List<ItemStack> results = fillingRecipe.rollResults();
			if (results.isEmpty())
				return ItemStack.EMPTY;

			ItemStack result = results.get(0).copy();
			// Preserve input NBT (SequencedAssembly tags etc.)
			if (stack.hasTag()) {
				result.getOrCreateTag().merge(stack.getTag().copy());
			}
			if (result.hasTag() && result.getTag().contains("SequencedAssembly")) {
				result.getOrCreateTag().put("SequencedAssembly",
					result.getTag().getCompound("SequencedAssembly").copy());
			}

			availableFluid.shrink(requiredAmount);
			stack.shrink(1);
			return result;
		}

		return ItemStack.EMPTY;
	}

	private static Predicate<FillingRecipe> matchItemAndFluid(Level level, FluidStack availableFluid) {
		return recipe -> recipe.matches(WRAPPER, level) && recipe.getRequiredFluid().test(availableFluid);
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
