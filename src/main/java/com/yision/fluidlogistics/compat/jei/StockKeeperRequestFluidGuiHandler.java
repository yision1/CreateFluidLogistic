package com.yision.fluidlogistics.compat.jei;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import com.simibubi.create.compat.jei.CreateJEI;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.yision.fluidlogistics.item.CompressedTankItem;

import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import net.neoforged.neoforge.fluids.FluidStack;

public class StockKeeperRequestFluidGuiHandler implements IGuiContainerHandler<StockKeeperRequestScreen> {

    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(StockKeeperRequestScreen containerScreen,
            double mouseX, double mouseY) {
        if (CreateJEI.runtime == null) {
            return Optional.empty();
        }

        return containerScreen.getHoveredIngredient((int) mouseX, (int) mouseY)
                .flatMap(pair -> {
                    Object ingredient = pair.getFirst();
                    if (ingredient instanceof net.minecraft.world.item.ItemStack stack
                            && stack.getItem() instanceof CompressedTankItem
                            && CompressedTankItem.isVirtual(stack)) {
                        FluidStack fluid = CompressedTankItem.getFluid(stack);
                        if (!fluid.isEmpty()) {
                            ingredient = fluid.copy();
                        }
                    }

                    return CreateJEI.runtime.getIngredientManager()
                            .createClickableIngredient(ingredient, pair.getSecond(), true);
                });
    }

    @Override
    public @NotNull java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(
            StockKeeperRequestScreen containerScreen) {
        return java.util.List.of();
    }
}
