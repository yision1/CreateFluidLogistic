package com.yision.fluidlogistics.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.simibubi.create.content.processing.basin.BasinRenderer;
import com.yision.fluidlogistics.util.CreateBasinCapacity;

@Mixin(value = BasinRenderer.class, remap = false)
public class BasinRendererMixin {

    @ModifyConstant(method = "renderFluids", constant = @Constant(floatValue = 2000.0F))
    private float fluidlogistics$scaleRenderedFluidHeight(float original) {
        return CreateBasinCapacity.TOTAL_CAPACITY;
    }
}
