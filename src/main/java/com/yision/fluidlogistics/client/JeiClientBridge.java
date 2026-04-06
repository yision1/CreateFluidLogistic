package com.yision.fluidlogistics.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

public final class JeiClientBridge {

    private static final String CREATE_JEI_CLASS = "com.simibubi.create.compat.jei.CreateJEI";
    private static final String NEOFORGE_TYPES_CLASS = "mezz.jei.api.neoforge.NeoForgeTypes";

    private static boolean jeiAvailable;

    @Nullable
    private static Class<?> createJeiClass;

    @Nullable
    private static Class<?> neoForgeTypesClass;

    private JeiClientBridge() {
    }

    public static void initializeForStartup() {
        jeiAvailable = false;
        createJeiClass = null;
        neoForgeTypesClass = null;

        if (!ModList.get().isLoaded("jei")) {
            return;
        }

        try {
            ClassLoader classLoader = JeiClientBridge.class.getClassLoader();
            createJeiClass = Class.forName(CREATE_JEI_CLASS, false, classLoader);
            neoForgeTypesClass = Class.forName(NEOFORGE_TYPES_CLASS, false, classLoader);
            jeiAvailable = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            jeiAvailable = false;
        }
    }

    public static List<Component> getFluidTooltipLines(FluidStack fluid) {
        TooltipData tooltipData = getTooltipData(fluid);
        if (tooltipData != null && !tooltipData.lines().isEmpty()) {
            return tooltipData.lines();
        }
        return List.of(fluid.getHoverName());
    }

    public static void renderFluidTooltip(GuiGraphics graphics, Font fallbackFont, FluidStack fluid, int x, int y) {
        TooltipData tooltipData = getTooltipData(fluid);
        if (tooltipData != null && !tooltipData.lines().isEmpty()) {
            Font font = tooltipData.font() != null ? tooltipData.font() : fallbackFont;
            graphics.renderComponentTooltip(font, tooltipData.lines(), x, y);
            return;
        }

        graphics.renderComponentTooltip(fallbackFont, List.of(fluid.getHoverName()), x, y);
    }

    @Nullable
    private static TooltipData getTooltipData(FluidStack fluid) {
        Object runtime = getRuntime();
        if (runtime == null) {
            return null;
        }

        TooltipFlag.Default tooltipFlag =
                (Minecraft.getInstance().options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED
                        : TooltipFlag.Default.NORMAL).asCreative();

        try {
            Object ingredientManager = invoke(runtime, "getIngredientManager");
            Object fluidStackType = getFluidStackType();
            Object renderer = invoke(ingredientManager, "getIngredientRenderer", fluidStackType);

            @SuppressWarnings("unchecked")
            List<Component> lines = new ArrayList<>((List<Component>) invoke(renderer, "getTooltip", fluid, tooltipFlag));
            if (lines.isEmpty()) {
                return null;
            }

            Optional<?> typedIngredient = (Optional<?>) invoke(ingredientManager, "createTypedIngredient", fluidStackType, fluid);
            if (typedIngredient.isPresent()) {
                Object jeiHelpers = invoke(runtime, "getJeiHelpers");
                Object modIdHelper = invoke(jeiHelpers, "getModIdHelper");
                Optional<?> modName = (Optional<?>) invoke(modIdHelper, "getModNameForTooltip", typedIngredient.get());
                if (modName.orElse(null) instanceof Component component) {
                    lines.add(component);
                }
            }

            Font font = (Font) invoke(renderer, "getFontRenderer", Minecraft.getInstance(), fluid);
            return new TooltipData(lines, font);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    private static Object getRuntime() {
        if (!jeiAvailable || createJeiClass == null) {
            return null;
        }

        try {
            Field runtimeField = createJeiClass.getField("runtime");
            return runtimeField.get(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Object getFluidStackType() throws ReflectiveOperationException {
        if (neoForgeTypesClass == null) {
            throw new ClassNotFoundException(NEOFORGE_TYPES_CLASS);
        }
        Field fluidStackField = neoForgeTypesClass.getField("FLUID_STACK");
        return fluidStackField.get(null);
    }

    private static Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, args);
        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String methodName, Object... args) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                Object arg = args[i];
                if (arg != null && !parameterTypes[i].isInstance(arg)) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                return method;
            }
        }

        throw new NoSuchMethodException(type.getName() + "#" + methodName);
    }

    private record TooltipData(List<Component> lines, @Nullable Font font) {
    }
}
