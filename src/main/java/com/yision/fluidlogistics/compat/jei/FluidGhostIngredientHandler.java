package com.yision.fluidlogistics.compat.jei;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.annotation.ParametersAreNonnullByDefault;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.yision.fluidlogistics.compat.ghost.GhostSlotSubmitter;
import com.yision.fluidlogistics.compat.ghost.GhostSlotTargets;
import com.yision.fluidlogistics.compat.ghost.VirtualFluidGhostStacks;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.Rect2i;
import net.minecraftforge.fluids.FluidStack;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class FluidGhostIngredientHandler<T extends AbstractSimiContainerScreen<? extends GhostItemMenu<?>>>
        implements IGhostIngredientHandler<T> {

    private final BiConsumer<T, Integer> afterSubmit;

    public FluidGhostIngredientHandler() {
        this((gui, slotIndex) -> {});
    }

    public FluidGhostIngredientHandler(BiConsumer<T, Integer> afterSubmit) {
        this.afterSubmit = afterSubmit;
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new LinkedList<>();

        if (ingredient.getType() == ForgeTypes.FLUID_STACK) {
            for (GhostSlotTargets.GhostSlotTarget target : GhostSlotTargets.collect(gui)) {
                targets.add(new FluidGhostTarget<>(target.area(), target.slotIndex(), gui, afterSubmit));
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

    private static class FluidGhostTarget<I, T extends AbstractSimiContainerScreen<? extends GhostItemMenu<?>>> implements Target<I> {

        private final Rect2i area;
        private final T gui;
        private final int slotIndex;
        private final BiConsumer<T, Integer> afterSubmit;

        FluidGhostTarget(Rect2i area, int slotIndex, T gui, BiConsumer<T, Integer> afterSubmit) {
            this.area = area;
            this.slotIndex = slotIndex;
            this.gui = gui;
            this.afterSubmit = afterSubmit;
        }

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof FluidStack fluidStack)) {
                return;
            }

            var stack = VirtualFluidGhostStacks.fromFluid(fluidStack);
            GhostSlotSubmitter.submit(gui, slotIndex, stack);
            afterSubmit.accept(gui, slotIndex);
        }
    }
}
