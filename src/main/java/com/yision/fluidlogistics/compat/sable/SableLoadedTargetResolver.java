package com.yision.fluidlogistics.compat.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

final class SableLoadedTargetResolver {

    private SableLoadedTargetResolver() {
    }

    static SableSublevelTargetHelper.Target resolveBlockEntity(Level level, BlockPos probePos) {
        SubLevel initialSubLevel = Sable.HELPER.getContaining(level, probePos);
        SableSublevelTargetHelper.Target result = Sable.HELPER.runIncludingSubLevels(
            level,
            probePos.getCenter(),
            true,
            initialSubLevel,
            (subLevel, internalPos) -> {
                BlockEntity be = level.getBlockEntity(internalPos);
                return be != null ? SableSublevelTargetHelper.Target.of(internalPos, be) : null;
            }
        );

        return result != null ? result : SableSublevelTargetHelper.Target.of(probePos, null);
    }
}
