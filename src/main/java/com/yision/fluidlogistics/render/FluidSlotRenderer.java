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

    public static void renderFluidInWorld(FluidStack stack, PoseStack ms, MultiBufferSource buffer, int light) {
        if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY) {
            return;
        }

        FluidStack renderStack = stack;
        if (stack.getAmount() == 0) {
            renderStack = stack.copyWithAmount(1);
        }

        float size = 1.0f / 5.0f;
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
                renderStack,
                -size, -size, -1.0f / 32.0f,
                size, size, 0,
                buffer, ms, light, true, false);
    }

    public static void renderFluidItemIcon(FluidStack stack, PoseStack ms, MultiBufferSource buffer, int light) {
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
                -0.5f, -0.5f, -depth,
                0.5f, 0.5f, 0,
                buffer, ms, light, true, false);
    }
}
