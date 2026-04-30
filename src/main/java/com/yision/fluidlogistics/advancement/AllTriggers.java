package com.yision.fluidlogistics.advancement;

import java.util.LinkedList;
import java.util.List;

import com.yision.fluidlogistics.FluidLogistics;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class AllTriggers {

    private static final List<CriterionTriggerBase<?>> triggers = new LinkedList<>();

    public static final SimpleFluidLogisticsTrigger HAND_POINTER_MODE = addSimple(
            FluidLogistics.asResource("hand_pointer_mode"));

    public static final SimpleFluidLogisticsTrigger FLUID_PACKAGE_CREATED = addSimple(
            FluidLogistics.asResource("fluid_package_created"));

    private static SimpleFluidLogisticsTrigger addSimple(ResourceLocation id) {
        return add(new SimpleFluidLogisticsTrigger(id));
    }

    private static <T extends CriterionTriggerBase<?>> T add(T instance) {
        triggers.add(instance);
        return instance;
    }

    public static void register() {
        triggers.forEach(trigger -> {
            Registry.register(BuiltInRegistries.TRIGGER_TYPES, trigger.getId(), trigger);
        });
    }
}
