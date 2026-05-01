package com.yision.fluidlogistics.filter.attribute;

import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

public interface FluidAttribute extends ItemAttribute {
    boolean appliesTo(FluidStack stack, Level level);

    @Override
    default boolean appliesTo(ItemStack stack, Level level) {
        for (FluidStack fluid : FluidAttributeHelper.extractFluids(stack, level)) {
            if (appliesTo(fluid, level))
                return true;
        }
        return false;
    }
}
