package com.yision.fluidlogistics.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public class FluidSlotRenderer {

    public static void renderFluidSlot(GuiGraphics graphics, int x, int y, FluidStack stack) {
        if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY) {
            return;
        }

        FluidStack renderStack = stack;
        if (stack.getAmount() == 0) {
            renderStack = stack.copy();
            renderStack.setAmount(1);
        }

        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(renderStack.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(clientFluid.getStillTexture(renderStack));

        int color = clientFluid.getTintColor(renderStack);
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        if (a == 0) {
            a = 1.0f;
        }

        graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFF8B8B8B);
        graphics.blit(x + 1, y + 1, 2, 14, 14, sprite, r, g, b, a);
    }

    public static void renderFluidInWorld(FluidStack stack, PoseStack ms, MultiBufferSource buffer, int light) {
        if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY) {
            return;
        }

        FluidStack renderStack = stack;
        if (stack.getAmount() == 0) {
            renderStack = stack.copy();
            renderStack.setAmount(1);
        }

        float size = 1.0f / 5.0f;
        ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(renderStack, -size, -size, -1.0f / 32.0f, size, size, 0,
            buffer, ms, light, true, false);
    }

    public static void renderFluidItemIcon(FluidStack stack, PoseStack ms, MultiBufferSource buffer, int light) {
        if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY) {
            return;
        }

        FluidStack renderStack = stack;
        if (stack.getAmount() == 0) {
            renderStack = stack.copy();
            renderStack.setAmount(1);
        }

        float depth = 1.0f / 32.0f;
        ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(renderStack, -0.5f, -0.5f, -depth, 0.5f, 0.5f, 0,
            buffer, ms, light, true, false);
    }
}
