package com.yision.fluidlogistics.filter.attribute;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import com.yision.fluidlogistics.registry.AllFluidAttributeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FluidTypeAttribute implements FluidAttribute {
    private final Fluid fluid;

    public FluidTypeAttribute(Fluid fluid) {
        this.fluid = fluid;
    }

    @Override
    public boolean appliesTo(FluidStack stack, Level level) {
        return stack.getFluid().isSame(fluid);
    }

    @Override
    public ItemAttributeType getType() {
        return AllFluidAttributeTypes.IS_FLUID_TYPE.get();
    }

    @Override
    public String getTranslationKey() {
        return "is_fluid_no_nbt";
    }

    @Override
    public Object[] getTranslationParameters() {
        return new Object[]{fluid.getFluidType().getDescription().getString()};
    }

    public static class Type implements ItemAttributeType {
        public static final MapCodec<FluidTypeAttribute> CODEC = BuiltInRegistries.FLUID.byNameCodec()
                .fieldOf("fluid")
                .xmap(FluidTypeAttribute::new, a -> a.fluid);

        public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, FluidTypeAttribute> STREAM_CODEC =
                net.minecraft.network.codec.ByteBufCodecs.registry(net.minecraft.core.registries.Registries.FLUID)
                        .map(FluidTypeAttribute::new, a -> a.fluid);

        @Override
        public @NotNull ItemAttribute createAttribute() {
            return new FluidTypeAttribute(net.minecraft.world.level.material.Fluids.EMPTY);
        }

        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            return FluidAttributeHelper.extractFluids(stack, level).stream()
                    .map(FluidStack::getFluid)
                    .distinct()
                    .map(f -> (ItemAttribute) new FluidTypeAttribute(f))
                    .toList();
        }

        @Override
        public MapCodec<? extends ItemAttribute> codec() {
            return CODEC;
        }

        @Override
        public net.minecraft.network.codec.StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
