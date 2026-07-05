package com.yision.fluidlogistics.api;

import java.util.List;

import com.simibubi.create.content.logistics.BigItemStack;

public record FluidCraftingRecipeData(
        int outputCount,
        int transferLimit,
        List<BigItemStack> requirements
) {
    public FluidCraftingRecipeData {
        requirements = List.copyOf(requirements);
    }
}
