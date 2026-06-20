package com.yision.fluidlogistics.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageVisual;
import com.yision.fluidlogistics.item.FluidPackageItem;

import dev.engine_room.flywheel.lib.instance.TransformedInstance;

@Mixin(value = PackageVisual.class, remap = false)
public abstract class PackageVisualMixin {

    @Shadow(remap = false)
    @Final
    public TransformedInstance instance;

    @Unique
    private boolean fluidlogistics$isFluidPackage = false;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void fluidlogistics$onInitTail(dev.engine_room.flywheel.api.visualization.VisualizationContext ctx, PackageEntity entity, float partialTick, CallbackInfo ci) {
        if (entity.box.getItem() instanceof FluidPackageItem) {
            fluidlogistics$isFluidPackage = true;
            if (instance != null) {
                instance.setZeroTransform().setChanged();
            }
        }
    }

    @Inject(method = "beginFrame", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$onBeginFrameHead(PackageVisual.Context ctx, CallbackInfo ci) {
        if (fluidlogistics$isFluidPackage) {
            ci.cancel();
        }
    }
}
