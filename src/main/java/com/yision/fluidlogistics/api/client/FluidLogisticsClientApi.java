package com.yision.fluidlogistics.api.client;

import java.util.List;
import java.util.Optional;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.yision.fluidlogistics.api.FluidCraftingRecipeData;
import com.yision.fluidlogistics.api.FluidLogisticsApi;
import com.yision.fluidlogistics.render.FluidSlotAmountRenderer;
import com.yision.fluidlogistics.util.IFluidCraftableBigItemStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class FluidLogisticsClientApi {

    private FluidLogisticsClientApi() {
    }

    public static void renderStockKeeperFluidAmount(GuiGraphics graphics, int amount) {
        FluidSlotAmountRenderer.renderInStockKeeper(graphics, amount);
    }

    public static void renderPackageFluidAmount(GuiGraphics graphics, ItemStack stack, int itemX, int itemY) {
        int amount = FluidLogisticsApi.getPackageFluidAmount(stack);
        if (amount <= 0) {
            return;
        }
        FluidSlotAmountRenderer.renderAtSlotPosition(graphics, amount, itemX, itemY);
    }

    public static boolean hasFluidCraftingRecipeData(CraftableBigItemStack stack) {
        return stack instanceof IFluidCraftableBigItemStack ext
                && ext.fluidlogistics$hasCustomRecipeData();
    }

    public static Optional<FluidCraftingRecipeData> getFluidCraftingRecipeData(CraftableBigItemStack stack) {
        if (!(stack instanceof IFluidCraftableBigItemStack ext)
                || !ext.fluidlogistics$hasCustomRecipeData()) {
            return Optional.empty();
        }
        return Optional.of(new FluidCraftingRecipeData(
                ext.fluidlogistics$getCustomOutputCount(),
                ext.fluidlogistics$getCustomTransferLimit(),
                ext.fluidlogistics$getCustomRequirements()
        ));
    }

    public static void setFluidCraftingRecipeData(CraftableBigItemStack stack,
            FluidCraftingRecipeData data) {
        if (stack instanceof IFluidCraftableBigItemStack ext) {
            List<BigItemStack> requirements = data.requirements();
            ext.fluidlogistics$setCustomRecipeData(data.outputCount(), data.transferLimit(), requirements);
        }
    }
}
