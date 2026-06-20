package com.yision.fluidlogistics.registry;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.storage.HorizontalMultiFluidTankMountedStorageType;
import com.yision.fluidlogistics.block.MultiFluidTank.storage.MultiFluidTankMountedStorageType;
import com.tterrag.registrate.util.entry.RegistryEntry;

public class AllMountedStorageTypes {
    private static final CreateRegistrate REGISTRATE = FluidLogistics.REGISTRATE;

    public static final RegistryEntry<MultiFluidTankMountedStorageType> MULTI_FLUID_TANK = 
        REGISTRATE.mountedFluidStorage("multi_fluid_tank", MultiFluidTankMountedStorageType::new)
            .register();

    public static final RegistryEntry<HorizontalMultiFluidTankMountedStorageType> HORIZONTAL_MULTI_FLUID_TANK = 
        REGISTRATE.mountedFluidStorage("horizontal_multi_fluid_tank", HorizontalMultiFluidTankMountedStorageType::new)
            .register();

    public static void register() {
    }
}
