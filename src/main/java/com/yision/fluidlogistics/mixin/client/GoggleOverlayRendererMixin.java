package com.yision.fluidlogistics.mixin.client;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public class GoggleOverlayRendererMixin {

    @Definition(id = "BlockHitResult", type = BlockHitResult.class)
    @Expression("? instanceof BlockHitResult")
    @WrapOperation(
            method = "renderOverlay",
            at = @At("MIXINEXTRAS:EXPRESSION")
    )
    private static boolean fluidlogistics$wrapInstanceOfCheck(Object object, Operation<Boolean> original,
                                                              @Local LocalRef<HitResult> hitResult,
                                                              @Share("fluidlogistics$entityHitResult") LocalRef<EntityHitResult> result) {
        if (object instanceof EntityHitResult entityResult && entityResult.getType() != HitResult.Type.MISS) {
            if (entityResult.getEntity() instanceof IHaveGoggleInformation) {
                result.set(entityResult);
                hitResult.set(
                        new BlockHitResult(entityResult.getLocation(), Direction.getNearest(entityResult.getLocation().x, entityResult.getLocation().y, entityResult.getLocation().z),
                                           BlockPos.containing(entityResult.getLocation()), true));
                return true;
            }
        }
        return original.call(object);
    }

    @Definition(id = "IHaveGoggleInformation", type = IHaveGoggleInformation.class)
    @Expression("? instanceof IHaveGoggleInformation")
    @ModifyExpressionValue(
            method = "renderOverlay",
            at = @At("MIXINEXTRAS:EXPRESSION"),
            remap = false
    )
    private static boolean fluidlogistics$modifyBlockEntityInstanceOfCheck(boolean original,
                                                                           @Share("fluidlogistics$entityHitResult") LocalRef<EntityHitResult> result) {
        if (result.get() != null && result.get().getEntity() instanceof IHaveGoggleInformation) {
            return true;
        }
        return original;
    }

    @Definition(id = "IHaveGoggleInformation", type = IHaveGoggleInformation.class)
    @Expression("(IHaveGoggleInformation) ?")
    @WrapOperation(
            method = "renderOverlay",
            at = @At("MIXINEXTRAS:EXPRESSION")
    )
    private static IHaveGoggleInformation fluidlogistics$wrapBlockEntityCast(Object object, Operation<IHaveGoggleInformation> original,
                                                                              @Share("fluidlogistics$entityHitResult") LocalRef<EntityHitResult> result) {
        if (result.get() != null && result.get().getEntity() instanceof IHaveGoggleInformation gte) {
            return gte;
        }
        return original.call(object);
    }
}
