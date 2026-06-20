package com.yision.fluidlogistics.compat.jei;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.client.JeiRuntimeHolder;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;

public final class JeiFluidTooltipProvider {

    private JeiFluidTooltipProvider() {
    }

    @Nullable
    @SuppressWarnings("removal")
    public static List<Component> getFluidTooltipLines(FluidStack fluid, TooltipFlag tooltipFlag) {
        IJeiRuntime runtime = getRuntime();
        if (runtime == null) {
            return null;
        }

        IIngredientManager ingredientManager = runtime.getIngredientManager();
        IIngredientRenderer<FluidStack> renderer = ingredientManager.getIngredientRenderer(ForgeTypes.FLUID_STACK);
        List<Component> lines = new ArrayList<>(renderer.getTooltip(fluid, tooltipFlag));
        if (lines.isEmpty()) {
            return null;
        }

        ingredientManager.createTypedIngredient(ForgeTypes.FLUID_STACK, fluid)
            .flatMap(typedIngredient -> getModName(runtime, typedIngredient))
            .ifPresent(lines::add);

        return lines;
    }

    @Nullable
    public static Font getFluidTooltipFont(FluidStack fluid) {
        IJeiRuntime runtime = getRuntime();
        if (runtime == null) {
            return null;
        }

        IIngredientRenderer<FluidStack> renderer = runtime.getIngredientManager().getIngredientRenderer(ForgeTypes.FLUID_STACK);
        return renderer.getFontRenderer(Minecraft.getInstance(), fluid);
    }

    @Nullable
    private static IJeiRuntime getRuntime() {
        Object runtime = JeiRuntimeHolder.getRuntime();
        return runtime instanceof IJeiRuntime jeiRuntime ? jeiRuntime : null;
    }

    private static <T> java.util.Optional<Component> getModName(IJeiRuntime runtime, ITypedIngredient<T> typedIngredient) {
        return runtime.getJeiHelpers()
            .getModIdHelper()
            .getModNameForTooltip(typedIngredient);
    }
}
