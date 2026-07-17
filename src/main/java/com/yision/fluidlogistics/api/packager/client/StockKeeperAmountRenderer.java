package com.yision.fluidlogistics.api.packager.client;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface StockKeeperAmountRenderer {
    void render(GuiGraphics graphics, int amount);
}
