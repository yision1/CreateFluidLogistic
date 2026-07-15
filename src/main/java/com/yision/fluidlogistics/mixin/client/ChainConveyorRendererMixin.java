package com.yision.fluidlogistics.mixin.client;

import java.util.List;
import java.util.Map;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.phantomChain.PhantomChainVisibility;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.FluidPackageItemRenderer;
import com.yision.fluidlogistics.util.PhantomChainConveyorAccess;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChainConveyorRenderer.class)
public class ChainConveyorRendererMixin {
    @Unique
    private static final ResourceLocation fluidlogistics$PHANTOM_CHAIN_TEXTURE =
        FluidLogistics.asResource("textures/block/phantom_chain.png");

    @Unique
    private static final ThreadLocal<ResourceLocation> fluidlogistics$chainTextureOverride = new ThreadLocal<>();

    @WrapOperation(
            method = "renderChains",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorRenderer;renderChain(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;FFIIZ)V"
            )
    )
    private void fluidlogistics$skipPhantomChainStrip(PoseStack ms, MultiBufferSource buffer, float animation,
                                                      float length, int light1, int light2, boolean far,
                                                      Operation<Void> original,
                                                      @Local(argsOnly = true) ChainConveyorBlockEntity be,
                                                      @Local(ordinal = 0) BlockPos blockPos) {
        if (!PhantomChainVisibility.shouldRenderConnection(be, blockPos)) {
            return;
        }
        if (!((PhantomChainConveyorAccess) be).fluidlogistics$isPhantomConnection(blockPos)) {
            original.call(ms, buffer, animation, length, light1, light2, far);
            return;
        }

        fluidlogistics$chainTextureOverride.set(fluidlogistics$PHANTOM_CHAIN_TEXTURE);
        try {
            original.call(ms, buffer, animation, length, light1, light2, far);
        } finally {
            fluidlogistics$chainTextureOverride.remove();
        }
    }

    @ModifyExpressionValue(
            method = "renderChain",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorRenderer;CHAIN_LOCATION:Lnet/minecraft/resources/ResourceLocation;"
            )
    )
    private static ResourceLocation fluidlogistics$usePhantomChainTexture(ResourceLocation original) {
        ResourceLocation override = fluidlogistics$chainTextureOverride.get();
        return override == null ? original : override;
    }

    @WrapOperation(
            method = "renderSafe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorRenderer;renderBox(Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/core/BlockPos;Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage;F)V"
            ),
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;travellingPackages:Ljava/util/Map;"
                    )
            )
    )
    private void fluidlogistics$skipPhantomTravellingBox(ChainConveyorRenderer instance, ChainConveyorBlockEntity be,
                                                        PoseStack ms, MultiBufferSource buffer, int overlay,
                                                        BlockPos pos, ChainConveyorPackage box, float partialTicks,
                                                        Operation<Void> original,
                                                        @Local Map.Entry<BlockPos, List<ChainConveyorPackage>> entry) {
        if (!PhantomChainVisibility.shouldRenderConnection(be, entry.getKey())) {
            return;
        }
        original.call(instance, be, ms, buffer, overlay, pos, box, partialTicks);
    }

    @Inject(
            method = "renderBox",
            at = @At("TAIL")
    )
    private void fluidlogistics$renderFluid(ChainConveyorBlockEntity be, PoseStack ms, MultiBufferSource buffer,
                                            int overlay, BlockPos pos, ChainConveyorPackage box, float partialTicks,
                                            CallbackInfo ci) {
        if (!(box.item.getItem() instanceof FluidPackageItem)) {
            return;
        }

        ChainConveyorPackage.ChainConveyorPackagePhysicsData physicsData = box.physicsData(be.getLevel());
        if (physicsData.prevPos == null) {
            return;
        }

        Vec3 position = physicsData.prevPos.lerp(physicsData.pos, partialTicks);
        Vec3 targetPosition = physicsData.prevTargetPos.lerp(physicsData.targetPos, partialTicks);
        float yaw = AngleHelper.angleLerp(partialTicks, physicsData.prevYaw, physicsData.yaw);
        Vec3 offset =
            new Vec3(targetPosition.x - pos.getX(), targetPosition.y - pos.getY(), targetPosition.z - pos.getZ());

        BlockPos containingPos = BlockPos.containing(position);
        Level level = be.getLevel();
        int light = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, containingPos),
            level.getBrightness(LightLayer.SKY, containingPos));

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
