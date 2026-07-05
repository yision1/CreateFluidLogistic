package com.yision.fluidlogistics.compat.ghost;

import com.simibubi.create.AllPackets;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;

import net.minecraft.world.item.ItemStack;

public final class GhostSlotSubmitter {

    private GhostSlotSubmitter() {
    }

    public static <T extends AbstractSimiContainerScreen<? extends GhostItemMenu<?>>> void submit(T gui, int slotIndex,
            ItemStack stack) {
        GhostItemMenu<?> menu = (GhostItemMenu<?>) gui.getMenu();
        menu.ghostInventory.setStackInSlot(slotIndex, stack);
        gui.getMenu().getSlot(slotIndex + 36).setChanged();
        AllPackets.getChannel().sendToServer(new GhostItemSubmitPacket(stack, slotIndex));
    }
}
