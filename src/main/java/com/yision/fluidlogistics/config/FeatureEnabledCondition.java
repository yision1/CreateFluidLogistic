package com.yision.fluidlogistics.config;

import com.google.gson.JsonObject;
import com.yision.fluidlogistics.FluidLogistics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

public class FeatureEnabledCondition implements ICondition {

    private final ResourceLocation feature;

    public FeatureEnabledCondition(ResourceLocation feature) {
        this.feature = feature;
    }

    @Override
    public ResourceLocation getID() {
        return FluidLogistics.asResource("feature_enabled");
    }

    @Override
    public boolean test(IContext context) {
        return FeatureToggle.isEnabled(feature);
    }

    public static class Serializer implements IConditionSerializer<FeatureEnabledCondition> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, FeatureEnabledCondition value) {
            json.addProperty("feature", value.feature.toString());
        }

        @Override
        public FeatureEnabledCondition read(JsonObject json) {
            String featureStr = json.get("feature").getAsString();
            ResourceLocation feature = ResourceLocation.tryParse(featureStr);
            if (feature == null) {
                throw new IllegalArgumentException("Invalid feature id: " + featureStr);
            }
            return new FeatureEnabledCondition(feature);
        }

        @Override
        public ResourceLocation getID() {
            return FluidLogistics.asResource("feature_enabled");
        }
    }
}
