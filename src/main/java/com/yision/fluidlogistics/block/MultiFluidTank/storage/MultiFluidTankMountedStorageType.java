package com.yision.fluidlogistics.block.MultiFluidTank.storage;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MultiFluidTankMountedStorageType extends MountedFluidStorageType<MultiFluidTankMountedStorage> {
    public MultiFluidTankMountedStorageType() {
        super(MultiFluidTankMountedStorage.CODEC);
    }

    @Override
    @Nullable
    public MultiFluidTankMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof MultiFluidTankBlockEntity tank && tank.isController()) {
            return MultiFluidTankMountedStorage.fromTank(tank);
        }
        return null;
    }
}
