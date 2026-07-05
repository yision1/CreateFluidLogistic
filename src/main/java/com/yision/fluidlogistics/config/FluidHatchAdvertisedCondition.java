package com.yision.fluidlogistics.config;

import com.google.gson.JsonObject;
import com.yision.fluidlogistics.FluidLogistics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

public class FluidHatchAdvertisedCondition implements ICondition {

    @Override
    public ResourceLocation getID() {
        return FluidLogistics.asResource("fluid_hatch_advertised");
    }

    @Override
    public boolean test(IContext context) {
        return FeatureToggle.isFluidHatchAdvertised();
    }

    public static class Serializer implements IConditionSerializer<FluidHatchAdvertisedCondition> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, FluidHatchAdvertisedCondition value) {
        }

        @Override
        public FluidHatchAdvertisedCondition read(JsonObject json) {
            return new FluidHatchAdvertisedCondition();
        }

        @Override
        public ResourceLocation getID() {
            return FluidLogistics.asResource("fluid_hatch_advertised");
        }
    }
}
