package com.yision.fluidlogistics.block.Faucet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.yision.fluidlogistics.registry.AllPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.platform.ForgeCatnipServices;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraftforge.fluids.FluidStack;

public class AbstractFaucetRenderer<T extends AbstractFaucetBlockEntity> extends SmartBlockEntityRenderer<T> {
    private static final float BELT_STREAM_BOTTOM = -1f / 16f;
    private static final float STREAM_MIN = 6f / 16f;
    private static final float STREAM_MAX = 10f / 16f;
    private static final float STREAM_TOP = 4f / 16f;

    public AbstractFaucetRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(T be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
        int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        if (be.shouldRenderSourceInterface()) {
            Direction sourceSide = be.getBlockState().getValue(AbstractFaucetBlock.FACING).getOpposite();
            renderAttachment(be, ms, buffer, light, sourceSide, ComponentPartials.DRAIN);
        }

        if (!be.hasFluidToRender() || !be.getBlockState().getValue(AbstractFaucetBlock.OPEN)) {
            return;
        }

        FluidStack fluidStack = be.getRenderingFluid();
        if (fluidStack.isEmpty()) {
            return;
        }

        ms.pushPose();
        Direction facing = be.getBlockState().getValue(AbstractFaucetBlock.FACING);
        switch (facing) {
            case SOUTH -> rotateY(ms, 180);
            case WEST -> rotateY(ms, 270);
            case EAST -> rotateY(ms, 90);
            default -> {
            }
        }

        renderFluidStream(be, fluidStack, ms, buffer, light);
        ms.popPose();
    }

    private void rotateY(PoseStack ms, float angle) {
        ms.translate(0.5, 0, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(angle));
        ms.translate(-0.5, 0, -0.5);
    }

    private void renderFluidStream(T be, FluidStack fluidStack, PoseStack ms,
        MultiBufferSource buffer, int light) {
        StreamBounds bounds = new StreamBounds(STREAM_MIN, be.isProcessingOnBelt() ? BELT_STREAM_BOTTOM : -8f / 16f,
            STREAM_MIN, STREAM_MAX, STREAM_TOP, STREAM_MAX);

        if (be.isProcessing()) {
            float progress = be.getProcessingTicks() / 20f;
            float scale = 0.75f + 0.25f * Mth.sin(progress * (float) Math.PI);
            float center = 0.5f;
            bounds = bounds.scaleHorizontally(center, scale);
        }

        renderFluidBox(fluidStack, bounds, buffer, ms, light);
    }

    private void renderAttachment(T be, PoseStack ms, MultiBufferSource buffer, int light,
        Direction direction, ComponentPartials partialType) {
        PartialModel partial = partialType == ComponentPartials.DRAIN
            ? AllPartialModels.FAUCET_SOURCE_INTERFACE.get(direction)
            : null;
        if (partial == null) {
            return;
        }
        SuperByteBuffer attachment = CachedBuffers.partial(partial, be.getBlockState());
        attachment.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private void renderFluidBox(FluidStack fluidStack, StreamBounds bounds, MultiBufferSource buffer, PoseStack ms,
        int light) {
        ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluidStack, bounds.xMin(), bounds.yMin(), bounds.zMin(),
            bounds.xMax(), bounds.yMax(), bounds.zMax(), buffer, ms, light, true, true);
    }

    private record StreamBounds(float xMin, float yMin, float zMin, float xMax, float yMax, float zMax) {
        private StreamBounds scaleHorizontally(float center, float scale) {
            return new StreamBounds(scale(center, xMin, scale), yMin, scale(center, zMin, scale),
                scale(center, xMax, scale), yMax, scale(center, zMax, scale));
        }

        private static float scale(float center, float coordinate, float scale) {
            return center + (coordinate - center) * scale;
        }
    }
}