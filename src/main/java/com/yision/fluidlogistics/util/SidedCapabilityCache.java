package com.yision.fluidlogistics.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public final class SidedCapabilityCache<T> {
    private final EnumMap<Direction, BlockCapabilityCache<T, @Nullable Direction>> caches =
        new EnumMap<>(Direction.class);
    private final BlockCapability<T, @Nullable Direction> capability;

    public SidedCapabilityCache(BlockCapability<T, @Nullable Direction> capability) {
        this.capability = capability;
    }

    @Nullable
    public T get(Level level, BlockPos targetPos, Direction side) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return level.getCapability(capability, targetPos, side.getOpposite());
        }
        BlockCapabilityCache<T, @Nullable Direction> cache = caches.get(side);
        if (cache == null) {
            cache = BlockCapabilityCache.create(capability, serverLevel, targetPos, side.getOpposite());
            caches.put(side, cache);
        }
        return cache.getCapability();
    }

    public void clear() {
        caches.clear();
    }
}
