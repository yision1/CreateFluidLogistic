package com.yision.fluidlogistics.filter.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import com.yision.fluidlogistics.registry.AllFluidAttributeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class AnyFluidAttribute implements FluidAttribute {
	private final Type type;

	private AnyFluidAttribute(Type type) {
		this.type = type;
	}

	@Override
	public boolean appliesTo(FluidStack stack, Level level) {
		return !stack.isEmpty();
	}

	@Override
	public ItemAttributeType getType() {
		return type;
	}

	@Override
	public String getTranslationKey() {
		return "is_any_fluid";
	}

	public static final class Type implements ItemAttributeType {
		private final AnyFluidAttribute attribute = new AnyFluidAttribute(this);

		@Override
		public @NotNull ItemAttribute createAttribute() {
			return attribute;
		}

		@Override
		public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
			return FluidAttributeHelper.extractFluids(stack, level).isEmpty()
				? List.of()
				: List.of(attribute);
		}

		@Override
		public MapCodec<? extends ItemAttribute> codec() {
			return Codec.unit(attribute).fieldOf("value");
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
			return StreamCodec.unit(attribute);
		}
	}
}
