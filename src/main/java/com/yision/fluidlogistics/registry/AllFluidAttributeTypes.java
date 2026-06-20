package com.yision.fluidlogistics.registry;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.filter.attribute.AnyFluidAttribute;
import com.yision.fluidlogistics.filter.attribute.FluidStackAttribute;
import com.yision.fluidlogistics.filter.attribute.FluidTypeAttribute;
import net.minecraft.core.Registry;

public class AllFluidAttributeTypes {
    public static final ItemAttributeType IS_FLUID = register("is_fluid", new FluidStackAttribute.Type());
    public static final ItemAttributeType IS_FLUID_TYPE = register("is_fluid_type", new FluidTypeAttribute.Type());
    public static final ItemAttributeType IS_ANY_FLUID = register("is_any_fluid", new AnyFluidAttribute.Type());

    private static ItemAttributeType register(String id, ItemAttributeType type) {
        return Registry.register(CreateBuiltInRegistries.ITEM_ATTRIBUTE_TYPE, FluidLogistics.asResource(id), type);
    }

    public static void init() {
    }
}
