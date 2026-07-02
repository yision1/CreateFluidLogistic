package com.yision.fluidlogistics.client;

import java.util.ArrayList;
import java.util.List;

import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidTooltipHelper {

    private FluidTooltipHelper() {
    }

    public static List<Component> getTooltipLines(FluidStack fluid, boolean advanced) {
        return getTooltipLines(fluid, advanced, true);
    }

    public static List<Component> getTooltipLines(FluidStack fluid, boolean advanced, boolean includeModName) {
        if (fluid.isEmpty()) {
            return List.of();
        }

        List<Component> lines = new ArrayList<>();
        lines.add(fluid.getHoverName().copy());

        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        if (advanced && fluidId != null) {
            lines.add(Component.literal(fluidId.toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        addAdvancedComponentLines(lines, fluid, advanced);

        if (includeModName) {
            String namespace = fluidId != null ? fluidId.getNamespace() : "minecraft";
            String modName = getModName(namespace);
            lines.add(Component.literal(modName)
                    .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        }
        return lines;
    }

    public static List<Component> getVirtualCompressedTankTooltipLines(ItemStack stack, boolean advanced) {
        return getVirtualCompressedTankTooltipLines(stack, advanced, true);
    }

    public static List<Component> getVirtualCompressedTankTooltipLines(ItemStack stack, boolean advanced,
            boolean includeModName) {
        FluidStack fluid = getVirtualCompressedTankFluid(stack);
        return fluid.isEmpty() ? List.of() : getTooltipLines(fluid, advanced, includeModName);
    }

    public static FluidStack getVirtualCompressedTankFluid(ItemStack stack) {
        if (!(stack.getItem() instanceof CompressedTankItem) || !CompressedTankItem.isVirtual(stack)) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(stack);
    }

    public static void addAdvancedComponentLines(List<Component> lines, FluidStack fluid, boolean advanced) {
        if (!advanced || fluid.isEmpty()) {
            return;
        }

        int componentCount = fluid.getComponents().size();
        if (componentCount > 0) {
            lines.add(Component.translatable("item.components", componentCount)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String getModName(String namespace) {
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        return ModList.get()
                .getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(namespace);
    }
}
