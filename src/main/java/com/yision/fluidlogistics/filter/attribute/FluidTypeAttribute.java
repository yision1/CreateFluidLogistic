package com.yision.fluidlogistics.filter.attribute;

import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import com.yision.fluidlogistics.registry.AllFluidAttributeTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class FluidTypeAttribute implements FluidAttribute {
    private Fluid fluid;

    public FluidTypeAttribute(Fluid fluid) {
        this.fluid = fluid;
    }

    @Override
    public boolean appliesTo(FluidStack stack, Level level) {
        return stack.getFluid().isSame(fluid);
    }

    @Override
    public ItemAttributeType getType() {
        return AllFluidAttributeTypes.IS_FLUID_TYPE;
    }

    @Override
    public void save(CompoundTag nbt) {
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        if (id != null)
            nbt.putString("fluidId", id.toString());
    }

    @Override
    public void load(CompoundTag nbt) {
        ResourceLocation id = ResourceLocation.tryParse(nbt.getString("fluidId"));
        Fluid loaded = ForgeRegistries.FLUIDS.getValue(id);
        fluid = loaded == null ? Fluids.EMPTY : loaded;
    }

    @Override
    public String getTranslationKey() {
        return "is_fluid_no_nbt";
    }

    @Override
    public Object[] getTranslationParameters() {
        return new Object[]{fluid.getFluidType().getDescription().getString()};
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FluidTypeAttribute other)) return false;
        return fluid == other.fluid;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fluid);
    }

    public static class Type implements ItemAttributeType {
        @Override
        public @NotNull ItemAttribute createAttribute() {
            return new FluidTypeAttribute(Fluids.EMPTY);
        }

        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            return FluidAttributeHelper.extractFluids(stack, level).stream()
                    .map(FluidStack::getFluid)
                    .distinct()
                    .map(f -> (ItemAttribute) new FluidTypeAttribute(f))
                    .toList();
        }
    }
}
