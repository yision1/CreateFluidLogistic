package com.yision.fluidlogistics.filter.attribute;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.yision.fluidlogistics.item.CompressedTankItem;
import net.createmod.catnip.data.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FluidAttributeHelper {
    public static List<FluidStack> extractFluids(ItemStack stack, Level level) {
        List<FluidStack> result = new ArrayList<>();
        Set<FluidStackKey> seen = new HashSet<>();

        if (stack.getItem() instanceof CompressedTankItem) {
            FluidStack fluid = CompressedTankItem.getFluid(stack);
            addIfNew(result, seen, fluid);
        }

        IFluidHandlerItem handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (handler != null) {
            for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack fluid = handler.getFluidInTank(i);
                addIfNew(result, seen, fluid);
            }
        }

        if (result.isEmpty() && GenericItemEmptying.canItemBeEmptied(level, stack)) {
            Pair<FluidStack, ItemStack> emptied = GenericItemEmptying.emptyItem(level, stack, true);
            addIfNew(result, seen, emptied.getFirst());
        }

        return result;
    }

    private static void addIfNew(List<FluidStack> result, Set<FluidStackKey> seen, FluidStack fluid) {
        if (fluid.isEmpty())
            return;
        FluidStackKey key = new FluidStackKey(fluid);
        if (seen.add(key))
            result.add(fluid.copy());
    }

    record FluidStackKey(net.minecraft.world.level.material.Fluid fluid, CompoundTag tag) {
        FluidStackKey(FluidStack stack) {
            this(stack.getFluid(), stack.hasTag() ? stack.getTag().copy() : null);
        }
    }
}
