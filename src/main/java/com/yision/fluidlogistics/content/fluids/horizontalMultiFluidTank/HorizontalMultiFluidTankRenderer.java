package com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.SmartMultiFluidTank;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;

public class HorizontalMultiFluidTankRenderer extends SafeBlockEntityRenderer<HorizontalMultiFluidTankBlockEntity> {

    public HorizontalMultiFluidTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(HorizontalMultiFluidTankBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        if (!be.isController())
            return;
        if (!be.window) {
            return;
        }
        if (be.tankInventory.isEmpty()) {
            return;
        }

        LerpedFloat[] fluidLevel = be.getFluidLevel();
        if (fluidLevel == null)
            return;

        float capSize = 1 / 4f;
        float tankHullSize = 1 / 16f + 1 / 128f;
        float minPuddleHeight = 1 / 16f;
        float totalHeight = be.getWidth() - 2 * tankHullSize - minPuddleHeight;

        SmartMultiFluidTank tank = be.tankInventory;
        if (tank.isEmpty()) return;
        FluidStack[] fluidStacks = tank.getFluids();
        float accHeight = 0;
        for (int i = 0; i < fluidStacks.length; i++) {
            FluidStack fluidStack = fluidStacks[i];
            if (fluidStack.isEmpty())
                continue;

            float level = fluidLevel[i].getValue(partialTicks);
            if (level < 1 / (512f * totalHeight))
                continue;
            float clampedLevel = Mth.clamp(level * totalHeight, 0, totalHeight);

            Axis axis = be.getAxis();
            float xMin = axis == Axis.X ? capSize : tankHullSize;
            float xMax = axis == Axis.X ? xMin + be.getHeight() - 2 * capSize : xMin + be.getWidth() - 2 * tankHullSize;
            float yMin = totalHeight + tankHullSize + minPuddleHeight - clampedLevel;
            float yMax = yMin + clampedLevel;
            float zMin = axis == Axis.Z ? capSize : tankHullSize;
            float zMax = axis == Axis.Z ? zMin + be.getHeight() - 2 * capSize : zMin + be.getWidth() - 2 * tankHullSize;

            ms.pushPose();
            ms.translate(0, clampedLevel - totalHeight + accHeight, 0);
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluidStack, xMin, yMin, zMin, xMax, yMax, zMax, buffer, ms, light, false, true);
            ms.popPose();

            accHeight += clampedLevel;
        }
    }

    @Override
    public boolean shouldRenderOffScreen(HorizontalMultiFluidTankBlockEntity be) {
        return be.isController();
    }
}
