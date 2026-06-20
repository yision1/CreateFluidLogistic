package com.yision.fluidlogistics.compat.emi;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import dev.emi.emi.api.forge.ForgeEmiStack;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public final class EmiFluidTooltipProvider {

    private EmiFluidTooltipProvider() {
    }

    @Nullable
    public static List<Component> getFluidTooltipLines(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return null;
        }

        EmiStack emiStack = ForgeEmiStack.of(fluid);
        List<Component> lines = new ArrayList<>(emiStack.getTooltipText());
        if (lines.isEmpty()) {
            lines.add(fluid.getDisplayName());
        }

        lines.add(getModNameComponent(fluid));
        return lines;
    }

    private static Component getModNameComponent(FluidStack fluid) {
        ResourceLocation key = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
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
