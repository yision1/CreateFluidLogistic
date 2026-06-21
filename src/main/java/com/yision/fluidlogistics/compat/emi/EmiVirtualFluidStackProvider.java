package com.yision.fluidlogistics.compat.emi;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.yision.fluidlogistics.item.CompressedTankItem;

import dev.emi.emi.api.EmiStackProvider;
import dev.emi.emi.api.neoforge.NeoForgeEmiStack;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class EmiVirtualFluidStackProvider {

    private EmiVirtualFluidStackProvider() {
    }

    public static EmiStackInteraction forGhostSlotScreen(AbstractContainerScreen<?> gui, int x, int y) {
        for (int i = 36; i < gui.getMenu().slots.size(); i++) {
            Slot slot = gui.getMenu().slots.get(i);
            if (!slot.isActive()) {
                continue;
            }
            Rect2i area = new Rect2i(gui.getGuiLeft() + slot.x, gui.getGuiTop() + slot.y, 16, 16);
            if (!area.contains(x, y)) {
                continue;
            }
            ItemStack ghost = slot.getItem();
            if (ghost.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(ghost)) {
                FluidStack fluid = CompressedTankItem.getFluid(ghost);
                if (!fluid.isEmpty()) {
                    return new EmiStackInteraction(NeoForgeEmiStack.of(fluid), null, false);
                }
            }
        }
        return EmiStackInteraction.EMPTY;
    }

    public static class FactoryPanel implements EmiStackProvider<FactoryPanelSetItemScreen> {
        public static final FactoryPanel INSTANCE = new FactoryPanel();

        @Override
        public EmiStackInteraction getStackAt(FactoryPanelSetItemScreen screen, int x, int y) {
            return forGhostSlotScreen(screen, x, y);
        }
    }

    public static class RedstoneRequester implements EmiStackProvider<RedstoneRequesterScreen> {
        public static final RedstoneRequester INSTANCE = new RedstoneRequester();

        @Override
        public EmiStackInteraction getStackAt(RedstoneRequesterScreen screen, int x, int y) {
            return forGhostSlotScreen(screen, x, y);
        }
    }

    public static class StockKeeper implements EmiStackProvider<StockKeeperRequestScreen> {
        public static final StockKeeper INSTANCE = new StockKeeper();

        @Override
        public EmiStackInteraction getStackAt(StockKeeperRequestScreen containerScreen, int x, int y) {
            return containerScreen.getHoveredIngredient(x, y)
                    .map(pair -> toInteraction(pair.getFirst()))
                    .orElse(EmiStackInteraction.EMPTY);
        }

        private static EmiStackInteraction toInteraction(Object ingredient) {
            if (ingredient instanceof ItemStack stack
                    && stack.getItem() instanceof CompressedTankItem
                    && CompressedTankItem.isVirtual(stack)) {
                FluidStack fluid = CompressedTankItem.getFluid(stack);
                if (!fluid.isEmpty()) {
                    return new EmiStackInteraction(NeoForgeEmiStack.of(fluid), null, false);
                }
                return EmiStackInteraction.EMPTY;
            }
            if (ingredient instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                return new EmiStackInteraction(EmiStack.of(itemStack), null, true);
            }
            if (ingredient instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                return new EmiStackInteraction(NeoForgeEmiStack.of(fluidStack), null, false);
            }
            return EmiStackInteraction.EMPTY;
        }
    }
}
