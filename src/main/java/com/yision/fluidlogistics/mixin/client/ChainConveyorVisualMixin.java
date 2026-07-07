package com.yision.fluidlogistics.mixin.client;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorVisual;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.phantomChain.PhantomChainVisibility;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.FluidPackageItemRenderer;
import com.yision.fluidlogistics.render.FluidVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ChainConveyorVisual.class)
public class ChainConveyorVisualMixin {

    @Unique
    private FluidVisual fluidlogistics$fluidVisual;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void fluidlogistics$ctor(VisualizationContext context, ChainConveyorBlockEntity blockEntity, float partialTick, CallbackInfo ci) {
        fluidlogistics$fluidVisual = new FluidVisual(context, false, true);
    }

    @Inject(
            method = "beginFrame",
            at = @At("HEAD")
    )
    private void fluidlogistics$begin(DynamicVisual.Context ctx, CallbackInfo ci) {
        if (fluidlogistics$fluidVisual == null) {
            return;
        }
        fluidlogistics$fluidVisual.begin();
    }

    @Inject(
            method = "_delete",
            at = @At("RETURN")
    )
    private void fluidlogistics$delete(CallbackInfo ci) {
        if (fluidlogistics$fluidVisual == null) {
            return;
        }
        fluidlogistics$fluidVisual.delete();
    }

    @Inject(
            method = "beginFrame",
            at = @At("RETURN")
    )
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
                    ordinal = 1
            )
    )
    private void fluidlogistics$skipPhantomTravellingBoxVisual(ChainConveyorVisual instance,
                                                               ChainConveyorBlockEntity be, ChainConveyorPackage box,
                                                               float partialTicks, Operation<Void> original,
                                                               @Local(ordinal = 0) Map.Entry<BlockPos, List<ChainConveyorPackage>> entry) {
        if (!PhantomChainVisibility.shouldRenderConnection(be, entry.getKey())) {
            return;
        }
        original.call(instance, be, box, partialTicks);
    }

    @Definition(id = "TransformedInstance", type = TransformedInstance.class)
    @Expression("new TransformedInstance[]{?,?}")
    @ModifyExpressionValue(
            method = "setupBoxVisual",
            at = @At("MIXINEXTRAS:EXPRESSION")
    )
    private TransformedInstance[] fluidlogistics$setupFluidBuffers(TransformedInstance[] original,
                                                                   @Local(argsOnly = true) ChainConveyorPackage box,
                                                                   @Share("fluid") LocalRef<FluidStack> fluid,
                                                                   @Share("fluidBuffers") LocalRef<TransformedInstance[]> fluidBuffers) {
        fluid.set(null);
        fluidBuffers.set(null);

        if (fluidlogistics$fluidVisual == null) {
            return original;
        }
        if (!(box.item.getItem() instanceof FluidPackageItem)) return original;

        fluid.set(FluidPackageItemRenderer.getPrimaryContainedFluid(box.item));

        if (fluid.get().isEmpty()) return original;

        TransformedInstance[] buffers = fluidlogistics$fluidVisual.setupBuffers(fluid.get(), original.length);
        if (buffers == null) return original;

        System.arraycopy(original, 0, buffers, 0, original.length);

        TransformedInstance[] addedBuffers = new TransformedInstance[buffers.length - original.length];
        System.arraycopy(buffers, original.length, addedBuffers, 0, addedBuffers.length);
        fluidBuffers.set(addedBuffers);

        return buffers;
    }

    @WrapOperation(
            method = "setupBoxVisual",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/lib/instance/TransformedInstance;light(I)Ldev/engine_room/flywheel/lib/instance/ColoredLitInstance;"
            )
    )
    private dev.engine_room.flywheel.lib.instance.ColoredLitInstance fluidlogistics$setupFluidVisual(
            TransformedInstance instance, int light, Operation<dev.engine_room.flywheel.lib.instance.ColoredLitInstance> original,
            @Local(ordinal = 0) TransformedInstance rigBuffer,
            @Local(ordinal = 1) TransformedInstance boxBuffer,
            @Share("fluidBuffers") LocalRef<TransformedInstance[]> fluidBuffers,
            @Share("fluid") LocalRef<FluidStack> fluid) {
        if (fluidlogistics$fluidVisual == null) {
            return original.call(instance, light);
        }
        if (instance == rigBuffer || instance == boxBuffer) {
            return original.call(instance, light);
        }
        if (fluid.get() == null || fluid.get().isEmpty()) {
            return original.call(instance, light);
        }

        int fluidBufferIndex = fluidlogistics$getFluidBufferIndex(fluidBuffers.get(), instance);
        if (fluidBufferIndex == -1) {
            return original.call(instance, light);
        }

        fluidlogistics$fluidVisual.setupBuffer(fluid.get(), Config.getFluidPerPackage(), instance, fluidBufferIndex,
            FluidPackageItemRenderer.FLUID_MIN_XZ,
            FluidPackageItemRenderer.FLUID_MAX_XZ,
            FluidPackageItemRenderer.FLUID_MIN_Y,
            FluidPackageItemRenderer.FLUID_MAX_Y);

        return original.call(instance, light);
    }

    @Unique
    private static int fluidlogistics$getFluidBufferIndex(TransformedInstance[] buffers, TransformedInstance instance) {
        if (buffers == null) {
            return -1;
        }
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] == instance) {
                return i;
            }
        }
        return -1;
    }
}
