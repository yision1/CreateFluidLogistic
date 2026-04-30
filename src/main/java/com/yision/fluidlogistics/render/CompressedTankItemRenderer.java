package com.yision.fluidlogistics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import com.yision.fluidlogistics.util.VirtualFluidDisplayHelper;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
public class CompressedTankItemRenderer extends CustomRenderedItemModelRenderer {

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
            ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        FluidStack fluid = VirtualFluidDisplayHelper.getDisplayFluid(stack);
        if (!fluid.isEmpty() && shouldRenderFluidIcon(displayContext)) {
            FluidSlotRenderer.renderFluidItemIcon(fluid, ms, buffer, light);
            return;
        }

        renderer.render(model.getOriginalModel(), light);
    }

    private static boolean shouldRenderFluidIcon(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.GUI
                || displayContext == ItemDisplayContext.GROUND
                || displayContext == ItemDisplayContext.FIXED
                || displayContext == ItemDisplayContext.HEAD;
    }
}
