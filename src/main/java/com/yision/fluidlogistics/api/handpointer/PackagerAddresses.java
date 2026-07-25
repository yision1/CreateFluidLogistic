package com.yision.fluidlogistics.api.handpointer;

import java.util.function.Supplier;

import com.yision.fluidlogistics.content.equipment.handPointer.PackagerAddressRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class PackagerAddresses {
    private static final PackagerAddressRegistry REGISTRY = PackagerAddressRegistry.instance();

    private PackagerAddresses() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static void register(Supplier<? extends Block> block) {
        REGISTRY.register(block);
    }

    public static boolean isTarget(Level level, BlockPos pos) {
        return REGISTRY.isTarget(level, pos);
    }

    public static EditResult set(Level level, BlockPos pos, String address) {
        return REGISTRY.set(level, pos, address);
    }

    public static EditResult clear(Level level, BlockPos pos) {
        return REGISTRY.clear(level, pos);
    }

    public enum EditResult {
        UPDATED,
        NOT_TARGET,
        NETWORK_LINKED,
        SIGN_CONTROLLED,
        ALREADY_EMPTY
    }
}
