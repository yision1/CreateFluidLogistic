package com.yision.fluidlogistics.block.Faucet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.yision.fluidlogistics.registry.AllPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

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
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        TextureAtlasSprite texture = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(fluidExtensions.getStillTexture(fluidStack));
        int color = fluidExtensions.getTintColor(fluidStack);
        int blockLight = (light >> 4) & 0xF;
        int luminosity = Math.max(blockLight, fluidStack.getFluid().getFluidType().getLightLevel(fluidStack));
        light = (light & 0xF00000) | luminosity << 4;

        VertexConsumer builder = buffer.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = ms.last();
        emitFace(Direction.UP, bounds.xMin(), bounds.zMin(), bounds.xMax(), bounds.zMax(), bounds.yMax(), builder, pose,
            light, color, texture);
        emitFace(Direction.NORTH, bounds.xMin(), bounds.yMin(), bounds.xMax(), bounds.yMax(), bounds.zMin(), builder,
            pose, light, color, texture);
        emitFace(Direction.SOUTH, bounds.xMin(), bounds.yMin(), bounds.xMax(), bounds.yMax(), bounds.zMax(), builder,
            pose, light, color, texture);
        emitFace(Direction.WEST, bounds.zMin(), bounds.yMin(), bounds.zMax(), bounds.yMax(), bounds.xMin(), builder,
            pose, light, color, texture);
        emitFace(Direction.EAST, bounds.zMin(), bounds.yMin(), bounds.zMax(), bounds.yMax(), bounds.xMax(), builder,
            pose, light, color, texture);
    }

    private void emitFace(Direction direction, float minU, float minV, float maxU, float maxV, float depth,
        VertexConsumer builder, PoseStack.Pose pose, int light, int color, TextureAtlasSprite texture) {
        float shrink = texture.uvShrinkRatio() * 0.25f;
        float centerU = Mth.lerp(0.5f, texture.getU0(), texture.getU1());
        float centerV = Mth.lerp(0.5f, texture.getV0(), texture.getV1());
        boolean mirrored = direction == Direction.NORTH || direction == Direction.EAST;

        for (float currentU = minU; currentU < maxU;) {
            float nextU = nextTileBoundary(currentU, maxU);
            UvWindow uWindow = sampleWindow(texture, currentU, nextU, mirrored, shrink, centerU, true);

            for (float currentV = minV; currentV < maxV;) {
                float nextV = nextTileBoundary(currentV, maxV);
                UvWindow vWindow = sampleWindow(texture, currentV, nextV, direction != Direction.UP, shrink, centerV,
                    false);
                emitQuad(direction, depth, currentU, nextU, currentV, nextV, uWindow, vWindow, builder, pose, light,
                    color);
                currentV = nextV;
            }

            currentU = nextU;
        }
    }

    private float nextTileBoundary(float current, float max) {
        return Math.min(Mth.floor(current) + 1, max);
    }

    private UvWindow sampleWindow(TextureAtlasSprite texture, float start, float end, boolean reverse, float shrink,
        float center, boolean useU) {
        float tile = reverse ? Mth.ceil(end) : Mth.floor(start);
        float first = reverse ? tile - end : start - tile;
        float second = reverse ? tile - start : end - tile;
        float low = useU ? texture.getU(first) : texture.getV(first);
        float high = useU ? texture.getU(second) : texture.getV(second);
        return new UvWindow(Mth.lerp(shrink, low, center), Mth.lerp(shrink, high, center));
    }

    private void emitQuad(Direction direction, float depth, float startU, float endU, float startV, float endV,
        UvWindow uWindow, UvWindow vWindow, VertexConsumer builder, PoseStack.Pose pose, int light, int color) {
        if (direction.getAxis().isHorizontal()) {
            emitSideQuad(direction, depth, startU, endU, startV, endV, uWindow, vWindow, builder, pose, light, color);
            return;
        }

        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        emitVertex(builder, pose, startU, depth, positive ? startV : endV, color, uWindow.min(), vWindow.min(),
            direction, light);
        emitVertex(builder, pose, startU, depth, positive ? endV : startV, color, uWindow.min(), vWindow.max(),
            direction, light);
        emitVertex(builder, pose, endU, depth, positive ? endV : startV, color, uWindow.max(), vWindow.max(),
            direction, light);
        emitVertex(builder, pose, endU, depth, positive ? startV : endV, color, uWindow.max(), vWindow.min(),
            direction, light);
    }

    private void emitSideQuad(Direction direction, float depth, float startU, float endU, float startV, float endV,
        UvWindow uWindow, UvWindow vWindow, VertexConsumer builder, PoseStack.Pose pose, int light, int color) {
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        if (direction.getAxis() == Direction.Axis.X) {
            float nearZ = positive ? endU : startU;
            float farZ = positive ? startU : endU;
            emitVertex(builder, pose, depth, endV, nearZ, color, uWindow.min(), vWindow.min(), direction, light);
            emitVertex(builder, pose, depth, startV, nearZ, color, uWindow.min(), vWindow.max(), direction, light);
            emitVertex(builder, pose, depth, startV, farZ, color, uWindow.max(), vWindow.max(), direction, light);
            emitVertex(builder, pose, depth, endV, farZ, color, uWindow.max(), vWindow.min(), direction, light);
            return;
        }

        float nearX = positive ? startU : endU;
        float farX = positive ? endU : startU;
        emitVertex(builder, pose, nearX, endV, depth, color, uWindow.min(), vWindow.min(), direction, light);
        emitVertex(builder, pose, nearX, startV, depth, color, uWindow.min(), vWindow.max(), direction, light);
        emitVertex(builder, pose, farX, startV, depth, color, uWindow.max(), vWindow.max(), direction, light);
        emitVertex(builder, pose, farX, endV, depth, color, uWindow.max(), vWindow.min(), direction, light);
    }

    private void emitVertex(VertexConsumer builder, PoseStack.Pose pose, float x, float y, float z, int color, float u,
        float v, Direction face, int light) {
        Vec3i normal = face.getNormal();
        int a = color >> 24 & 0xFF;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;

        builder.addVertex(pose.pose(), x, y, z)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setLight(light)
            .setNormal(pose, normal.getX(), normal.getY(), normal.getZ());
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

    private record UvWindow(float min, float max) {
    }
}
