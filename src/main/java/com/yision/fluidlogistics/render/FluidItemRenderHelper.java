package com.yision.fluidlogistics.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FluidItemRenderHelper {

    private FluidItemRenderHelper() {
    }

    public static VertexConsumer getFluidBuilder(MultiBufferSource buffer, ItemDisplayContext displayContext) {
        if (displayContext != null && displayContext.firstPerson())
            return new OverlayFillingVertexConsumer(
                buffer.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS)));
        return FluidRenderHelper.getFluidBuilder(buffer);
    }

    private static final class OverlayFillingVertexConsumer implements VertexConsumer {

        private final VertexConsumer wrapped;

        private OverlayFillingVertexConsumer(VertexConsumer wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            wrapped.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            wrapped.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            wrapped.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            wrapped.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            wrapped.overlayCoords(OverlayTexture.NO_OVERLAY);
            wrapped.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            wrapped.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            wrapped.endVertex();
        }

        @Override
        public void defaultColor(int defaultR, int defaultG, int defaultB, int defaultA) {
            wrapped.defaultColor(defaultR, defaultG, defaultB, defaultA);
        }

        @Override
        public void unsetDefaultColor() {
            wrapped.unsetDefaultColor();
        }
    }
}
