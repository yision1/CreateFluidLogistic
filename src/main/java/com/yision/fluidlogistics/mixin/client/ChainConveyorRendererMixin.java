package com.yision.fluidlogistics.mixin.client;

import java.util.List;
import java.util.Map;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.phantomChain.PhantomChainVisibility;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.FluidPackageItemRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ChainConveyorRenderer.class, remap = false)
public class ChainConveyorRendererMixin {

    @Unique
    private static final ThreadLocal<ResourceLocation> fluidlogistics$phantomChainTextureOverride = new ThreadLocal<>();

    @Unique
    private static final ResourceLocation fluidlogistics$PHANTOM_CHAIN_LOCATION =
        FluidLogistics.asResource("textures/block/phantom_chain.png");

    @ModifyExpressionValue(
            method = "renderChain",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorRenderer;CHAIN_LOCATION:Lnet/minecraft/resources/ResourceLocation;",
                    remap = false
            ),
            remap = false
    )
    private static ResourceLocation fluidlogistics$overrideChainTexture(ResourceLocation original) {
        ResourceLocation override = fluidlogistics$phantomChainTextureOverride.get();
        return override != null ? override : original;
    }

    @WrapOperation(
            method = "renderChains",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorRenderer;renderChain(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;FFIIZ)V",
                    remap = false
            ),
            remap = false
    )
    private void fluidlogistics$renderPhantomAwareChain(PoseStack ms, MultiBufferSource buffer,
                                                        float animation, float length, int light1, int light2, boolean far,
                                                        Operation<Void> original,
                                                        @Local(argsOnly = true) ChainConveyorBlockEntity be,
                                                        @Local(ordinal = 0) BlockPos blockPos) {
        if (!PhantomChainVisibility.shouldRenderConnection(be, blockPos)) {
            return;
        }

        boolean phantomRevealed = PhantomChainVisibility.isRevealing()
            && ((com.yision.fluidlogistics.util.PhantomChainConveyorAccess) be)
                .fluidlogistics$isPhantomConnection(blockPos);
        if (phantomRevealed) {
            fluidlogistics$phantomChainTextureOverride.set(fluidlogistics$PHANTOM_CHAIN_LOCATION);
        }
        try {
            original.call(ms, buffer, animation, length, light1, light2, far);
        } finally {
            if (phantomRevealed) {
                fluidlogistics$phantomChainTextureOverride.remove();
            }
        }
    }

    @WrapOperation(
            method = "renderSafe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorRenderer;renderBox(Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/core/BlockPos;Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage;F)V",
                    ordinal = 1,
                    remap = false
            ),
            remap = false
    )
    private void fluidlogistics$hidePhantomTravellingBoxes(ChainConveyorRenderer instance, ChainConveyorBlockEntity be,
                                                           PoseStack ms, MultiBufferSource buffer, int overlay,
                                                           BlockPos pos, ChainConveyorPackage box, float partialTicks,
                                                           Operation<Void> original,
                                                           @Local Map.Entry<BlockPos, List<ChainConveyorPackage>> entry) {
        if (!PhantomChainVisibility.shouldRenderConnection(be, entry.getKey())) {
            return;
        }
        original.call(instance, be, ms, buffer, overlay, pos, box, partialTicks);
    }

    @WrapOperation(
            method = "renderBox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/render/SuperByteBuffer;renderInto(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V",
                    remap = false
            )
    )
    private void fluidlogistics$renderFluid(SuperByteBuffer instance, PoseStack ms, VertexConsumer vertexConsumer, Operation<Void> original,
                                            @Local(argsOnly = true) ChainConveyorBlockEntity be,
                                            @Local(argsOnly = true) MultiBufferSource buffer,
                                            @Local(argsOnly = true) BlockPos pos,
                                            @Local(argsOnly = true) ChainConveyorPackage box,
                                            @Local(argsOnly = true) float partialTicks,
                                            @Local(ordinal = 1) SuperByteBuffer boxBuffer,
                                            @Local(ordinal = 1) int light) {
        original.call(instance, ms, vertexConsumer);

        if (boxBuffer == instance && box.item.getItem() instanceof FluidPackageItem) {
            ChainConveyorPackage.ChainConveyorPackagePhysicsData physicsData = box.physicsData(be.getLevel());
            if (physicsData.prevPos == null) {
                return;
            }

            Vec3 position = physicsData.prevPos.lerp(physicsData.pos, partialTicks);
            Vec3 targetPosition = physicsData.prevTargetPos.lerp(physicsData.targetPos, partialTicks);
            float yaw = AngleHelper.angleLerp(partialTicks, physicsData.prevYaw, physicsData.yaw);
            Vec3 offset =
                    new Vec3(targetPosition.x - pos.getX(), targetPosition.y - pos.getY(), targetPosition.z - pos.getZ());

            Vec3 dangleDiff = VecHelper.rotate(targetPosition.add(0, 0.5, 0).subtract(position), -yaw, Direction.Axis.Y);
            float zRot = Mth.wrapDegrees((float) Mth.atan2(-dangleDiff.x, dangleDiff.y) * Mth.RAD_TO_DEG) / 2;
            float xRot = Mth.wrapDegrees((float) Mth.atan2(dangleDiff.z, dangleDiff.y) * Mth.RAD_TO_DEG) / 2;
            zRot = Mth.clamp(zRot, -25, 25);
            xRot = Mth.clamp(xRot, -25, 25);

            ms.pushPose();
            ms.translate(offset.x, offset.y + 10 / 16f, offset.z);
            ms.mulPose(Axis.YP.rotationDegrees(yaw));
            ms.mulPose(Axis.ZP.rotationDegrees(zRot));
            ms.mulPose(Axis.XP.rotationDegrees(xRot));
            ms.translate(0, -PackageItem.getHookDistance(box.item) + 7 / 16f, 0);
            ms.translate(0, -0.5f, 0);

            FluidPackageItemRenderer.renderFluidContentsForEntity(box.item, -1, ms, buffer, light);

            ms.popPose();
        }
    }
}
