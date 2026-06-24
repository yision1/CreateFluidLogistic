package com.yision.fluidlogistics.compat.jei;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import com.yision.fluidlogistics.handpointer.filter.HandPointerFilterScreen;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class HandPointerFilterGhostHandler
        implements IGhostIngredientHandler<HandPointerFilterScreen> {

    public static final HandPointerFilterGhostHandler INSTANCE = new HandPointerFilterGhostHandler();

    private HandPointerFilterGhostHandler() {
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(HandPointerFilterScreen gui, ITypedIngredient<I> ingredient,
            boolean doStart) {
        List<Target<I>> targets = new LinkedList<>();

        if (ingredient.getType() == VanillaTypes.ITEM_STACK) {
            for (int i = 36; i < gui.getMenu().slots.size(); i++) {
                if (gui.getMenu().slots.get(i).isActive()) {
                    targets.add(new HandPointerFilterGhostTarget(gui, i - 36));
                }
            }
        }

        return targets;
    }

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

    private static class HandPointerFilterGhostTarget<I> implements Target<I> {

        private final Rect2i area;
        private final HandPointerFilterScreen gui;
        private final int slotIndex;

        public HandPointerFilterGhostTarget(HandPointerFilterScreen gui, int slotIndex) {
            this.gui = gui;
            this.slotIndex = slotIndex;
            Slot slot = gui.getMenu().slots.get(slotIndex + 36);
            this.area = new Rect2i(gui.getGuiLeft() + slot.x, gui.getGuiTop() + slot.y, 16, 16);
        }

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof ItemStack itemStack)) {
                return;
            }

            ItemStack copy = itemStack.copyWithCount(1);
            gui.getMenu().ghostInventory.setStackInSlot(slotIndex, copy);
            CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(copy, slotIndex));
        }
    }
}
