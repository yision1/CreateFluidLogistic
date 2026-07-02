package com.yision.fluidlogistics.registry;

import com.simibubi.create.content.fluids.PipeAttachmentModel;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerBlock;
import com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerGenerator;
import com.yision.fluidlogistics.content.logistics.fluidPackager.repackager.FluidRepackagerBlock;
import com.yision.fluidlogistics.content.logistics.fluidPackager.repackager.FluidRepackagerGenerator;
import com.yision.fluidlogistics.content.logistics.fluidTransporter.FluidTransporterBlock;
import com.yision.fluidlogistics.content.logistics.fluidTransporter.FluidTransporterGenerator;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankBlock;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankModel;
import com.yision.fluidlogistics.content.fluids.multiFluidAccessPort.MultiFluidAccessPortBlock;
import com.yision.fluidlogistics.content.fluids.multiFluidAccessPort.MultiFluidAccessPortGenerator;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankBlock;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankGenerator;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankModel;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetBlock;
import com.yision.fluidlogistics.content.fluids.faucet.FaucetGenerator;
import com.yision.fluidlogistics.content.fluids.faucet.SmartFaucetBlock;
import com.yision.fluidlogistics.content.logistics.smartHopper.SmartHopperBlock;
import com.yision.fluidlogistics.content.logistics.smartHopper.SmartHopperGenerator;
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpBlock;
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpGenerator;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunGenerator;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunItem;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidTankBlock;
import com.yision.fluidlogistics.content.fluids.waterContainingCopperCasing.WaterContainingCopperCasingBlock;
import com.yision.fluidlogistics.content.processing.copperBasin.CopperBasinBlock;
import com.yision.fluidlogistics.content.materials.WaterproofCardboardBlock;
import com.yision.fluidlogistics.content.fluids.fluidHatch.FluidHatchBlock;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankGenerator;
import com.simibubi.create.content.processing.basin.BasinGenerator;
import com.simibubi.create.content.processing.basin.BasinMovementBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankItem;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidTankItem;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankItem;
import com.yision.fluidlogistics.content.fluids.waterContainingCopperCasing.WaterContainingCopperCasingItem;

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
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new FluidTransporterGenerator()::generate)
            .item()
            .model(AssetLookup.customBlockItemModel("fluid_transporter", "block_vertical"))
            .build()
            .register();

    public static final BlockEntry<FluidPackagerBlock> FLUID_PACKAGER = REGISTRATE.block("fluid_packager", FluidPackagerBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .properties(p -> p.isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_BLUE)
                    .sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new FluidPackagerGenerator()::generate)
            .item()
            .model(AssetLookup::customItemModel)
            .build()
            .register();

    public static final BlockEntry<FluidRepackagerBlock> FLUID_REPACKAGER = REGISTRATE.block("fluid_repackager", FluidRepackagerBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .properties(p -> p.isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_BLUE)
                    .sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new FluidRepackagerGenerator()::generate)
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
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new FaucetGenerator("smart_faucet/smart_faucet", "smart_faucet/smart_faucet_open")::generate)
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/smart_faucet/smart_faucet"))
                .transforms()
                .transform(ItemDisplayContext.GUI)
                .rotation(30, -135, 0)
                .translation(2.5f, -1.25f, 0)
                .scale(0.625f)
                .end()
                .transform(ItemDisplayContext.FIXED)
                .translation(0, 0, -2)
                .scale(0.5f))
            .build()
            .register();

    public static final BlockEntry<FaucetBlock> FAUCET =
        REGISTRATE.block("faucet", FaucetBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).sound(SoundType.COPPER))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new FaucetGenerator("faucet/faucet", "faucet/faucet_open")::generate)
            .item()
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/faucet/faucet"))
                .transforms()
                .transform(ItemDisplayContext.GUI)
                .rotation(30, -135, 0)
                .translation(2.5f, -1.25f, 0)
                .scale(0.625f)
                .end()
                .transform(ItemDisplayContext.FIXED)
                .translation(0, 0, -2)
                .scale(0.5f))
            .build()
            .register();

    public static final BlockEntry<WaterproofCardboardBlock> WATERPROOF_CARDBOARD_BLOCK = REGISTRATE.block("waterproof_cardboard_block", WaterproofCardboardBlock::new)
            .initialProperties(() -> Blocks.MUSHROOM_STEM)
            .properties(p -> p.mapColor(MapColor.COLOR_BROWN)
                    .sound(SoundType.CHISELED_BOOKSHELF))
            .transform(axeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .blockstate((ctx, prov) -> BlockStateGen.horizontalAxisBlock(ctx, prov,
                    $ -> prov.models().getExistingFile(prov.modLoc("block/waterproof_cardboard_block/block"))))
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(),
                    prov.modLoc("block/waterproof_cardboard_block/block")))
            .build()
            .register();

    public static final BlockEntry<MultiFluidTankBlock> MULTI_FLUID_TANK = REGISTRATE.block("multi_fluid_tank", MultiFluidTankBlock::regular)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion()
                    .isRedstoneConductor((p1, p2, p3) -> true))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
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
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
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
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new MultiFluidAccessPortGenerator()::generate)
            .item()
            .model(AssetLookup.customBlockItemModel("multi_fluid_access_port", "block_wall_off"))
            .build()
            .register();

    public static final BlockEntry<SmartHopperBlock> SMART_HOPPER =
        REGISTRATE.block("smart_hopper", SmartHopperBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_CYAN).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new SmartHopperGenerator()::generate)
            .item()
            .model(AssetLookup.customBlockItemModel("smart_hopper", "fluid_hopper_side"))
            .build()
            .register();

    public static final BlockEntry<FluidPumpBlock> FLUID_PUMP =
        REGISTRATE.block("fluid_pump", FluidPumpBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.STONE).sound(SoundType.COPPER))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(new FluidPumpGenerator()::generate)
            .onRegister(CreateRegistrate.blockModel(() -> PipeAttachmentModel::withAO))
            .item()
            .model(AssetLookup::customItemModel)
            .build()
            .register();

    public static final BlockEntry<InfiniteFluidTankBlock> INFINITE_FLUID_TANK =
        REGISTRATE.block("infinite_fluid_tank", InfiniteFluidTankBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion()
                .mapColor(MapColor.COLOR_PURPLE)
                .isRedstoneConductor(($1, $2, $3) -> true))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
                prov.models().getExistingFile(prov.modLoc("block/infinite_fluid_tank/block"))))
            .loot((loot, block) -> loot.add(block, LootTable.lootTable()
                .withPool(loot.applyExplosionCondition(block, LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0F))
                    .add(LootItem.lootTableItem(block)
                        .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                            .include(DataComponents.BLOCK_ENTITY_DATA)))))))
            .item(InfiniteFluidTankItem::new)
            .properties(p -> p.rarity(Rarity.EPIC))
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/infinite_fluid_tank/block")))
            .build()
            .register();

    public static final BlockEntry<WaterContainingCopperCasingBlock> WATER_CONTAINING_COPPER_CASING =
        REGISTRATE.block("water_containing_copper_casing", WaterContainingCopperCasingBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).sound(SoundType.COPPER))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
                prov.models().getExistingFile(prov.modLoc("block/water_containing_copper_casing/block"))))
            .item(WaterContainingCopperCasingItem::new)
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(),
                prov.modLoc("block/water_containing_copper_casing/block")))
            .build()
            .register();

    public static final BlockEntry<CopperBasinBlock> COPPER_BASIN =
        REGISTRATE.block("copper_basin", CopperBasinBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.noOcclusion()
                .mapColor(MapColor.COLOR_GRAY)
                .sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .blockstate(new BasinGenerator()::generate)
            .addLayer(() -> RenderType::cutoutMipped)
            .onRegister(b -> MovementBehaviour.REGISTRY.register(b, new BasinMovementBehaviour()))
            .item()
            .model(AssetLookup.customBlockItemModel("_", "block"))
            .build()
            .register();

    public static final BlockEntry<MechanicalFluidGunBlock> MECHANICAL_FLUID_GUN =
        REGISTRATE.block("mechanical_fluid_gun", MechanicalFluidGunBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).sound(SoundType.COPPER))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(MechanicalFluidGunGenerator::generate)
            .item(MechanicalFluidGunItem::new)
            .model(AssetLookup::customItemModel)
            .build()
            .register();

    public static final BlockEntry<FluidHatchBlock> FLUID_HATCH =
        REGISTRATE.block("fluid_hatch", FluidHatchBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .properties(p -> p.noOcclusion().isRedstoneConductor(($1, $2, $3) -> false))
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY))
            .transform(pickaxeOnly())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate((ctx, prov) -> {
                var model = prov.models().getExistingFile(prov.modLoc("block/fluid_hatch"));
                prov.getVariantBuilder(ctx.get()).forAllStates(state -> {
                    Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                    int y = switch (facing) {
                        case NORTH -> 0;
                        case SOUTH -> 180;
                        case EAST -> 90;
                        case WEST -> 270;
                        default -> 0;
                    };
                    return new ConfiguredModel[]{new ConfiguredModel(model, 0, y, false)};
                });
            })
            .item()
            .model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.modLoc("block/fluid_hatch")))
            .build()
            .register();

    public static void register() {
    }
}
