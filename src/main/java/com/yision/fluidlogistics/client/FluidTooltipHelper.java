package com.yision.fluidlogistics.client;

import java.util.List;

import com.yision.fluidlogistics.item.CompressedTankItem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidTooltipHelper {

    private FluidTooltipHelper() {
    }

    public static List<Component> getTooltipLines(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return List.of();
        }

        List<Component> jeiLines = JeiClientBridge.getFluidTooltipLines(fluid);
        return jeiLines.size() > 1 ? jeiLines : List.of(fluid.getHoverName().copy());
    }

    public static List<Component> getVirtualCompressedTankTooltipLines(ItemStack stack) {
        FluidStack fluid = getVirtualCompressedTankFluid(stack);
        return fluid.isEmpty() ? List.of() : getTooltipLines(fluid);
    }

    public static FluidStack getVirtualCompressedTankFluid(ItemStack stack) {
        if (!(stack.getItem() instanceof CompressedTankItem) || !CompressedTankItem.isVirtual(stack)) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(stack);
    }
}
