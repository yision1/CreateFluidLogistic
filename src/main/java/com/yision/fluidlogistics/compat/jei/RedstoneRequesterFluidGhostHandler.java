package com.yision.fluidlogistics.compat.jei;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.simibubi.create.AllPackets;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.mixin.accessor.RedstoneRequesterScreenAccessor;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class RedstoneRequesterFluidGhostHandler
    implements IGhostIngredientHandler<RedstoneRequesterScreen> {

    public static final RedstoneRequesterFluidGhostHandler INSTANCE = new RedstoneRequesterFluidGhostHandler();

    private RedstoneRequesterFluidGhostHandler() {
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(RedstoneRequesterScreen gui, ITypedIngredient<I> ingredient,
        boolean doStart) {
        List<Target<I>> targets = new LinkedList<>();

        if (ingredient.getType() == ForgeTypes.FLUID_STACK) {
            for (int i = 36; i < gui.getMenu().slots.size(); i++) {
                if (gui.getMenu().slots.get(i).isActive()) {
                    targets.add(new RequesterGhostTarget<>(gui, i - 36));
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

    private static class RequesterGhostTarget<I> implements Target<I> {

        private final Rect2i area;
        private final RedstoneRequesterScreen gui;
        private final int slotIndex;

        private RequesterGhostTarget(RedstoneRequesterScreen gui, int slotIndex) {
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
            if (!(ingredient instanceof FluidStack fluidStack)) {
                return;
            }

            ItemStack stack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            FluidStack template = fluidStack.copy();
            template.setAmount(1);
            CompressedTankItem.setFluidVirtual(stack, template);

            gui.getMenu().ghostInventory.setStackInSlot(slotIndex, stack);
            ((RedstoneRequesterScreenAccessor) gui).getAmounts()
                .set(slotIndex, FluidAmountHelper.DEFAULT_FLUID_REQUEST_AMOUNT);

            AllPackets.getChannel().sendToServer(new GhostItemSubmitPacket(stack, slotIndex));
        }
    }
}
