package com.yision.fluidlogistics.client;

import java.lang.reflect.Method;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.compat.CompatMods;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

public final class EmiClientBridge {

    private static final String TOOLTIP_PROVIDER_CLASS = "com.yision.fluidlogistics.compat.emi.EmiFluidTooltipProvider";

    private static boolean emiAvailable;

    @Nullable
    private static Class<?> tooltipProviderClass;

    private EmiClientBridge() {
    }

    @Nullable
    public static List<Component> getFluidTooltipLines(FluidStack fluid) {
        return invokeListMethod("getFluidTooltipLines", fluid);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static List<Component> invokeListMethod(String methodName, FluidStack fluid) {
        ensureInitialized();
        if (!emiAvailable || tooltipProviderClass == null) {
            return null;
        }

        try {
            Method method = tooltipProviderClass.getMethod(methodName, FluidStack.class);
            return (List<Component>) method.invoke(null, fluid);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            return null;
        }
    }

    private static void ensureInitialized() {
        if (emiAvailable && tooltipProviderClass != null) {
            return;
        }

        if (!CompatMods.emiLoaded()) {
            reset();
            return;
        }

        try {
            ClassLoader classLoader = EmiClientBridge.class.getClassLoader();
            tooltipProviderClass = Class.forName(TOOLTIP_PROVIDER_CLASS, false, classLoader);
            emiAvailable = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            reset();
        }
    }

    private static void reset() {
        emiAvailable = false;
        tooltipProviderClass = null;
    }
}
