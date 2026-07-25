package com.yision.fluidlogistics.foundation.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class CachedFluidInterface {

    private @Nullable LazyOptional<IFluidHandler> sided;
    private @Nullable LazyOptional<IFluidHandler> unsided;
    private @Nullable BlockEntity cachedEntity;
    private @Nullable BlockPos cachedPos;
    private @Nullable Direction cachedSide;

    public @Nullable IFluidHandler get(Level level, BlockPos pos, @Nullable Direction side) {
        ensureCaches(level, pos, side);
        LazyOptional<IFluidHandler> sidedProvider = sided;
        IFluidHandler handler = sidedProvider == null ? null : sidedProvider.orElse(null);
        if (handler != null) {
            return handler;
        }
        LazyOptional<IFluidHandler> unsidedProvider = unsided;
        return unsidedProvider == null ? null : unsidedProvider.orElse(null);
    }

    public void invalidate() {
        sided = null;
        unsided = null;
        cachedEntity = null;
        cachedPos = null;
        cachedSide = null;
    }

    private void ensureCaches(Level level, BlockPos pos, @Nullable Direction side) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!pos.equals(cachedPos) || side != cachedSide || entity != cachedEntity) {
            invalidate();
            cachedEntity = entity;
            cachedPos = pos.immutable();
            cachedSide = side;
        }
        if (entity == null) {
            return;
        }
        if (sided == null) {
            sided = createProvider(entity, side, true);
        }
        if (unsided == null) {
            unsided = createProvider(entity, null, false);
        }
    }

    private LazyOptional<IFluidHandler> createProvider(BlockEntity entity, @Nullable Direction side,
        boolean sidedProvider) {
        LazyOptional<IFluidHandler> provider = entity.getCapability(ForgeCapabilities.FLUID_HANDLER, side);
        provider.addListener(invalidated -> {
            if (sidedProvider) {
                if (sided == invalidated) {
                    sided = null;
                }
            } else if (unsided == invalidated) {
                unsided = null;
            }
        });
        return provider;
    }
}
