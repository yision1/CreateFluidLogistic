package com.yision.fluidlogistics.foundation.fluid;

import com.simibubi.create.foundation.ICapabilityProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class CachedFluidInterface {

    private @Nullable ICapabilityProvider<IFluidHandler> sided;
    private @Nullable ICapabilityProvider<IFluidHandler> unsided;
    private @Nullable BlockPos cachedPos;
    private @Nullable Direction cachedSide;

    public @Nullable IFluidHandler get(Level level, BlockPos pos, @Nullable Direction side) {
        ensureCaches(level, pos, side);
        ICapabilityProvider<IFluidHandler> sidedProvider = sided;
        IFluidHandler handler = sidedProvider == null ? null : sidedProvider.getCapability();
        if (handler != null) {
            return handler;
        }
        ICapabilityProvider<IFluidHandler> unsidedProvider = unsided;
        return unsidedProvider == null ? null : unsidedProvider.getCapability();
    }

    public void invalidate() {
        sided = null;
        unsided = null;
        cachedPos = null;
        cachedSide = null;
    }

    private void ensureCaches(Level level, BlockPos pos, @Nullable Direction side) {
        if (!pos.equals(cachedPos) || side != cachedSide) {
            invalidate();
            cachedPos = pos.immutable();
            cachedSide = side;
        }
        if (sided == null) {
            sided = createProvider(level, pos, side, true);
        }
        if (unsided == null) {
            unsided = createProvider(level, pos, null, false);
        }
    }

    private ICapabilityProvider<IFluidHandler> createProvider(Level level, BlockPos pos, @Nullable Direction side,
        boolean sidedProvider) {
        if (level instanceof ServerLevel serverLevel) {
            return ICapabilityProvider.of(invalidate -> BlockCapabilityCache.create(
                Capabilities.FluidHandler.BLOCK,
                serverLevel,
                pos.immutable(),
                side,
                () -> true,
                () -> {
                    if (sidedProvider) {
                        sided = null;
                    } else {
                        unsided = null;
                    }
                    invalidate.run();
                }));
        }
        return ICapabilityProvider.of(() -> level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side));
    }
}
