package com.yision.fluidlogistics.registry;

import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlock;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerGenerator;
import com.yision.fluidlogistics.block.FluidTransporter.FluidTransporterBlock;
import com.yision.fluidlogistics.block.FluidTransporter.FluidTransporterGenerator;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.HorizontalMultiFluidTankBlock;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.HorizontalMultiFluidTankModel;
import com.yision.fluidlogistics.block.MultiFluidAccessPort.MultiFluidAccessPortBlock;
import com.yision.fluidlogistics.block.MultiFluidAccessPort.MultiFluidAccessPortGenerator;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankBlock;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankGenerator;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankModel;
import com.yision.fluidlogistics.block.SmartFaucet.SmartFaucetBlock;
import com.yision.fluidlogistics.block.SmartFaucet.SmartFaucetGenerator;
import com.yision.fluidlogistics.block.SmartHopper.SmartHopperBlock;
import com.yision.fluidlogistics.block.SmartHopper.SmartHopperGenerator;
import com.yision.fluidlogistics.block.WaterproofCardboardBlock;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.HorizontalMultiFluidTankGenerator;
import com.yision.fluidlogistics.item.HorizontalMultiFluidTankItem;
import com.yision.fluidlogistics.item.MultiFluidTankItem;

import static com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType.mountedFluidStorage;
import static com.simibubi.create.foundation.data.TagGen.axeOnly;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static com.yision.fluidlogistics.FluidLogistics.REGISTRATE;

public class AllBlocks {

    public static final BlockEntry<FluidTransporterBlock> FLUID_TRANSPORTER =
        REGISTRATE.block("fluid_transporter", FluidTransporterBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new FluidTransporterGenerator()::generate)
            .item()
            .model(AssetLookup::customItemModel)
            .build()
            .register();

    public static final BlockEntry<FluidPackagerBlock> FLUID_PACKAGER = REGISTRATE.block("fluid_packager", FluidPackagerBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .properties(p -> p.isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_BLUE)
                    .sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new FluidPackagerGenerator()::generate)
            .item()
            .model(AssetLookup::customItemModel)
            .build()
            .register();

    public static final BlockEntry<SmartFaucetBlock> SMART_FAUCET =
        REGISTRATE.block("smart_faucet", SmartFaucetBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).sound(SoundType.COPPER))
            .transform(pickaxeOnly())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new SmartFaucetGenerator()::generate)
            .item()
            .model(AssetLookup::customItemModel)
            .build()
            .register();

    public static final BlockEntry<WaterproofCardboardBlock> WATERPROOF_CARDBOARD_BLOCK = REGISTRATE.block("waterproof_cardboard_block", WaterproofCardboardBlock::new)
            .initialProperties(() -> Blocks.MUSHROOM_STEM)
            .properties(p -> p.mapColor(MapColor.COLOR_BROWN)
                    .sound(SoundType.CHISELED_BOOKSHELF))
            .transform(axeOnly())
            .blockstate(BlockStateGen.horizontalAxisBlockProvider(false))
            .item()
            .build()
            .register();

    public static final BlockEntry<MultiFluidTankBlock> MULTI_FLUID_TANK = REGISTRATE.block("multi_fluid_tank", MultiFluidTankBlock::regular)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion()
                    .isRedstoneConductor((p1, p2, p3) -> true))
            .transform(pickaxeOnly())
            .blockstate(new MultiFluidTankGenerator()::generate)
            .onRegister(CreateRegistrate.blockModel(() -> MultiFluidTankModel::standard))
            .addLayer(() -> RenderType::cutoutMipped)
            .item(MultiFluidTankItem::new)
            .model(AssetLookup.customBlockItemModel("_", "block_single_window"))
            .build()
            .transform(mountedFluidStorage(AllMountedStorageTypes.MULTI_FLUID_TANK))
            .register();

    public static final BlockEntry<HorizontalMultiFluidTankBlock> HORIZONTAL_MULTI_FLUID_TANK = REGISTRATE.block("horizontal_multi_fluid_tank", HorizontalMultiFluidTankBlock::regular)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion()
                    .isRedstoneConductor((p1, p2, p3) -> true))
            .transform(pickaxeOnly())
            .blockstate(new HorizontalMultiFluidTankGenerator()::generate)
            .onRegister(CreateRegistrate.blockModel(() -> HorizontalMultiFluidTankModel::standard))
            .addLayer(() -> RenderType::cutoutMipped)
            .item(HorizontalMultiFluidTankItem::new)
            .model(AssetLookup.customBlockItemModel("horizontal_multi_fluid_tank", "block_x_single_window"))
            .build()
            .transform(mountedFluidStorage(AllMountedStorageTypes.HORIZONTAL_MULTI_FLUID_TANK))
            .register();

    public static final BlockEntry<MultiFluidAccessPortBlock> MULTI_FLUID_ACCESS_PORT =
        REGISTRATE.block("multi_fluid_access_port", MultiFluidAccessPortBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_BLUE).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new MultiFluidAccessPortGenerator()::generate)
            .item()
            .model(AssetLookup::customItemModel)
            .build()
            .register();

    public static final BlockEntry<SmartHopperBlock> SMART_HOPPER =
        REGISTRATE.block("smart_hopper", SmartHopperBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_CYAN).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new SmartHopperGenerator()::generate)
            .item()
            .model(AssetLookup.customBlockItemModel("smart_hopper", "fluid_hopper_side"))
            .build()
            .register();

    public static void register() {
    }
}
