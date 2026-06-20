package com.yision.fluidlogistics.filter.attribute;

import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import com.yision.fluidlogistics.registry.AllFluidAttributeTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class FluidStackAttribute implements FluidAttribute {
    private FluidStack fluid;

    public FluidStackAttribute(FluidStack fluid) {
        this.fluid = fluid.copy();
    }

    @Override
    public boolean appliesTo(FluidStack stack, Level level) {
        return fluid.isFluidEqual(stack);
    }

    @Override
    public ItemAttributeType getType() {
        return AllFluidAttributeTypes.IS_FLUID;
    }

    @Override
    public void save(CompoundTag nbt) {
        nbt.put("fluid", fluid.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag nbt) {
        fluid = FluidStack.loadFluidStackFromNBT(nbt.getCompound("fluid"));
    }

    @Override
    public String getTranslationKey() {
        return "is_fluid";
    }

    @Override
    public Object[] getTranslationParameters() {
        if (fluid.isEmpty())
            return new Object[0];
        return new Object[]{fluid.getDisplayName().getString()};
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FluidStackAttribute other)) return false;
        return fluid.isFluidEqual(other.fluid);
    }

    @Override
    public int hashCode() {
        return fluid.getFluid().hashCode();
    }

    public static class Type implements ItemAttributeType {
        @Override
        public @NotNull ItemAttribute createAttribute() {
            return new FluidStackAttribute(FluidStack.EMPTY);
        }

        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            return FluidAttributeHelper.extractFluids(stack, level).stream()
                    .map(f -> (ItemAttribute) new FluidStackAttribute(f))
                    .toList();
        }
    }
}
