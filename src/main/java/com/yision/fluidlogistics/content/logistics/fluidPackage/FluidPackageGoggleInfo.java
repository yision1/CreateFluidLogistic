package com.yision.fluidlogistics.content.logistics.fluidPackage;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.FluidFormatter;
import com.yision.fluidlogistics.config.Config;

import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class FluidPackageGoggleInfo {

    private FluidPackageGoggleInfo() {
    }

    public static boolean append(ItemStack box, List<Component> tooltip) {
        if (box.isEmpty() || !(box.getItem() instanceof FluidPackageItem)) {
            return false;
        }

        List<FluidStack> fluids = getContainedFluids(box);
        if (fluids.isEmpty()) {
            return false;
        }

        CreateLang.translate("gui.goggles.fluid_container").forGoggles(tooltip);
        int capacity = Config.getFluidPerPackage();
        for (FluidStack fluid : fluids) {
            CreateLang.fluidName(fluid).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            CreateLang.builder()
                .add(formatFluidAmount(fluid.getAmount()))
                .text(ChatFormatting.GRAY, " / ")
                .add(formatFluidAmount(capacity))
                .forGoggles(tooltip, 1);
        }
        return true;
    }

    private static Component formatFluidAmount(int amount) {
        Couple<MutableComponent> components = FluidFormatter.asComponents(amount, true);
        return CreateLang.builder()
            .add(components.getFirst().withStyle(ChatFormatting.GOLD))
            .text(" ")
            .add(components.getSecond().withStyle(ChatFormatting.GOLD))
            .component();
    }

    private static List<FluidStack> getContainedFluids(ItemStack box) {
        List<FluidStack> fluids = new ArrayList<>();
        if (!PackageItem.isPackage(box)) {
            return fluids;
        }

        ItemStackHandler contents = PackageItem.getContents(box);
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack slotStack = contents.getStackInSlot(i);
            if (!CompressedTankItem.isFluidStack(slotStack)) {
                continue;
            }
            FluidStack fluid = CompressedTankItem.getFluid(slotStack);
            int amount = fluid.getAmount() * slotStack.getCount();
            boolean merged = false;
            for (FluidStack existing : fluids) {
                if (FluidStack.isSameFluidSameComponents(existing, fluid)) {
                    existing.grow(amount);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                fluids.add(fluid.copyWithAmount(amount));
            }
        }
        return fluids;
    }
}
