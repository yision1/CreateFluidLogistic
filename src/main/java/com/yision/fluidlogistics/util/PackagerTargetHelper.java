package com.yision.fluidlogistics.util;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PackagerTargetHelper {

    private PackagerTargetHelper() {
    }

    public static boolean isToggleTarget(BlockEntity blockEntity, BlockState state) {
        return blockEntity instanceof PackagerBlockEntity
            && state.getBlock() instanceof PackagerBlock
            && state.hasProperty(PackagerBlock.POWERED);
    }

    public static boolean isClipboardAddressTarget(BlockEntity blockEntity, BlockState state) {
        return blockEntity instanceof PackagerBlockEntity && isClipboardAddressBlock(state);
    }

    public static boolean isClipboardAddressBlock(BlockState state) {
        return AllBlocks.PACKAGER.has(state)
            || com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state);
    }
}
