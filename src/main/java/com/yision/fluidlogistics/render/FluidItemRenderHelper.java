package com.yision.fluidlogistics.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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
        public VertexConsumer addVertex(float x, float y, float z) {
            wrapped.addVertex(x, y, z).setOverlay(OverlayTexture.NO_OVERLAY);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            wrapped.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            wrapped.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            wrapped.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            wrapped.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            wrapped.setNormal(x, y, z);
            return this;
        }
    }
}
