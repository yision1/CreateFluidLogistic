package com.yision.fluidlogistics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;

import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FactoryPanelFluidPreviewRenderer {
    private static final float PREVIEW_SCALE = 1.625f;
    private static final float BLOCK_GUI_SCALE = 0.625f;

    private FactoryPanelFluidPreviewRenderer() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static void render(GuiGraphics graphics, ItemStack resourceKey, int x, int y) {
        FluidStack fluid = CompressedTankItem.getFluid(resourceKey);
        if (fluid.isEmpty() || fluid.getFluid() == Fluids.EMPTY) {
            return;
        }

        FluidStack renderFluid = fluid.getAmount() == 0 ? fluid.copyWithAmount(1) : fluid;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 100);
        pose.scale(PREVIEW_SCALE, PREVIEW_SCALE, PREVIEW_SCALE);
        UIRenderHelper.flipForGuiRender(pose);

        pose.translate(0, 0, 100);
        pose.translate(8, -8, 0);
        pose.scale(16, 16, 16);
        pose.mulPose(Axis.XP.rotationDegrees(30));
        pose.mulPose(Axis.YP.rotationDegrees(225));
        pose.scale(BLOCK_GUI_SCALE, BLOCK_GUI_SCALE, BLOCK_GUI_SCALE);
        pose.translate(-0.5f, -0.5f, -0.5f);
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(renderFluid,
                0, 0, 0, 1, 1, 1,
                graphics.bufferSource(), pose, LightTexture.FULL_BRIGHT, false, true);
        graphics.flush();
        pose.popPose();
    }
}
