package com.yision.fluidlogistics.util;

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

}
