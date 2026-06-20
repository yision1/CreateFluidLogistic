package com.yision.fluidlogistics.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorVisual;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.client.phantomchain.PhantomChainVisibility;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.item.FluidPackageItem;
import com.yision.fluidlogistics.render.FluidPackageItemRenderer;
import com.yision.fluidlogistics.render.FluidVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;

@Mixin(value = ChainConveyorVisual.class, remap = false)
public abstract class ChainConveyorVisualMixin {

    @Unique
    private FluidVisual fluidlogistics$fluidVisual;

    @Unique
    private static final float fluidlogistics$CHAIN_FLUID_Y_OFFSET = 21f / 16f;

    @Unique
    private static final float fluidlogistics$CHAIN_FLUID_EXTRA_Y_OFFSET = 5f / 16f;

    @Unique
    private static final float fluidlogistics$CHAIN_FLUID_XZ_OFFSET = .5f;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void fluidlogistics$ctor(VisualizationContext context, ChainConveyorBlockEntity blockEntity, float partialTick, CallbackInfo ci) {
        if (!Config.isAdvancedLogisticsNetworkEnabled()) {
            return;
        }
        fluidlogistics$fluidVisual = new FluidVisual(context, false, true);
    }

    @Inject(method = "beginFrame", at = @At("HEAD"), remap = false)
    private void fluidlogistics$begin(DynamicVisual.Context ctx, CallbackInfo ci) {
        if (fluidlogistics$fluidVisual == null) {
            return;
        }
        fluidlogistics$fluidVisual.begin();
    }

    @Inject(method = "_delete", at = @At("RETURN"), remap = false)
    private void fluidlogistics$delete(CallbackInfo ci) {
        if (fluidlogistics$fluidVisual == null) {
            return;
        }
        fluidlogistics$fluidVisual.delete();
    }

    @Inject(method = "beginFrame", at = @At("RETURN"), remap = false)
    private void fluidlogistics$end(DynamicVisual.Context ctx, CallbackInfo ci) {
        if (fluidlogistics$fluidVisual == null) {
            return;
        }
        fluidlogistics$fluidVisual.end();
    }

    @WrapOperation(
            method = "beginFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorVisual;setupBoxVisual(Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage;F)V",
                    ordinal = 1,
                    remap = false
            ),
            remap = false
    )
    private void fluidlogistics$hidePhantomTravellingBoxes(ChainConveyorVisual instance, ChainConveyorBlockEntity be,
                                                           ChainConveyorPackage box, float partialTicks,
                                                           Operation<Void> original,
                                                           @Local Map.Entry<BlockPos, List<ChainConveyorPackage>> entry) {
        if (!PhantomChainVisibility.shouldRenderConnection(be, entry.getKey())) {
            return;
        }
        original.call(instance, be, box, partialTicks);
    }

    @Inject(method = "setupBoxVisual", at = @At("TAIL"), remap = false)
    private void fluidlogistics$setupFluidVisual(ChainConveyorBlockEntity be, ChainConveyorPackage box, float partialTicks, CallbackInfo ci) {
        if (fluidlogistics$fluidVisual == null) {
            return;
        }
        if (!(box.item.getItem() instanceof FluidPackageItem))
            return;

        FluidStack fluid = FluidPackageItemRenderer.getVisualContainedFluid(box.item);
        if (fluid.isEmpty())
            return;

        if (box.worldPosition == null)
            return;

        ChainConveyorPackage.ChainConveyorPackagePhysicsData physicsData = box.physicsData(be.getLevel());
        if (physicsData.prevPos == null)
            return;

        Vec3 position = physicsData.prevPos.lerp(physicsData.pos, partialTicks);
        Vec3 targetPosition = physicsData.prevTargetPos.lerp(physicsData.targetPos, partialTicks);
        float yaw = AngleHelper.angleLerp(partialTicks, physicsData.prevYaw, physicsData.yaw);

        BlockPos blockPos = be.getBlockPos();
        Vec3 offset = new Vec3(targetPosition.x - blockPos.getX(), targetPosition.y - blockPos.getY(), targetPosition.z - blockPos.getZ());

        Vec3 dangleDiff = VecHelper.rotate(targetPosition.add(0, 0.5, 0)
                .subtract(position), -yaw, Direction.Axis.Y);
        float zRot = Mth.wrapDegrees((float) Mth.atan2(-dangleDiff.x, dangleDiff.y) * Mth.RAD_TO_DEG) / 2;
        float xRot = Mth.wrapDegrees((float) Mth.atan2(dangleDiff.z, dangleDiff.y) * Mth.RAD_TO_DEG) / 2;
        zRot = Mth.clamp(zRot, -25, 25);
        xRot = Mth.clamp(xRot, -25, 25);

        BlockPos containingPos = BlockPos.containing(position);
        Level level = be.getLevel();
        int light = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, containingPos),
                level.getBrightness(LightLayer.SKY, containingPos));

        TransformedInstance[] buffers = fluidlogistics$fluidVisual.setupBuffers(fluid, 0);
        if (buffers == null)
            return;

        for (int i = 0; i < buffers.length; i++) {
            TransformedInstance buf = buffers[i];
            buf.setIdentityTransform();
            buf.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            buf.translate(offset);
            buf.translate(0, 10 / 16f, 0);
            buf.rotateYDegrees(yaw);
            buf.rotateZDegrees(zRot);
            buf.rotateXDegrees(xRot);
            buf.uncenter();
            buf.translate(0, -PackageItem.getHookDistance(box.item) + 7 / 16f, 0);
            buf.translate(fluidlogistics$CHAIN_FLUID_XZ_OFFSET, 0, fluidlogistics$CHAIN_FLUID_XZ_OFFSET);
            buf.translateY(fluidlogistics$CHAIN_FLUID_Y_OFFSET);
            buf.translateY(fluidlogistics$CHAIN_FLUID_EXTRA_Y_OFFSET);
            fluidlogistics$fluidVisual.setupBuffer(fluid, Config.getFluidPerPackage(), buf, i,
                FluidPackageItemRenderer.PACKAGE_VISUAL_WIDTH,
                FluidPackageItemRenderer.PACKAGE_VISUAL_HEIGHT);
            buf.light(light);
            buf.setChanged();
        }
    }
}
