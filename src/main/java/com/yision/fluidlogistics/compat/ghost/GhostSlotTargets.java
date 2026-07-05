package com.yision.fluidlogistics.compat.ghost;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.filter.AttributeFilterScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;

public final class GhostSlotTargets {

    private GhostSlotTargets() {
    }

    public record GhostSlotTarget(Rect2i area, int slotIndex) {
    }

    public static <T extends AbstractSimiContainerScreen<? extends GhostItemMenu<?>>> List<GhostSlotTarget> collect(T gui) {
        List<GhostSlotTarget> targets = new ArrayList<>();
        boolean attributeFilter = gui instanceof AttributeFilterScreen;
        int guiLeft = gui.getGuiLeft();
        int guiTop = gui.getGuiTop();

        for (int i = 36; i < gui.getMenu().slots.size(); i++) {
            int slotIndex = i - 36;
            if (attributeFilter && slotIndex != 0) {
                continue;
            }
            Slot slot = gui.getMenu().slots.get(i);
            if (!slot.isActive()) {
                continue;
            }
            Rect2i area = new Rect2i(guiLeft + slot.x, guiTop + slot.y, 16, 16);
            targets.add(new GhostSlotTarget(area, slotIndex));
        }

        return targets;
    }

    public static <T extends AbstractSimiContainerScreen<? extends GhostItemMenu<?>>> GhostSlotTarget hit(T gui, int x, int y) {
        for (GhostSlotTarget target : collect(gui)) {
            if (target.area().contains(x, y)) {
                return target;
            }
        }
        return null;
    }
}
