package com.yision.fluidlogistics.registry;

import com.simibubi.create.content.kinetics.base.ShaftVisual;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerBlockEntity;
import com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerRenderer;
import com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerVisual;
import com.yision.fluidlogistics.content.logistics.fluidPackager.repackager.FluidRepackagerBlockEntity;
import com.yision.fluidlogistics.content.logistics.fluidPackager.repackager.FluidRepackagerRenderer;
import com.yision.fluidlogistics.content.logistics.fluidPackager.repackager.FluidRepackagerVisual;
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpBlockEntity;
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpRenderer;
import com.yision.fluidlogistics.content.logistics.fluidTransporter.FluidTransporterBlockEntity;
import com.yision.fluidlogistics.content.logistics.fluidTransporter.FluidTransporterRenderer;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetBlockEntity;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetRenderer;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankRenderer;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidTankRenderer;
import com.yision.fluidlogistics.content.fluids.multiFluidAccessPort.MultiFluidAccessPortBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidAccessPort.MultiFluidAccessPortRenderer;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankRenderer;
import com.yision.fluidlogistics.content.fluids.faucet.SmartFaucetBlockEntity;
import com.yision.fluidlogistics.content.fluids.faucet.SmartFaucetRenderer;
import com.yision.fluidlogistics.content.logistics.smartHopper.SmartHopperBlockEntity;
import com.yision.fluidlogistics.content.logistics.smartHopper.SmartHopperRenderer;
import com.yision.fluidlogistics.content.fluids.waterContainingCopperCasing.WaterContainingCopperCasingBlock;
import com.yision.fluidlogistics.content.processing.copperBasin.CopperBasinBlockEntity;
import com.yision.fluidlogistics.content.processing.copperBasin.CopperBasinRenderer;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunRenderer;
import com.yision.fluidlogistics.content.fluids.fluidHatch.FluidHatchBlockEntity;

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

    public static final BlockEntityEntry<FluidRepackagerBlockEntity> FLUID_REPACKAGER = REGISTRATE
            .blockEntity("fluid_repackager", FluidRepackagerBlockEntity::new)
            .visual(() -> FluidRepackagerVisual::new, true)
            .validBlocks(AllBlocks.FLUID_REPACKAGER)
            .renderer(() -> FluidRepackagerRenderer::new)
            .register();

    public static final BlockEntityEntry<FaucetBlockEntity> FAUCET = REGISTRATE
            .blockEntity("faucet", FaucetBlockEntity::new)
            .validBlocks(AllBlocks.FAUCET)
            .renderer(() -> FaucetRenderer::new)
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

    public static final BlockEntityEntry<FluidPumpBlockEntity> FLUID_PUMP = REGISTRATE
            .blockEntity("fluid_pump", FluidPumpBlockEntity::new)
            .visual(() -> ShaftVisual::new)
            .validBlocks(AllBlocks.FLUID_PUMP)
            .renderer(() -> FluidPumpRenderer::new)
            .register();

    public static final BlockEntityEntry<InfiniteFluidTankBlockEntity> INFINITE_FLUID_TANK = REGISTRATE
            .blockEntity("infinite_fluid_tank", InfiniteFluidTankBlockEntity::new)
            .validBlocks(AllBlocks.INFINITE_FLUID_TANK)
            .renderer(() -> InfiniteFluidTankRenderer::new)
            .register();

    public static final BlockEntityEntry<WaterContainingCopperCasingBlock.Entity> WATER_CONTAINING_COPPER_CASING = REGISTRATE
            .blockEntity("water_containing_copper_casing", WaterContainingCopperCasingBlock.Entity::new)
            .validBlocks(AllBlocks.WATER_CONTAINING_COPPER_CASING)
            .renderer(() -> WaterContainingCopperCasingBlock.Renderer::new)
            .register();

    public static final BlockEntityEntry<CopperBasinBlockEntity> COPPER_BASIN = REGISTRATE
            .blockEntity("copper_basin", CopperBasinBlockEntity::new)
            .validBlocks(AllBlocks.COPPER_BASIN)
            .renderer(() -> CopperBasinRenderer::new)
            .register();

    public static final BlockEntityEntry<MechanicalFluidGunBlockEntity> MECHANICAL_FLUID_GUN = REGISTRATE
            .blockEntity("mechanical_fluid_gun", MechanicalFluidGunBlockEntity::new)
            .validBlocks(AllBlocks.MECHANICAL_FLUID_GUN)
            .renderer(() -> MechanicalFluidGunRenderer::new)
            .register();

    public static final BlockEntityEntry<FluidHatchBlockEntity> FLUID_HATCH = REGISTRATE
            .blockEntity("fluid_hatch", FluidHatchBlockEntity::new)
            .validBlocks(AllBlocks.FLUID_HATCH)
            .renderer(() -> SmartBlockEntityRenderer::new)
            .register();

    public static void register() {
    }
}
