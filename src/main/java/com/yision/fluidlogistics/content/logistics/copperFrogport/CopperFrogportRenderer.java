package com.yision.fluidlogistics.content.logistics.copperFrogport;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.yision.fluidlogistics.registry.AllPartialModels;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CopperFrogportRenderer extends SmartBlockEntityRenderer<CopperFrogportBlockEntity> {

    public CopperFrogportRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CopperFrogportBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                              MultiBufferSource buffer, int light, int overlay) {
        if (blockEntity.addressFilter != null && !blockEntity.addressFilter.isBlank()) {
            renderNameplateOnHover(
                blockEntity,
                Component.literal(blockEntity.addressFilter),
                1,
                poseStack,
                buffer,
                light
            );
        }

        if (VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
            return;
        }

        CopperFrogportAnimationState animation = CopperFrogportAnimationState.create(blockEntity, partialTicks);

        poseStack.pushPose();
        applyAttachmentTransform(poseStack, animation);

        SuperByteBuffer body = CachedBuffers.partial(
            AllPartialModels.COPPER_FROGPORT_BODY,
            blockEntity.getBlockState()
        );
        body.center()
            .rotateYDegrees(animation.yaw())
            .uncenter()
            .light(light)
            .overlay(overlay)
            .renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));

        SuperByteBuffer head = CachedBuffers.partial(
            blockEntity.goggles
                ? AllPartialModels.COPPER_FROGPORT_HEAD_GOGGLES
                : AllPartialModels.COPPER_FROGPORT_HEAD,
            blockEntity.getBlockState()
        );
        head.center()
            .rotateYDegrees(animation.yaw())
            .uncenter()
            .translate(8 / 16f, 10 / 16f, 11 / 16f)
            .rotateXDegrees(animation.headPitch())
            .translateBack(8 / 16f, 10 / 16f, 11 / 16f)
            .light(light)
            .overlay(overlay)
            .renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));

        SuperByteBuffer tongue =
            CachedBuffers.partial(AllPartialModels.COPPER_FROGPORT_TONGUE, blockEntity.getBlockState());
        tongue.center()
            .rotateYDegrees(animation.yaw())
            .uncenter()
            .translate(8 / 16f, 10 / 16f, 11 / 16f)
            .rotateXDegrees(animation.tonguePitch())
            .scale(1, 1, animation.tongueLength() / (7 / 16f))
            .translateBack(8 / 16f, 10 / 16f, 11 / 16f)
            .light(light)
            .overlay(overlay)
            .renderInto(poseStack, buffer.getBuffer(RenderType.cutoutMipped()));

        poseStack.popPose();

        if (animation.animating()) {
            renderPackage(
                blockEntity,
                poseStack,
                buffer,
                light,
                overlay,
                animation
            );
        }
    }

    private void renderPackage(CopperFrogportBlockEntity blockEntity, PoseStack poseStack,
                               MultiBufferSource buffer, int light, int overlay,
                               CopperFrogportAnimationState animation) {
        if (blockEntity.animatedPackage == null || animation.packageScale() < 0.45) {
            return;
        }

        ResourceLocation key = BuiltInRegistries.ITEM.getKey(blockEntity.animatedPackage.getItem());
        if (key == BuiltInRegistries.ITEM.getDefaultKey()) {
            return;
        }

        SuperByteBuffer rig = CachedBuffers.partial(
            com.simibubi.create.AllPartialModels.PACKAGE_RIGGING.get(key),
            blockEntity.getBlockState()
        );
        SuperByteBuffer box = CachedBuffers.partial(
            com.simibubi.create.AllPartialModels.PACKAGES.get(key),
            blockEntity.getBlockState()
        );

        boolean depositing = blockEntity.currentlyDepositing;
        var worldOffset = animation.packageOffset(blockEntity);
        for (SuperByteBuffer rendered : new SuperByteBuffer[] {box, rig}) {
            rendered.translate(worldOffset)
                .center()
                .scale(animation.packageScale())
                .uncenter()
                .light(light)
                .overlay(overlay)
                .renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
            if (!depositing) {
                break;
            }
        }
    }

    private static void applyAttachmentTransform(PoseStack poseStack,
                                                 CopperFrogportAnimationState animation) {
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(animation.orientation());
        poseStack.translate(-0.5, -0.5, -0.5);
    }
}
