package com.yision.fluidlogistics.compat.jei;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.yision.fluidlogistics.compat.ghost.GhostSlotSubmitter;
import com.yision.fluidlogistics.compat.ghost.GhostSlotTargets;
import com.yision.fluidlogistics.compat.ghost.FluidGhostStacks;
import com.yision.fluidlogistics.content.equipment.handPointer.filter.HandPointerFilterScreen;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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

        if (ingredient.getType() == VanillaTypes.ITEM_STACK
                || ingredient.getType() == NeoForgeTypes.FLUID_STACK) {
            for (GhostSlotTargets.GhostSlotTarget target : GhostSlotTargets.collect(gui)) {
                targets.add(new HandPointerFilterGhostTarget<>(target.area(), target.slotIndex(), gui));
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

        public HandPointerFilterGhostTarget(Rect2i area, int slotIndex, HandPointerFilterScreen gui) {
            this.area = area;
            this.slotIndex = slotIndex;
            this.gui = gui;
        }

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            ItemStack stack = null;

            if (ingredient instanceof FluidStack fluidStack) {
                stack = FluidGhostStacks.fromFluid(fluidStack);
            } else if (ingredient instanceof ItemStack itemStack) {
                stack = itemStack.copyWithCount(1);
            }

            if (stack == null) {
                return;
            }

            GhostSlotSubmitter.submit(gui, slotIndex, stack);
        }
    }
}
