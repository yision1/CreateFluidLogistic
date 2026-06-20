package com.yision.fluidlogistics.client;

import java.util.List;

import com.yision.fluidlogistics.item.CompressedTankItem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fluids.FluidStack;

public final class FluidTooltipHelper {

    private FluidTooltipHelper() {
    }

    public static List<Component> getTooltipLines(FluidStack fluid, TooltipFlag tooltipFlag) {
        if (fluid.isEmpty()) {
            return List.of();
        }

        List<Component> jeiLines = JeiClientBridge.getFluidTooltipLines(fluid, tooltipFlag);
        if (jeiLines.size() > 1) {
            return jeiLines;
        }

        List<Component> emiLines = EmiClientBridge.getFluidTooltipLines(fluid);
        if (emiLines != null && !emiLines.isEmpty()) {
            return emiLines;
        }

        return List.of(fluid.getDisplayName().copy());
    }

    public static List<Component> getVirtualCompressedTankTooltipLines(ItemStack stack, TooltipFlag tooltipFlag) {
        FluidStack fluid = getVirtualCompressedTankFluid(stack);
        return fluid.isEmpty() ? List.of() : getTooltipLines(fluid, tooltipFlag);
    }

    public static FluidStack getVirtualCompressedTankFluid(ItemStack stack) {
        if (!(stack.getItem() instanceof CompressedTankItem) || !CompressedTankItem.isVirtual(stack)) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(stack);
    }
}
