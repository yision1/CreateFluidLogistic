package com.yision.fluidlogistics.compat.jei;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import com.yision.fluidlogistics.client.RedstoneRequesterAmountsAccess;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

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

        boolean acceptsItem = ingredient.getType() == VanillaTypes.ITEM_STACK && ModList.get().isLoaded("emi");
        boolean acceptsFluid = ingredient.getType() == NeoForgeTypes.FLUID_STACK;

        if (acceptsItem || acceptsFluid) {
            for (int i = 36; i < gui.getMenu().slots.size(); i++) {
                if (gui.getMenu().slots.get(i).isActive()) {
                    targets.add(new RequesterGhostTarget(gui, i - 36));
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

        public RequesterGhostTarget(RedstoneRequesterScreen gui, int slotIndex) {
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
            ItemStack stack;
            boolean fluidRequest = false;

            if (ingredient instanceof ItemStack itemStack) {
                stack = itemStack.copy();
                stack.setCount(1);
            } else if (ingredient instanceof FluidStack fluidStack) {
                stack = new ItemStack(com.yision.fluidlogistics.registry.AllItems.COMPRESSED_STORAGE_TANK.get());
                CompressedTankItem.setFluidVirtual(stack, fluidStack.copyWithAmount(1));
                fluidRequest = true;
            } else {
                return;
            }

            gui.getMenu().ghostInventory.setStackInSlot(slotIndex, stack);

            if (fluidRequest) {
                ((RedstoneRequesterAmountsAccess) gui).fluidlogistics$getAmounts()
                    .set(slotIndex, FluidAmountHelper.DEFAULT_FLUID_REQUEST_AMOUNT);
            }

            CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(stack, slotIndex));
        }
    }
}
