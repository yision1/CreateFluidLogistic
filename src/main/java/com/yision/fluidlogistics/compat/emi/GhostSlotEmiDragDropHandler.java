package com.yision.fluidlogistics.compat.emi;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.yision.fluidlogistics.compat.ghost.GhostSlotSubmitter;
import com.yision.fluidlogistics.compat.ghost.GhostSlotTargets;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class GhostSlotEmiDragDropHandler<T extends AbstractSimiContainerScreen<? extends GhostItemMenu<?>>>
        implements EmiDragDropHandler<T> {

    public enum AcceptMode {
        FLUID_ONLY,
        ITEM_OR_FLUID
    }

    @FunctionalInterface
    public interface AfterSubmit<T> {
        void accept(T gui, int slotIndex, boolean fromFluid);
    }

    private final AcceptMode acceptMode;
    private final AfterSubmit<T> afterSubmit;

    public GhostSlotEmiDragDropHandler(AcceptMode acceptMode) {
        this(acceptMode, (gui, slotIndex, fromFluid) -> {});
    }

    public GhostSlotEmiDragDropHandler(AcceptMode acceptMode, AfterSubmit<T> afterSubmit) {
        this.acceptMode = acceptMode;
        this.afterSubmit = afterSubmit;
    }

    @Override
    public boolean dropStack(T gui, EmiIngredient stack, int x, int y) {
        if (!shouldAccept(stack)) {
            return false;
        }

        GhostSlotTargets.GhostSlotTarget target = GhostSlotTargets.hit(gui, x, y);
        if (target == null) {
            return false;
        }

        ItemStack ghost = EmiIngredientHelper.toGhostStack(stack);
        if (ghost == null) {
            return false;
        }

        boolean fromFluid = EmiIngredientHelper.getFirstFluid(stack) != null;
        GhostSlotSubmitter.submit(gui, target.slotIndex(), ghost);
        afterSubmit.accept(gui, target.slotIndex(), fromFluid);
        return true;
    }

    @Override
    public void render(T gui, EmiIngredient dragged, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!shouldAccept(dragged)) {
            return;
        }

        for (GhostSlotTargets.GhostSlotTarget target : GhostSlotTargets.collect(gui)) {
            graphics.fill(target.area().getX(), target.area().getY(),
                target.area().getX() + 16, target.area().getY() + 16, 0x8822BB33);
        }
    }

    private boolean shouldAccept(EmiIngredient ingredient) {
        if (ingredient.isEmpty()) {
            return false;
        }
        if (acceptMode == AcceptMode.FLUID_ONLY) {
            return EmiIngredientHelper.getFirstFluid(ingredient) != null;
        }
        return true;
    }
}
