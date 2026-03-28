package com.yision.fluidlogistics.block.FluidTransporter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class FluidTransporterRenderer extends SmartBlockEntityRenderer<FluidTransporterBlockEntity> {

    public FluidTransporterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(FluidTransporterBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
        MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(blockEntity, partialTicks, poseStack, buffer, light, overlay);

        for (Direction direction : Iterate.directions) {
            if (!blockEntity.shouldRenderInterface(direction)) {
                continue;
            }

            renderAttachment(blockEntity, poseStack, buffer, light, direction, ComponentPartials.RIM_CONNECTOR);
            renderAttachment(blockEntity, poseStack, buffer, light, direction, ComponentPartials.DRAIN);
        }
    }

    private void renderAttachment(FluidTransporterBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffer,
        int light, Direction direction, ComponentPartials partialType) {
        PartialModel partial = AllPartialModels.PIPE_ATTACHMENTS.get(partialType).get(direction);
        SuperByteBuffer attachment = CachedBuffers.partial(partial, blockEntity.getBlockState());
        attachment.light(light).renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
    }
}
