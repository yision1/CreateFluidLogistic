package com.yision.fluidlogistics.compat.emi;

import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import com.yision.fluidlogistics.client.RedstoneRequesterAmountsAccess;
import com.yision.fluidlogistics.compat.CompatMods;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class RedstoneRequesterEmiDragDropHandler implements EmiDragDropHandler<RedstoneRequesterScreen> {

    public static final RedstoneRequesterEmiDragDropHandler INSTANCE = new RedstoneRequesterEmiDragDropHandler();

    private RedstoneRequesterEmiDragDropHandler() {
    }

    @Override
    public boolean dropStack(RedstoneRequesterScreen gui, EmiIngredient stack, int x, int y) {
        if (!shouldAccept(stack)) {
            return false;
        }

        for (int i = 36; i < gui.getMenu().slots.size(); i++) {
            Slot slot = gui.getMenu().slots.get(i);
            if (!slot.isActive()) {
                continue;
            }
            Rect2i area = new Rect2i(gui.getGuiLeft() + slot.x, gui.getGuiTop() + slot.y, 16, 16);
            if (area.contains(x, y)) {
                ItemStack ghost = EmiIngredientHelper.toGhostStack(stack);
                if (ghost == null) {
                    return false;
                }
                int slotIndex = i - 36;
                gui.getMenu().ghostInventory.setStackInSlot(slotIndex, ghost);

                if (EmiIngredientHelper.getFirstFluid(stack) != null) {
                    ((RedstoneRequesterAmountsAccess) gui).fluidlogistics$getAmounts()
                        .set(slotIndex, FluidAmountHelper.DEFAULT_FLUID_REQUEST_AMOUNT);
                }

                CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(ghost, slotIndex));
                return true;
            }
        }

        return false;
    }

    @Override
    public void render(RedstoneRequesterScreen gui, EmiIngredient dragged, GuiGraphics graphics, int mouseX,
            int mouseY, float delta) {
        if (!shouldAccept(dragged)) {
            return;
        }

        for (int i = 36; i < gui.getMenu().slots.size(); i++) {
            Slot slot = gui.getMenu().slots.get(i);
            if (!slot.isActive()) {
                continue;
            }
            int x = gui.getGuiLeft() + slot.x;
            int y = gui.getGuiTop() + slot.y;
            graphics.fill(x, y, x + 16, y + 16, 0x8822BB33);
        }
    }

    private static boolean shouldAccept(EmiIngredient ingredient) {
        if (ingredient.isEmpty()) {
            return false;
        }
        if (EmiIngredientHelper.getFirstFluid(ingredient) != null) {
            return !CompatMods.jeiLoaded();
        }
        return true;
    }
}
