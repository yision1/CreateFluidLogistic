package com.yision.fluidlogistics.network;

import com.yision.fluidlogistics.item.HandPointerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

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
}
