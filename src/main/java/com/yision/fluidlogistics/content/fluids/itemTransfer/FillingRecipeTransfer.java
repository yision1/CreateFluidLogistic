/*
 * Copyright (C) 2025 DragonsPlus
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Ported from CreateDragonsPlus to CreateFluidLogistics.
 */

package com.yision.fluidlogistics.content.fluids.itemTransfer;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

public class FillingRecipeTransfer {

    private static final RecipeWrapper WRAPPER = new RecipeWrapper(new ItemStackHandler(1));

    public static boolean canItemBeFilled(Level level, ItemStack stack) {
        WRAPPER.setItem(0, stack);
        if (SequencedAssemblyRecipe.getRecipe(level, WRAPPER, AllRecipeTypes.FILLING.getType(), FillingRecipe.class)
                .isPresent()) {
            return true;
        }
        return AllRecipeTypes.FILLING.find(WRAPPER, level).isPresent();
    }

    public static OptionalInt getRequiredAmountForItem(Level level, ItemStack stack, FluidStack availableFluid) {
        Optional<FillingRecipe> recipe = findRecipe(level, stack, availableFluid);
        if (recipe.isEmpty()) {
            return OptionalInt.empty();
        }
        FluidIngredient requiredFluid = recipe.get().getRequiredFluid();
        return OptionalInt.of(requiredFluid.getRequiredAmount());
    }

    public static Optional<ItemStack> fillItem(Level level, int requiredAmount, ItemStack stack,
            FluidStack availableFluid) {
        FluidStack toFill = availableFluid.copy();
        toFill.setAmount(requiredAmount);
        Optional<FillingRecipe> recipe = findRecipe(level, stack, toFill);
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> results = recipe.get().rollResults();
        if (results.isEmpty()) {
            availableFluid.shrink(requiredAmount);
            stack.shrink(1);
            return Optional.of(ItemStack.EMPTY);
        }

        ItemStack result = results.get(0).copy();
        if (stack.hasTag()) {
            result.getOrCreateTag().merge(stack.getTag().copy());
        }
        if (result.hasTag() && result.getTag().contains("SequencedAssembly")) {
            result.getOrCreateTag().put("SequencedAssembly",
                result.getTag().getCompound("SequencedAssembly").copy());
        }

        availableFluid.shrink(requiredAmount);
        stack.shrink(1);
        return Optional.of(result);
    }

    private static Optional<FillingRecipe> findRecipe(Level level, ItemStack stack, FluidStack availableFluid) {
        WRAPPER.setItem(0, stack);
        Optional<FillingRecipe> sequencedRecipe = SequencedAssemblyRecipe.getRecipe(level,
                WRAPPER, AllRecipeTypes.FILLING.getType(), FillingRecipe.class,
                matchItemAndFluid(level, availableFluid));
        if (sequencedRecipe.isPresent()) {
            return sequencedRecipe;
        }

        for (Recipe<RecipeWrapper> recipe : level.getRecipeManager()
                .getRecipesFor(AllRecipeTypes.FILLING.getType(), WRAPPER, level)) {
            FillingRecipe fillingRecipe = (FillingRecipe) recipe;
            if (fillingRecipe.getRequiredFluid().test(availableFluid)) {
                return Optional.of(fillingRecipe);
            }
        }
        return Optional.empty();
    }

    private static Predicate<FillingRecipe> matchItemAndFluid(Level level, FluidStack availableFluid) {
        return recipe -> recipe.matches(WRAPPER, level) && recipe.getRequiredFluid().test(availableFluid);
    }
}
