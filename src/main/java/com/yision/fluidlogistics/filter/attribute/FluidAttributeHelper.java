package com.yision.fluidlogistics.filter.attribute;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.yision.fluidlogistics.item.CompressedTankItem;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

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

        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
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
            result.add(fluid);
    }

    record FluidStackKey(net.minecraft.world.level.material.Fluid fluid,
                         net.minecraft.core.component.DataComponentPatch components) {
        FluidStackKey(FluidStack stack) {
            this(stack.getFluid(), stack.getComponentsPatch());
        }
    }
}
