package com.yision.fluidlogistics.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public final class SidedCapabilityCache<T> {
    private final EnumMap<Direction, LazyOptional<T>> caches = new EnumMap<>(Direction.class);
    private final Capability<T> capability;

    public SidedCapabilityCache(Capability<T> capability) {
        this.capability = capability;
    }

    @Nullable
    public T get(Level level, BlockPos targetPos, Direction side) {
        LazyOptional<T> cache = caches.get(side);
        if (cache == null || !cache.isPresent()) {
            BlockEntity blockEntity = level.getBlockEntity(targetPos);
            if (blockEntity == null) {
                caches.remove(side);
                return null;
            }
            LazyOptional<T> resolved = blockEntity.getCapability(capability, side.getOpposite());
            if (!resolved.isPresent()) {
                caches.remove(side);
                return null;
            }
            caches.put(side, resolved);
            resolved.addListener(opt -> caches.remove(side, resolved));
            cache = resolved;
        }
        return cache.orElse(null);
    }

    public void clear() {
        caches.clear();
    }
}
