package com.yision.fluidlogistics.client;

import com.yision.fluidlogistics.FluidLogistics;

import net.createmod.catnip.gui.TextureSheetSegment;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public enum FluidLogisticsGuiTextures implements ScreenElement, TextureSheetSegment {

    ADDITIONAL_STOCK_LEFT_BG("fluid_threshold", 20, 0, 37, 27, 192, 32),
    PROMISE_LIMIT_BG("fluid_threshold", 57, 0, 72, 28, 192, 32),
    ADDITIONAL_STOCK_BG("fluid_threshold", 140, 0, 36, 18, 192, 32),

    FROGPORT_HEADER("frogport_and_mailbox", 0, 0, 214, 17, 256, 256),
    FROGPORT_SLOT("frogport_and_mailbox", 26, 55, 18, 18, 256, 256),
    FROGPORT_EDIT_NAME("frogport_and_mailbox", 230, 3, 13, 13, 256, 256),
    FROGPORT_BG("frogport_and_mailbox", 0, 47, 220, 82, 256, 256);

    public final ResourceLocation location;
    private final int width;
    private final int height;
    private final int startX;
    private final int startY;
    private final int textureWidth;
    private final int textureHeight;

    FluidLogisticsGuiTextures(String location, int startX, int startY, int width, int height, int textureWidth,
            int textureHeight) {
        this.location = FluidLogistics.asResource("textures/gui/" + location + ".png");
        this.width = width;
        this.height = height;
        this.startX = startX;
        this.startY = startY;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    @Override
    public ResourceLocation getLocation() {
        return location;
    }

    @OnlyIn(Dist.CLIENT)
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(location, x, y, startX, startY, width, height, textureWidth, textureHeight);
    }

    @Override
    public int getStartX() {
        return startX;
    }

    @Override
    public int getStartY() {
        return startY;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }
}
