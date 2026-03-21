package com.yision.fluidlogistics.mixin.client;

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
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.yision.fluidlogistics.item.FluidPackageItem;
import com.yision.fluidlogistics.render.FluidPackageItemRenderer;

@Mixin(ChainConveyorRenderer.class)
public class ChainConveyorRendererMixin {
    @WrapOperation(
            method = "renderBox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/render/SuperByteBuffer;renderInto(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"
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
