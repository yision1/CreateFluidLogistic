package com.yision.fluidlogistics.registry;

import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlockEntity;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerRenderer;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerVisual;
import com.yision.fluidlogistics.block.FluidTransporter.FluidTransporterBlockEntity;
import com.yision.fluidlogistics.block.FluidTransporter.FluidTransporterRenderer;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.HorizontalMultiFluidTankRenderer;
import com.yision.fluidlogistics.block.MultiFluidAccessPort.MultiFluidAccessPortBlockEntity;
import com.yision.fluidlogistics.block.MultiFluidAccessPort.MultiFluidAccessPortRenderer;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankRenderer;
import com.yision.fluidlogistics.block.SmartFaucet.SmartFaucetBlockEntity;
import com.yision.fluidlogistics.block.SmartFaucet.SmartFaucetRenderer;
import com.yision.fluidlogistics.block.SmartHopper.SmartHopperBlockEntity;
import com.yision.fluidlogistics.block.SmartHopper.SmartHopperRenderer;

import static com.yision.fluidlogistics.FluidLogistics.REGISTRATE;

public class AllBlockEntities {

    public static final BlockEntityEntry<FluidTransporterBlockEntity> FLUID_TRANSPORTER = REGISTRATE
            .blockEntity("fluid_transporter", FluidTransporterBlockEntity::new)
            .validBlocks(AllBlocks.FLUID_TRANSPORTER)
            .renderer(() -> FluidTransporterRenderer::new)
            .register();

    public static final BlockEntityEntry<FluidPackagerBlockEntity> FLUID_PACKAGER = REGISTRATE
            .blockEntity("fluid_packager", FluidPackagerBlockEntity::new)
            .visual(() -> FluidPackagerVisual::new, true)
            .validBlocks(AllBlocks.FLUID_PACKAGER)
            .renderer(() -> FluidPackagerRenderer::new)
            .register();

    public static final BlockEntityEntry<SmartFaucetBlockEntity> SMART_FAUCET = REGISTRATE
            .blockEntity("smart_faucet", SmartFaucetBlockEntity::new)
            .validBlocks(AllBlocks.SMART_FAUCET)
            .renderer(() -> SmartFaucetRenderer::new)
            .register();

    public static final BlockEntityEntry<MultiFluidTankBlockEntity> MULTI_FLUID_TANK = REGISTRATE
            .blockEntity("multi_fluid_tank", MultiFluidTankBlockEntity::new)
            .validBlocks(AllBlocks.MULTI_FLUID_TANK)
            .renderer(() -> MultiFluidTankRenderer::new)
            .register();

    public static final BlockEntityEntry<HorizontalMultiFluidTankBlockEntity> HORIZONTAL_MULTI_FLUID_TANK = REGISTRATE
            .blockEntity("horizontal_multi_fluid_tank", HorizontalMultiFluidTankBlockEntity::new)
            .validBlocks(AllBlocks.HORIZONTAL_MULTI_FLUID_TANK)
            .renderer(() -> HorizontalMultiFluidTankRenderer::new)
            .register();

    public static final BlockEntityEntry<MultiFluidAccessPortBlockEntity> MULTI_FLUID_ACCESS_PORT = REGISTRATE
            .blockEntity("multi_fluid_access_port", MultiFluidAccessPortBlockEntity::new)
            .validBlocks(AllBlocks.MULTI_FLUID_ACCESS_PORT)
            .renderer(() -> MultiFluidAccessPortRenderer::new)
            .register();

    public static final BlockEntityEntry<SmartHopperBlockEntity> SMART_HOPPER = REGISTRATE
            .blockEntity("smart_hopper", SmartHopperBlockEntity::new)
            .validBlocks(AllBlocks.SMART_HOPPER)
            .renderer(() -> SmartHopperRenderer::new)
            .register();

    public static void register() {
    }
}
