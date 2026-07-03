/*
 * Copyright (C) 2025 DragonsPlus
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Ported from CreateDragonsPlus to CreateFluidLogistics.
 */

package com.yision.fluidlogistics.content.fluids.fluidHatch;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public class FluidHatchFillingRecipeTransfer {
    public static boolean canItemBeFilled(Level level, ItemStack stack) {
        SingleRecipeInput input = new SingleRecipeInput(stack);
        if (SequencedAssemblyRecipe.getRecipe(level, input, AllRecipeTypes.FILLING.getType(), FillingRecipe.class)
                .isPresent()) {
            return true;
        }
        return AllRecipeTypes.FILLING.find(input, level).isPresent();
    }

    public static OptionalInt getRequiredAmountForItem(Level level, ItemStack stack, FluidStack availableFluid) {
        return findRecipe(level, stack, availableFluid)
                .map(RecipeHolder::value)
                .map(FillingRecipe::getRequiredFluid)
                .map(SizedFluidIngredient::amount)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    public static Optional<ItemStack> fillItem(Level level, int requiredAmount, ItemStack stack,
            FluidStack availableFluid) {
        FluidStack toFill = availableFluid.copyWithAmount(requiredAmount);
        return findRecipe(level, stack, toFill)
                .map(RecipeHolder::value)
                .map(recipe -> {
                    List<ItemStack> results = recipe.rollResults(level.random);
                    availableFluid.shrink(requiredAmount);
                    stack.shrink(1);
                    return results.isEmpty() ? ItemStack.EMPTY : results.getFirst();
                });
    }

    private static Optional<RecipeHolder<FillingRecipe>> findRecipe(Level level, ItemStack stack,
            FluidStack availableFluid) {
        SingleRecipeInput input = new SingleRecipeInput(stack);
        Optional<RecipeHolder<FillingRecipe>> sequencedRecipe = SequencedAssemblyRecipe.getRecipe(level,
                input, AllRecipeTypes.FILLING.getType(), FillingRecipe.class,
                matchItemAndFluid(level, input, availableFluid));
        if (sequencedRecipe.isPresent()) {
            return sequencedRecipe;
        }

        for (RecipeHolder<Recipe<SingleRecipeInput>> recipe : level.getRecipeManager()
                .getRecipesFor(AllRecipeTypes.FILLING.getType(), input, level)) {
            FillingRecipe fillingRecipe = (FillingRecipe) recipe.value();
            if (fillingRecipe.getRequiredFluid().ingredient().test(availableFluid)) {
                return Optional.of(new RecipeHolder<>(recipe.id(), fillingRecipe));
            }
        }
        return Optional.empty();
    }

    private static Predicate<RecipeHolder<FillingRecipe>> matchItemAndFluid(Level level,
            SingleRecipeInput input, FluidStack availableFluid) {
        return recipe -> recipe.value().matches(input, level)
                && recipe.value().getRequiredFluid().ingredient().test(availableFluid);
    }
}
