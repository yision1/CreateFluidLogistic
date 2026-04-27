package com.yision.fluidlogistics.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.box.PackageRenderer;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.yision.fluidlogistics.item.FluidPackageItem;
import com.yision.fluidlogistics.registry.AllPartialModels;
import com.yision.fluidlogistics.render.FluidPackageItemRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

@Mixin(PackageRenderer.class)
public class PackageRendererMixin {

    @Inject(method = "render(Lcom/simibubi/create/content/logistics/box/PackageEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void fluidlogistics$onRender(PackageEntity entity, float yaw, float pt, PoseStack ms,
                                         MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (entity.box.isEmpty() || !(entity.box.getItem() instanceof FluidPackageItem)) {
            return;
        }

        PartialModel model = AllPartialModels.getFluidPackageModel(entity.box);
        if (model == null || model.get() == null) {
            return;
        }

        ci.cancel();

        SuperByteBuffer sbb = CachedBuffers.partial(model, Blocks.AIR.defaultBlockState());
        sbb.translate(-.5, .02, -.5)
            .rotateCentered(-AngleHelper.rad(yaw + 90), Direction.UP)
            .light(light)
            .nudge(entity.getId());

        sbb.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        ms.pushPose();

        TransformStack.of(ms)
            .rotate(-AngleHelper.rad(yaw + 90), Direction.UP)
            .nudge(entity.getId());

        ms.translate(0, .02, 0);

        FluidPackageItemRenderer.renderFluidContentsForEntity(entity.box, ms, buffer, light);

        ms.popPose();
    }
}
