package com.yision.fluidlogistics.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yision.fluidlogistics.util.FluidAmountHelper;
import net.minecraft.client.gui.GuiGraphics;

import static com.simibubi.create.foundation.gui.AllGuiTextures.NUMBERS;

public class FluidSlotAmountRenderer {

    private static final int STOCK_KEEPER_COUNT_X = 14;
    private static final int STOCK_KEEPER_COUNT_Y = 10;
    private static final int SLOT_SIZE = 16;
    private static final int NUMBER_HEIGHT = 6;

    public static void render(GuiGraphics graphics, int amount) {
        renderAtSlotPosition(graphics, amount, 0, 0);
    }

    public static void renderInStockKeeper(GuiGraphics graphics, int amount) {
        String text = FluidAmountHelper.formatStockKeeper(amount);
        if (text.isBlank()) {
            return;
        }
        if (text.equals("\u221e")){
            text = "+"; //Stock ticker wants "+" character to represent infinity
        }

        int textWidth = calculateTextWidth(text);
        int renderX = STOCK_KEEPER_COUNT_X - textWidth + NUMBERS.getWidth();
        blitCreateFont(graphics, text, renderX, STOCK_KEEPER_COUNT_Y);
    }

    public static void renderAtSlotPosition(GuiGraphics graphics, int amount, int slotX, int slotY) {
        String text = FluidAmountHelper.format(amount);
        if (text.isBlank()) {
            return;
        }
        
        int textWidth = calculateTextWidth(text);
        int renderX = slotX + SLOT_SIZE - textWidth + 1;
        int renderY = slotY + SLOT_SIZE - NUMBER_HEIGHT + 1;
        
        blitCreateFont(graphics, text, renderX, renderY);
    }

    private static int calculateTextWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (c == ',')
                continue;
            switch (c) {
                case ' ':
                    width += 4;
                    break;
                case '.':
                    width += 3;
                    break;
                case 'm':
                    width += 7;
                    break;
                case '+':
                    width += 9;
                    break;
                default:
                    width += NUMBERS.getWidth();
                    break;
            }
            if (i < text.length() - 1) {
                width -= 1;
            }
        }
        return width;
    }

    private static void blitCreateFont(GuiGraphics graphics, String text, int startX, int startY) {
        int x = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));

            if (c == ',')
                continue;

            int index = c - '0';
            int xOffset = index * 6;
            int spriteWidth = NUMBERS.getWidth();

            switch (c) {
                case ' ':
                    x += 4;
                    continue;
                case '.':
                    spriteWidth = 3;
                    xOffset = 60;
                    break;
                case 'k':
                    xOffset = 64;
                    break;
                case 'm':
                    spriteWidth = 7;
                    xOffset = 70;
                    break;
                case 'b':
                    xOffset = 78;
                    break;
                case '+':
                    spriteWidth = 9;
                    xOffset = 84;
                    break;
            }

            RenderSystem.enableBlend();
            graphics.blit(NUMBERS.location, startX + x, startY, 0, NUMBERS.getStartX() + xOffset, NUMBERS.getStartY(),
                    spriteWidth, NUMBERS.getHeight(), 256, 256);
            x += spriteWidth - 1;
        }
    }
}
