package com.yision.fluidlogistics.client;

import java.util.ArrayList;
import java.util.List;

import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.FluidStack;

public final class FluidTooltipHelper {

    private FluidTooltipHelper() {
    }

    public static List<Component> getTooltipLines(FluidStack fluid, TooltipFlag tooltipFlag) {
        return getTooltipLines(fluid, tooltipFlag, true);
    }

    public static List<Component> getTooltipLines(FluidStack fluid, TooltipFlag tooltipFlag, boolean includeModName) {
        if (fluid.isEmpty()) {
            return List.of();
        }

        List<Component> lines = new ArrayList<>();
        lines.add(fluid.getDisplayName().copy());

        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        if (tooltipFlag.isAdvanced() && fluidId != null) {
            lines.add(Component.literal(fluidId.toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        addAdvancedTagLines(lines, fluid, tooltipFlag);

        if (includeModName) {
            String namespace = fluidId != null ? fluidId.getNamespace() : "minecraft";
            String modName = getModName(namespace);
            lines.add(Component.literal(modName)
                    .withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        }
        return lines;
    }

    public static List<Component> getVirtualCompressedTankTooltipLines(ItemStack stack, TooltipFlag tooltipFlag) {
        return getVirtualCompressedTankTooltipLines(stack, tooltipFlag, true);
    }

    public static List<Component> getVirtualCompressedTankTooltipLines(ItemStack stack, TooltipFlag tooltipFlag,
            boolean includeModName) {
        FluidStack fluid = getVirtualCompressedTankFluid(stack);
        return fluid.isEmpty() ? List.of() : getTooltipLines(fluid, tooltipFlag, includeModName);
    }

    public static FluidStack getVirtualCompressedTankFluid(ItemStack stack) {
        if (!(stack.getItem() instanceof CompressedTankItem) || !CompressedTankItem.isVirtual(stack)) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(stack);
    }

    public static void addAdvancedTagLines(List<Component> lines, FluidStack fluid, TooltipFlag tooltipFlag) {
        if (!tooltipFlag.isAdvanced() || fluid.isEmpty()) {
            return;
        }

        CompoundTag tag = fluid.getTag();
        if (tag != null && !tag.isEmpty()) {
            lines.add(Component.translatable("item.nbt_tags", tag.size())
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
