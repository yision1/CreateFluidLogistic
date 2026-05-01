package com.yision.fluidlogistics.registry;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.filter.attribute.FluidStackAttribute;
import com.yision.fluidlogistics.filter.attribute.FluidTypeAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AllFluidAttributeTypes {
    public static final DeferredRegister<ItemAttributeType> REGISTER =
            DeferredRegister.create(CreateBuiltInRegistries.ITEM_ATTRIBUTE_TYPE.key(), FluidLogistics.MODID);

    public static final DeferredHolder<ItemAttributeType, FluidStackAttribute.Type> IS_FLUID =
            REGISTER.register("is_fluid", FluidStackAttribute.Type::new);
    public static final DeferredHolder<ItemAttributeType, FluidTypeAttribute.Type> IS_FLUID_TYPE =
            REGISTER.register("is_fluid_type", FluidTypeAttribute.Type::new);

    private AllFluidAttributeTypes() {
    }
}
