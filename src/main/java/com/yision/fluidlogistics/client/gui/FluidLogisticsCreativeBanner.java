package com.yision.fluidlogistics.client.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.registry.CreativeTabSectionRegistry;
import com.yision.fluidlogistics.registry.CreativeTabSectionRegistry.PositionedSection;
import com.yision.fluidlogistics.mixin.client.CreativeModeInventoryScreenAccessor;
import com.yision.fluidlogistics.mixin.client.CreativeModeItemPickerMenuAccessor;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

@EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT)
public final class FluidLogisticsCreativeBanner {
    private static final ResourceLocation ROOT_BANNER_ID = FluidLogistics.asResource("creative_tab_root");
    private static final ResourceLocation ROOT_BANNER_TEXTURE =
            FluidLogistics.asResource("textures/gui/creative_tab/banner.png");
    private static final int WIDTH = 162;
    private static final int FRAME_HEIGHT = 18;
    private static final int ROOT_FRAME_COUNT = 10;
    private static final int VISIBLE_ROW_COUNT = 5;
    private static final long ROOT_FRAME_DURATION_MILLIS = 100L;
    private static final int BANNER_X_OFFSET = 8;
    private static final int BANNER_Y_OFFSET = 17;
    private static final int TITLE_X_OFFSET = BANNER_X_OFFSET + 5;
    private static final int TITLE_Y_INSET = 5;
    private static final int TITLE_BACKGROUND = 0xAA39231C;
    private static final int TITLE_TOP_COLOR = 0xFFFFEB8C;
    private static final int TITLE_BOTTOM_COLOR = 0xFFCEA05A;
    private static final Component ROOT_TITLE =
            Component.translatable("gui.fluidlogistics.creative_banner.title");
    private static final Map<ResourceLocation, HoverAnimation> ANIMATIONS = new HashMap<>();
    private static final Set<ResourceLocation> VISIBLE_BANNERS = new HashSet<>();

    private FluidLogisticsCreativeBanner() {
    }

    @SubscribeEvent
    static void onRenderForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof CreativeModeInventoryScreen screen)
                || CreativeModeInventoryScreenAccessor.fluidLogistics$getSelectedTab()
                        != FluidLogistics.FLUID_LOGISTICS_CREATIVE_TAB.get()) {
            ANIMATIONS.clear();
            VISIBLE_BANNERS.clear();
            return;
        }

        CreativeModeInventoryScreenAccessor screenAccessor = (CreativeModeInventoryScreenAccessor) screen;
        CreativeModeItemPickerMenuAccessor menuAccessor = (CreativeModeItemPickerMenuAccessor) screen.getMenu();
        int firstVisibleRow = menuAccessor.fluidLogistics$getRowIndexForScroll(
                screenAccessor.fluidLogistics$getScrollOffset());
        long nowMillis = Util.getMillis();
        VISIBLE_BANNERS.clear();

        if (firstVisibleRow == 0) {
            drawBanner(event, screen, ROOT_BANNER_ID, ROOT_BANNER_TEXTURE, ROOT_FRAME_COUNT,
                    ROOT_FRAME_DURATION_MILLIS, 0, nowMillis);
            drawTitle(event.getGuiGraphics(), ROOT_TITLE, 0);
            VISIBLE_BANNERS.add(ROOT_BANNER_ID);
        }

        for (PositionedSection section : CreativeTabSectionRegistry.instance().positionedSections()) {
            int visibleRow = section.bannerRow() - firstVisibleRow;
            if (visibleRow < 0 || visibleRow >= VISIBLE_ROW_COUNT) {
                continue;
            }
            drawBanner(event, screen, section.id(), section.bannerTexture(), section.frameCount(),
                    section.frameDurationMillis(), visibleRow, nowMillis);
            drawTitle(event.getGuiGraphics(), section.title(), visibleRow);
            VISIBLE_BANNERS.add(section.id());
        }

        ANIMATIONS.forEach((id, animation) -> {
            if (!VISIBLE_BANNERS.contains(id)) {
                animation.pause(nowMillis);
            }
        });
    }

    private static void drawBanner(
            ContainerScreenEvent.Render.Foreground event,
            CreativeModeInventoryScreen screen,
            ResourceLocation id,
            ResourceLocation texture,
            int frameCount,
            long frameDurationMillis,
            int visibleRow,
            long nowMillis) {
        int relativeY = BANNER_Y_OFFSET + visibleRow * FRAME_HEIGHT;
        int bannerX = screen.getGuiLeft() + BANNER_X_OFFSET;
        int bannerY = screen.getGuiTop() + relativeY;
        boolean hovered = event.getMouseX() >= bannerX && event.getMouseX() < bannerX + WIDTH
                && event.getMouseY() >= bannerY && event.getMouseY() < bannerY + FRAME_HEIGHT;
        int frame = ANIMATIONS.computeIfAbsent(id, ignored -> new HoverAnimation())
                .sample(nowMillis, hovered, frameCount, frameDurationMillis);

        event.getGuiGraphics().blit(texture, BANNER_X_OFFSET, relativeY, WIDTH, FRAME_HEIGHT,
                0.0F, frame * FRAME_HEIGHT, WIDTH, FRAME_HEIGHT,
                WIDTH, FRAME_HEIGHT * frameCount);
    }

    private static void drawTitle(GuiGraphics graphics, Component title, int visibleRow) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(title);
        int backgroundRight = Math.min(BANNER_X_OFFSET + WIDTH - 2, TITLE_X_OFFSET + textWidth + 3);
        int bannerY = BANNER_Y_OFFSET + visibleRow * FRAME_HEIGHT;
        int titleY = bannerY + TITLE_Y_INSET;

        graphics.fill(BANNER_X_OFFSET + 2, bannerY + 2,
                backgroundRight, bannerY + FRAME_HEIGHT - 2, TITLE_BACKGROUND);
        graphics.drawString(font, title, TITLE_X_OFFSET, titleY, TITLE_BOTTOM_COLOR, true);

        graphics.enableScissor(TITLE_X_OFFSET, titleY,
                TITLE_X_OFFSET + textWidth + 1, titleY + 5);
        try {
            graphics.drawString(font, title, TITLE_X_OFFSET, titleY, TITLE_TOP_COLOR, false);
        } finally {
            graphics.disableScissor();
        }
    }

    private static final class HoverAnimation {
        private long activeHoverMillis;
        private long lastSampleMillis = -1L;
        private boolean hoveredLastSample;

        private int sample(long nowMillis, boolean hovered, int frameCount, long frameDurationMillis) {
            if (lastSampleMillis >= 0L && nowMillis >= lastSampleMillis && hoveredLastSample && hovered) {
                activeHoverMillis += nowMillis - lastSampleMillis;
            }
            lastSampleMillis = nowMillis;
            hoveredLastSample = hovered;
            return (int) (activeHoverMillis / frameDurationMillis % frameCount);
        }

        private void pause(long nowMillis) {
            lastSampleMillis = nowMillis;
            hoveredLastSample = false;
        }
    }
}
