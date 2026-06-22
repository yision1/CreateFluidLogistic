package com.yision.fluidlogistics.block.WaterContainingCopperCasing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class WaterContainingCopperCasingItem extends BlockItem {

    public WaterContainingCopperCasingItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new Renderer()));
    }

    @OnlyIn(Dist.CLIENT)
    private static class Renderer extends CustomRenderedItemModelRenderer {

        @Override
        protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
            ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
            renderer.render(model.getOriginalModel(), light);

            ms.pushPose();
            ms.translate(-0.5f, -0.5f, -0.5f);
            WaterContainingCopperCasingBlock.Renderer.renderFluid(ms, buffer, light);
            ms.popPose();
        }
    }
}
