package com.yision.fluidlogistics.util;

import java.util.List;

import com.simibubi.create.content.logistics.BigItemStack;

public interface IFluidCraftableBigItemStack {

    void fluidlogistics$setCustomRecipeData(int outputCount, int transferLimit, List<BigItemStack> requirements);

    boolean fluidlogistics$hasCustomRecipeData();

    int fluidlogistics$getCustomOutputCount();

    int fluidlogistics$getCustomTransferLimit();

    List<BigItemStack> fluidlogistics$getCustomRequirements();
}
