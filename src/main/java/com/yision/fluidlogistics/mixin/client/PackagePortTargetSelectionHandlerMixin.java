package com.yision.fluidlogistics.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperFrogportBlock;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperFrogportRules;
import com.yision.fluidlogistics.registry.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackagePortTargetSelectionHandler.class)
public class PackagePortTargetSelectionHandlerMixin {

    @ModifyExpressionValue(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"
        )
    )
    private static boolean fluidlogistics$keepCopperFrogportPreview(boolean original,
                                                                    @Local LocalPlayer player) {
        return original || AllBlocks.COPPER_FROGPORT.isIn(player.getMainHandItem());
    }

    @ModifyExpressionValue(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private static Vec3 fluidlogistics$orientCopperFrogportConnectionSource(Vec3 original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return original;
        }

        BlockPos pos = BlockPos.containing(original);
        Direction attachedDirection = null;
        if (AllBlocks.COPPER_FROGPORT.has(minecraft.level.getBlockState(pos))) {
            return CopperFrogportBlock.getConnectionSource(pos, minecraft.level.getBlockState(pos));
        } else if (minecraft.player != null
            && AllBlocks.COPPER_FROGPORT.isIn(minecraft.player.getMainHandItem())) {
            attachedDirection = getPreviewAttachedDirection(minecraft);
        }

        if (attachedDirection == null) {
            return original;
        }
        return CopperFrogportBlock.getConnectionSource(pos, attachedDirection);
    }

    @ModifyExpressionValue(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;deflate(DDD)Lnet/minecraft/world/phys/AABB;"
        )
    )
    private static AABB fluidlogistics$orientCopperFrogportPlacementPreview(AABB original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
            || !AllBlocks.COPPER_FROGPORT.isIn(minecraft.player.getMainHandItem())) {
            return original;
        }

        BlockPos pos = BlockPos.containing(original.minX, original.minY, original.minZ);
        return getPlacementPreviewBounds(pos, getPreviewAttachedDirection(minecraft));
    }

    @Inject(method = "validateDiff", at = @At("HEAD"), cancellable = true)
    private static void fluidlogistics$validateCopperFrogport(Vec3 target, BlockPos placedPos,
                                                               CallbackInfoReturnable<String> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean holdingCopperFrogport = minecraft.player != null
            && AllBlocks.COPPER_FROGPORT.isIn(minecraft.player.getMainHandItem());
        boolean placedCopperFrogport = minecraft.level != null
            && AllBlocks.COPPER_FROGPORT.has(minecraft.level.getBlockState(placedPos));
        if (!holdingCopperFrogport && !placedCopperFrogport) {
            return;
        }

        Vec3 source = Vec3.atBottomCenterOf(placedPos);
        Vec3 diff = target.subtract(source);
        Direction attachedDirection = placedCopperFrogport
            ? CopperFrogportBlock.getAttachedDirection(minecraft.level.getBlockState(placedPos))
            : getPreviewAttachedDirection(minecraft);
        if (!CopperFrogportRules.isChainHeightValid(attachedDirection, target.y, source.y)) {
            cir.setReturnValue(attachedDirection == Direction.DOWN
                ? "fluidlogistics.package_port.cannot_reach_down"
                : "fluidlogistics.package_port.cannot_reach_up");
            return;
        }
        if (diff.length() > AllConfigs.server().logistics.packagePortRange.get()) {
            cir.setReturnValue("package_port.too_far");
            return;
        }
        cir.setReturnValue(null);
    }

    private static Direction getPreviewAttachedDirection(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult blockHitResult) {
            return blockHitResult.getDirection().getOpposite();
        }
        return Direction.UP;
    }

    private static AABB getPlacementPreviewBounds(BlockPos pos, Direction attachedDirection) {
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = minX + 1;
        double maxY = minY + 1;
        double maxZ = minZ + 1;
        double inset = 0.125;

        return switch (attachedDirection) {
            case DOWN -> new AABB(minX + inset, minY, minZ + inset,
                maxX - inset, minY, maxZ - inset);
            case UP -> new AABB(minX + inset, maxY, minZ + inset,
                maxX - inset, maxY, maxZ - inset);
            case NORTH -> new AABB(minX + inset, minY + inset, minZ,
                maxX - inset, maxY - inset, minZ);
            case SOUTH -> new AABB(minX + inset, minY + inset, maxZ,
                maxX - inset, maxY - inset, maxZ);
            case WEST -> new AABB(minX, minY + inset, minZ + inset,
                minX, maxY - inset, maxZ - inset);
            case EAST -> new AABB(maxX, minY + inset, minZ + inset,
                maxX, maxY - inset, maxZ - inset);
        };
    }
}
