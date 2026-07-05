package com.yision.fluidlogistics.content.equipment.handPointer.network;

import com.yision.fluidlogistics.content.equipment.handPointer.HandPointerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Shared server-side guard for hand pointer packets. Replaces the early-exit
 * that used to be provided by the advanced logistics config check, gating only
 * on build permission, chunk load, distance and whether the player is actually
 * holding a hand pointer.
 */
final class HandPointerInteractionGuard {

    private HandPointerInteractionGuard() {
    }

    static boolean canUseHandPointer(ServerPlayer player, BlockPos pos) {
        if (!player.mayBuild()) {
            return false;
        }

        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return false;
        }

        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return false;
        }

        return player.getMainHandItem().getItem() instanceof HandPointerItem;
    }

    static boolean canUseHandPointer(ServerPlayer player, BlockPos pos, ItemStack handPointerStack) {
        if (!player.mayBuild()) {
            return false;
        }

        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return false;
        }

        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return false;
        }

        return handPointerStack != null && handPointerStack.getItem() instanceof HandPointerItem;
    }
}
