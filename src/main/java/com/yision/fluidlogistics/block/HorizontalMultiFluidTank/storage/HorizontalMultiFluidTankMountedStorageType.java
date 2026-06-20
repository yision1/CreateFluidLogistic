package com.yision.fluidlogistics.block.HorizontalMultiFluidTank.storage;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HorizontalMultiFluidTankMountedStorageType extends MountedFluidStorageType<HorizontalMultiFluidTankMountedStorage> {
    public HorizontalMultiFluidTankMountedStorageType() {
        super(HorizontalMultiFluidTankMountedStorage.CODEC);
    }

    @Override
    @Nullable
    public HorizontalMultiFluidTankMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof HorizontalMultiFluidTankBlockEntity tank && tank.isController()) {
            return HorizontalMultiFluidTankMountedStorage.fromTank(tank);
        }
        return null;
    }
}
