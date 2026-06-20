package com.yision.fluidlogistics.client;

import java.lang.reflect.Method;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.compat.CompatMods;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;

public final class JeiClientBridge {

    private static final String TOOLTIP_PROVIDER_CLASS = "com.yision.fluidlogistics.compat.jei.JeiFluidTooltipProvider";

    private static boolean jeiAvailable;

    @Nullable
    private static Class<?> tooltipProviderClass;

    private JeiClientBridge() {
    }

    public static List<Component> getFluidTooltipLines(FluidStack fluid, TooltipFlag tooltipFlag) {
        List<Component> lines = invokeListMethod("getFluidTooltipLines", fluid, tooltipFlag);
        if (lines != null && !lines.isEmpty()) {
            return lines;
        }
        return List.of(fluid.getDisplayName());
    }

    public static void renderFluidTooltip(GuiGraphics graphics, Font fallbackFont, FluidStack fluid, int x, int y) {
        TooltipFlag.Default tooltipFlag = Minecraft.getInstance().options.advancedItemTooltips
            ? TooltipFlag.Default.ADVANCED
            : TooltipFlag.Default.NORMAL;
        List<Component> lines = invokeListMethod("getFluidTooltipLines", fluid, tooltipFlag.asCreative());
        if (lines != null && !lines.isEmpty()) {
            Font font = invokeFontMethod("getFluidTooltipFont", fluid);
            graphics.renderComponentTooltip(font != null ? font : fallbackFont, lines, x, y);
            return;
        }

        graphics.renderComponentTooltip(fallbackFont, List.of(fluid.getDisplayName()), x, y);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static List<Component> invokeListMethod(String methodName, FluidStack fluid, TooltipFlag tooltipFlag) {
        ensureInitialized();
        if (!jeiAvailable || tooltipProviderClass == null) {
            return null;
        }

        try {
            Method method = tooltipProviderClass.getMethod(methodName, FluidStack.class, TooltipFlag.class);
            return (List<Component>) method.invoke(null, fluid, tooltipFlag);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static Font invokeFontMethod(String methodName, FluidStack fluid) {
        ensureInitialized();
        if (!jeiAvailable || tooltipProviderClass == null) {
            return null;
        }

        try {
            Method method = tooltipProviderClass.getMethod(methodName, FluidStack.class);
            return (Font) method.invoke(null, fluid);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static void ensureInitialized() {
        if (jeiAvailable && tooltipProviderClass != null) {
            return;
        }

        if (!CompatMods.jeiLoaded()) {
            reset();
            return;
        }

        try {
            ClassLoader classLoader = JeiClientBridge.class.getClassLoader();
            tooltipProviderClass = Class.forName(TOOLTIP_PROVIDER_CLASS, false, classLoader);
            jeiAvailable = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            reset();
        }
    }

    private static void reset() {
        jeiAvailable = false;
        tooltipProviderClass = null;
    }
}
