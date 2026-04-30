package com.yision.fluidlogistics.mixin.client;

import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.yision.fluidlogistics.render.FluidSlotRenderer;
import com.yision.fluidlogistics.util.VirtualFluidDisplayHelper;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
@Mixin(ValueBoxRenderer.class)
public class ValueBoxRendererMixin {

    @Inject(
        method = "renderItemIntoValueBox",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private static void fluidlogistics$renderFluidIntoValueBox(ItemStack filter, PoseStack ms, 
            MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        FluidStack fluid = VirtualFluidDisplayHelper.getDisplayFluid(filter);
        if (!fluid.isEmpty()) {
            FluidSlotRenderer.renderFluidInWorld(fluid, ms, buffer, light);
            ci.cancel();
        }
    }

    @Inject(
        method = "renderFlatItemIntoValueBox",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private static void fluidlogistics$renderFlatFluidIntoValueBox(ItemStack filter, PoseStack ms,
            MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        FluidStack fluid = VirtualFluidDisplayHelper.getDisplayFluid(filter);
        if (fluid.isEmpty()) {
            return;
        }

        int blockLight = light >> 4 & 0xf;
        int skyLight = light >> 20 & 0xf;
        int itemLight = Mth.floor(skyLight + .5) << 20 | (Mth.floor(blockLight + .5) & 0xf) << 4;

        ms.pushPose();
        TransformStack.of(ms)
            .rotateXDegrees(230);
        Matrix3f normal = new Matrix3f(ms.last()
            .normal());
        ms.popPose();

        ms.pushPose();
        TransformStack.of(ms)
            .translate(0, 0, -1 / 4f)
            .translate(0, 0, 1 / 32f + .001)
            .rotateYDegrees(180);

        PoseStack squashedMS = new PoseStack();
        squashedMS.last()
            .pose()
            .mul(ms.last()
                .pose());
        squashedMS.scale(.5f, .5f, 1 / 1024f);
        squashedMS.last()
            .normal()
            .set(normal);

        FluidSlotRenderer.renderFluidItemIcon(fluid, squashedMS, buffer, itemLight);

        ms.popPose();
        ci.cancel();
    }
}
