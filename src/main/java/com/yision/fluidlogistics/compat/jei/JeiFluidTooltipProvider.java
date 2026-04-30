package com.yision.fluidlogistics.compat.jei;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.client.JeiRuntimeHolder;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

public final class JeiFluidTooltipProvider {

    private JeiFluidTooltipProvider() {
    }

    @Nullable
    @SuppressWarnings("removal")
    public static List<Component> getFluidTooltipLines(FluidStack fluid) {
        IJeiRuntime runtime = getRuntime();
        if (runtime == null) {
            return null;
        }

        IIngredientManager ingredientManager = runtime.getIngredientManager();
        IIngredientRenderer<FluidStack> renderer = ingredientManager.getIngredientRenderer(NeoForgeTypes.FLUID_STACK);
        TooltipFlag tooltipFlag = Minecraft.getInstance().options.advancedItemTooltips
            ? TooltipFlag.Default.ADVANCED.asCreative()
            : TooltipFlag.Default.NORMAL.asCreative();

        List<Component> lines = new ArrayList<>(renderer.getTooltip(fluid, tooltipFlag));
        if (lines.isEmpty()) {
            return null;
        }

        ingredientManager.createTypedIngredient(NeoForgeTypes.FLUID_STACK, fluid)
            .flatMap(typedIngredient -> getModName(runtime, typedIngredient))
            .ifPresent(lines::add);

        return lines;
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
