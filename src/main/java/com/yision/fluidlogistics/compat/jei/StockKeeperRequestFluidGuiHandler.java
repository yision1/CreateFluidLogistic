package com.yision.fluidlogistics.compat.jei;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;

import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class StockKeeperRequestFluidGuiHandler implements IGuiContainerHandler<StockKeeperRequestScreen> {

    @Override
    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(StockKeeperRequestScreen containerScreen,
            double mouseX, double mouseY) {
        IJeiRuntime runtime = FluidLogisticsJEI.getRuntime();
        if (runtime == null) {
            return Optional.empty();
        }

        Optional<net.createmod.catnip.data.Pair<ItemStack, Rect2i>> hovered =
            containerScreen.getHoveredIngredient((int) mouseX, (int) mouseY);
        if (hovered.isEmpty()) {
            return Optional.empty();
        }

        ItemStack stack = hovered.get().getFirst();
        if (!(stack.getItem() instanceof CompressedTankItem) || !CompressedTankItem.isVirtual(stack)) {
            return Optional.empty();
        }

        FluidStack fluid = CompressedTankItem.getFluid(stack);
        if (fluid.isEmpty()) {
            return Optional.empty();
        }

        return runtime.getIngredientManager()
            .createClickableIngredient(fluid.copy(), hovered.get().getSecond(), true)
            .map(ingredient -> (IClickableIngredient<?>) ingredient);
    }

    @Override
    public @NotNull List<Rect2i> getGuiExtraAreas(StockKeeperRequestScreen containerScreen) {
        return List.of();
    }
}
