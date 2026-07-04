package com.yision.fluidlogistics.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
public class FluidSlotRenderer {

    public static void renderFluidItemIcon(FluidStack stack, PoseStack ms, MultiBufferSource buffer, int light) {
        renderFluidItemIcon(stack, ms, buffer, light, 0.5f);
    }

    public static void renderFluidItemIcon(FluidStack stack, PoseStack ms, MultiBufferSource buffer, int light,
            float halfSize) {
        if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY) {
            return;
        }

        FluidStack renderStack = stack;
        if (stack.getAmount() == 0) {
            renderStack = stack.copyWithAmount(1);
        }

        float depth = 1.0f / 32.0f;
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
                renderStack,
                -halfSize, -halfSize, -depth,
                halfSize, halfSize, 0,
                buffer, ms, light, true, false);
    }
}
