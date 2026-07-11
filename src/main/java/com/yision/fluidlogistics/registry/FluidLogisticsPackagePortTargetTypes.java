package com.yision.fluidlogistics.registry;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetType;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperChainConveyorFrogportTarget;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FluidLogisticsPackagePortTargetTypes {

    private static final DeferredRegister<PackagePortTargetType> REGISTER =
        DeferredRegister.create(CreateBuiltInRegistries.PACKAGE_PORT_TARGET_TYPE.key(), FluidLogistics.MODID);

    public static final DeferredHolder<PackagePortTargetType, PackagePortTargetType> COPPER_CHAIN_CONVEYOR =
        REGISTER.register("copper_chain_conveyor", CopperChainConveyorFrogportTarget.Type::new);

    private FluidLogisticsPackagePortTargetTypes() {
    }

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}
