package com.yision.fluidlogistics.filter.attribute;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import com.yision.fluidlogistics.registry.AllFluidAttributeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FluidStackAttribute implements FluidAttribute {
    private final FluidStack fluid;

    public FluidStackAttribute(FluidStack fluid) {
        this.fluid = fluid;
    }

    @Override
    public boolean appliesTo(FluidStack stack, Level level) {
        return FluidStack.isSameFluidSameComponents(fluid, stack);
    }

    @Override
    public ItemAttributeType getType() {
        return AllFluidAttributeTypes.IS_FLUID.get();
    }

    @Override
    public String getTranslationKey() {
        return "is_fluid";
    }

    @Override
    public Object[] getTranslationParameters() {
        return new Object[]{fluid.getFluid().getFluidType().getDescription(fluid).getString()};
    }

    public static class Type implements ItemAttributeType {
        public static final MapCodec<FluidStackAttribute> CODEC = FluidStack.CODEC
                .fieldOf("fluid")
                .xmap(FluidStackAttribute::new, a -> a.fluid);

        public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, FluidStackAttribute> STREAM_CODEC =
                FluidStack.STREAM_CODEC.map(FluidStackAttribute::new, a -> a.fluid);

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
