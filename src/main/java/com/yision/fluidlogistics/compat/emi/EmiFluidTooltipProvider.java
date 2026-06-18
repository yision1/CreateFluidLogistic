package com.yision.fluidlogistics.compat.emi;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.neoforge.NeoForgeEmiStack;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

public final class EmiFluidTooltipProvider {

    private EmiFluidTooltipProvider() {
    }

    @Nullable
    public static List<Component> getFluidTooltipLines(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return null;
        }

        EmiStack emiStack = NeoForgeEmiStack.of(fluid);
        List<Component> lines = new ArrayList<>(emiStack.getTooltipText());
        if (lines.isEmpty()) {
            lines.add(fluid.getHoverName());
        }

        lines.add(getModNameComponent(fluid));
        return lines;
    }

    private static Component getModNameComponent(FluidStack fluid) {
        ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        String namespace = key != null ? key.getNamespace() : "minecraft";
        return Component.literal(getModName(namespace))
                .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC);
    }

    private static String getModName(String namespace) {
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }
}
