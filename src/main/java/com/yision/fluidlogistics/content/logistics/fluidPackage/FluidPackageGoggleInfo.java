package com.yision.fluidlogistics.content.logistics.fluidPackage;

import java.util.List;

import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.FluidFormatter;

import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidPackageGoggleInfo {
    private FluidPackageGoggleInfo() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static boolean append(
            List<Component> tooltip, List<FluidStack> fluids, int capacity) {
        List<FluidStack> displayedFluids = fluids.stream()
                .filter(fluid -> fluid != null && !fluid.isEmpty())
                .toList();
        if (displayedFluids.isEmpty()) {
            return false;
        }

        CreateLang.translate("gui.goggles.fluid_container").forGoggles(tooltip);
        for (FluidStack fluid : displayedFluids) {
            CreateLang.fluidName(fluid)
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
            CreateLang.builder()
                    .add(amountLine(fluid.getAmount(), capacity))
                    .forGoggles(tooltip, 1);
        }
        return true;
    }

    static Component amountLine(int amount, int capacity) {
        return CreateLang.builder()
                .add(formatFluidAmount(amount, ChatFormatting.GOLD))
                .text(ChatFormatting.GRAY, " / ")
                .add(formatFluidAmount(capacity, ChatFormatting.DARK_GRAY))
                .component();
    }

    private static Component formatFluidAmount(int amount, ChatFormatting style) {
        Couple<MutableComponent> components = FluidFormatter.asComponents(amount, true);
        return CreateLang.builder()
                .add(components.getFirst().withStyle(style))
                .text(" ")
                .add(components.getSecond().withStyle(style))
                .component();
    }
}
