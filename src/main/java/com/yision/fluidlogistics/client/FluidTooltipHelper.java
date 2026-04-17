package com.yision.fluidlogistics.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidTooltipHelper {

    private FluidTooltipHelper() {
    }

    public static List<Component> getTooltipLines(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return List.of();
        }

        List<Component> jeiLines = JeiClientBridge.getFluidTooltipLines(fluid);
        return jeiLines.size() > 1 ? jeiLines : List.of(fluid.getHoverName().copy());
    }

    public static void renderTooltip(GuiGraphics graphics, Font fallbackFont, FluidStack fluid, int x, int y) {
        if (fluid.isEmpty()) {
            return;
        }

        List<Component> jeiLines = JeiClientBridge.getFluidTooltipLines(fluid);
        if (jeiLines.size() > 1) {
            JeiClientBridge.renderFluidTooltip(graphics, fallbackFont, fluid, x, y);
            return;
        }

        graphics.renderComponentTooltip(fallbackFont, getTooltipLines(fluid), x, y);
    }
}
