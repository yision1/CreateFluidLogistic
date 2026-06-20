package com.yision.fluidlogistics.compat.sable;

import com.yision.fluidlogistics.compat.CompatMods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class SableSublevelTargetHelper {

    private SableSublevelTargetHelper() {
    }

    public record Target(BlockPos resolvedPos, @Nullable BlockEntity blockEntity) {
        public static Target of(BlockPos pos, @Nullable BlockEntity entity) {
            return new Target(pos, entity);
        }
    }

    public static Target resolveBlockEntity(Level level, BlockPos probePos) {
        if (!CompatMods.sableLoaded()) {
            return new Target(probePos, level.getBlockEntity(probePos));
        }
        return SableLoadedTargetResolver.resolveBlockEntity(level, probePos);
    }

    public static boolean isSameBlockAcrossSublevels(Level level, BlockPos expectedWorldPos, BlockPos actualLocalPos) {
        if (!CompatMods.sableLoaded()) {
            return expectedWorldPos.equals(actualLocalPos);
        }

        Target target = resolveBlockEntity(level, expectedWorldPos);
        return target.blockEntity() != null && target.resolvedPos().equals(actualLocalPos);
    }
}
