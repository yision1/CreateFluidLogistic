package com.yision.fluidlogistics.api.handpointer.crafter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Registry for crafter implementations supported by CFL's Hand Pointer.
 * Register adapters during mod initialization on both client and server. A
 * selection may contain only blocks resolved to the same registered ID.
 */
public final class HandPointerCrafterAdapters {
    private static volatile List<RegisteredAdapter> adapters = List.of();

    private HandPointerCrafterAdapters() {
        throw new AssertionError("This class should not be instantiated");
    }

    /**
     * Registers an adapter under a stable namespaced ID. The first adapter that
     * matches a block wins during lookup.
     *
     * @throws IllegalArgumentException if {@code id} is already registered
     */
    public static synchronized void register(ResourceLocation id, HandPointerCrafterAdapter adapter) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(adapter, "adapter");
        if (adapters.stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IllegalArgumentException("Hand Pointer crafter adapter already registered: " + id);
        }

        List<RegisteredAdapter> updated = new ArrayList<>(adapters);
        updated.add(new RegisteredAdapter(id, adapter));
        adapters = List.copyOf(updated);
    }

    /** Resolves an adapter for a loaded block position. */
    public static Optional<RegisteredAdapter> find(Level level, BlockPos pos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        if (!level.isLoaded(pos)) {
            return Optional.empty();
        }
        return find(level, pos, level.getBlockState(pos));
    }

    /** Resolves an adapter when the caller already has the block state. */
    public static Optional<RegisteredAdapter> find(Level level, BlockPos pos, BlockState state) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        for (RegisteredAdapter entry : adapters) {
            if (entry.adapter().matches(level, pos, state)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** A resolved adapter and the stable ID used to prevent mixed selections. */
    public record RegisteredAdapter(ResourceLocation id, HandPointerCrafterAdapter adapter) {
        public RegisteredAdapter {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(adapter, "adapter");
        }
    }
}
