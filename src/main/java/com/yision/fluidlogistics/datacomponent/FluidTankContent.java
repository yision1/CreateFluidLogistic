package com.yision.fluidlogistics.datacomponent;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

public record FluidTankContent(FluidStack fluid) {

    public static final Codec<FluidTankContent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FluidStack.CODEC.fieldOf("fluid").forGetter(FluidTankContent::fluid)
            ).apply(instance, FluidTankContent::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidTankContent> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> FluidStack.STREAM_CODEC.encode(buf, value.fluid),
            buf -> new FluidTankContent(FluidStack.STREAM_CODEC.decode(buf))
    );

    public static FluidTankContent empty() {
        return new FluidTankContent(FluidStack.EMPTY);
    }

    public boolean isEmpty() {
        return fluid.isEmpty();
    }

    public int getAmount() {
        return fluid.getAmount();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FluidTankContent other)) return false;
        if (!FluidStack.isSameFluidSameComponents(this.fluid, other.fluid)) {
            return false;
        }
        return this.fluid.getAmount() == other.fluid.getAmount();
    }

    @Override
    public int hashCode() {
        if (fluid.isEmpty()) return 0;
        return Objects.hash(fluid.getFluid(), fluid.getComponentsPatch(), fluid.getAmount());
    }
}
