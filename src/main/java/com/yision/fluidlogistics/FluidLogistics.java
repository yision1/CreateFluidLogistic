package com.yision.fluidlogistics;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.block.WaterContainingCopperCasing.WaterContainingCopperCasingFluidHandler;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlockEntity;
import com.yision.fluidlogistics.block.FluidRepackager.FluidRepackagerBlockEntity;
import com.yision.fluidlogistics.block.FluidTransporter.FluidTransporterBlockEntity;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.block.InfiniteFluidTank.InfiniteFluidTankBlockEntity;
import com.yision.fluidlogistics.block.MultiFluidAccessPort.MultiFluidAccessPortBlockEntity;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.block.SmartHopper.SmartHopperBlockEntity;
import com.yision.fluidlogistics.block.CopperBasin.CopperBasinBlockEntity;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.registry.FluidLogisticsArmInteractionPointTypes;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.registry.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.item.CompressedTankFluidHandler;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.item.CompressedTankTooltipModifier;
import com.yision.fluidlogistics.item.InfiniteFluidTankItem;
import com.yision.fluidlogistics.registry.AllDataComponents;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.registry.AllConditionCodecs;
import com.yision.fluidlogistics.registry.AllFluidAttributeTypes;
import com.yision.fluidlogistics.registry.FluidLogisticsUnpackingHandlers;
import com.yision.fluidlogistics.registry.AllFluidLogisticsParticleTypes;
import com.yision.fluidlogistics.config.FeatureToggle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;

@Mod(FluidLogistics.MODID)
public class FluidLogistics {
    public static final String MODID = "fluidlogistics";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<net.minecraft.world.item.CreativeModeTab> FLUID_LOGISTICS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, asResource("fluidlogistics_tab"));
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final Supplier<CreativeModeTab> FLUID_LOGISTICS_CREATIVE_TAB =
            CREATIVE_TABS.register("fluidlogistics_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.fluidlogistics.fluidlogistics_tab"))
                    .icon(() -> createWaterFluidPackage(50000))
                    .build());

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .setTooltipModifierFactory(item -> {
                TooltipModifier base = item instanceof InfiniteFluidTankItem
                        ? new InfiniteFluidTankItem.TooltipModifier(item, FontHelper.Palette.STANDARD_CREATE)
                        : item instanceof CompressedTankItem
                            ? new CompressedTankTooltipModifier(item, FontHelper.Palette.STANDARD_CREATE)
                            : new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE);
                return base.andThen(TooltipModifier.mapNull(KineticStats.create(item)));
            })
            .defaultCreativeTab(FLUID_LOGISTICS_TAB);

    public FluidLogistics(IEventBus modEventBus, ModContainer modContainer) {
        CREATIVE_TABS.register(modEventBus);
        REGISTRATE.registerEventListeners(modEventBus);

        AllConditionCodecs.register(modEventBus);
        AllDataComponents.register(modEventBus);
        AllFluidAttributeTypes.REGISTER.register(modEventBus);
        AllFluidLogisticsParticleTypes.register(modEventBus);
        AllBlocks.register();
        AllBlockEntities.register();
        AllItems.register();
        FluidLogisticsArmInteractionPointTypes.ARM_INTERACTION_POINT_TYPES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::hideDisabledItems);

        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("FluidLogistics initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FluidLogisticsPackets.register();
            ArmInteractionPointType.init();
            com.yision.fluidlogistics.registry.AllMountedStorageTypes.register();
            FluidLogisticsUnpackingHandlers.registerDefaults();
            BlockStressValues.IMPACTS.register(AllBlocks.FLUID_PUMP.get(), () -> 8.0);
            BlockStressValues.IMPACTS.register(AllBlocks.MECHANICAL_FLUID_GUN.get(), () -> 2.0);
            LOGGER.info("FluidLogistics mounted storage registered!");
        });
    }

    private void registerCapabilities(final RegisterCapabilitiesEvent event) {
        FluidTransporterBlockEntity.registerCapabilities(event);
        FluidPackagerBlockEntity.registerCapabilities(event);
        FluidRepackagerBlockEntity.registerCapabilities(event);
        MultiFluidTankBlockEntity.registerCapabilities(event);
        HorizontalMultiFluidTankBlockEntity.registerCapabilities(event);
        MultiFluidAccessPortBlockEntity.registerCapabilities(event);
        SmartHopperBlockEntity.registerCapabilities(event);
        InfiniteFluidTankBlockEntity.registerCapabilities(event);
        CopperBasinBlockEntity.registerCapabilities(event);
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> {
                    if (!FeatureToggle.isEnabled(FeatureToggle.WATER_CONTAINING_COPPER_CASING)) {
                        return null;
                    }
                    return WaterContainingCopperCasingFluidHandler.INSTANCE;
                },
                AllBlocks.WATER_CONTAINING_COPPER_CASING.get());
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> {
                    if (!Config.isAdvancedLogisticsNetworkEnabled()) {
                        return null;
                    }
                    return new CompressedTankFluidHandler(stack);
                },
                AllItems.COMPRESSED_STORAGE_TANK.get());

    }

    private void hideDisabledItems(final BuildCreativeModeTabContentsEvent event) {
        if (!Objects.equals(event.getTabKey(), FLUID_LOGISTICS_TAB) && !Objects.equals(event.getTabKey(), CreativeModeTabs.SEARCH)) {
            return;
        }

        for (FeatureItem fi : FEATURE_ITEMS) {
            boolean shouldHide;
            if (fi.feature == FeatureToggle.FLUID_HATCH) {
                shouldHide = !FeatureToggle.isFluidHatchAdvertised();
            } else {
                shouldHide = !FeatureToggle.isEnabled(fi.feature);
            }
            if (shouldHide) {
                ItemStack hiddenItem = event.getSearchEntries().stream()
                        .filter(stack -> stack.getItem() == fi.item.get().asItem())
                        .findFirst()
                        .orElseGet(() -> event.getParentEntries().stream()
                                .filter(stack -> stack.getItem() == fi.item.get().asItem())
                                .findFirst()
                                .orElse(ItemStack.EMPTY));

                if (!hiddenItem.isEmpty()) {
                    event.remove(hiddenItem, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                }
            }
        }
    }

    record FeatureItem(ResourceLocation feature, Supplier<? extends ItemLike> item) {}

    private static final FeatureItem[] FEATURE_ITEMS = {
            new FeatureItem(FeatureToggle.FLUID_TRANSPORTER, AllBlocks.FLUID_TRANSPORTER),
            new FeatureItem(FeatureToggle.SMART_FAUCET, AllBlocks.SMART_FAUCET),
            new FeatureItem(FeatureToggle.FAUCET, AllBlocks.FAUCET),
            new FeatureItem(FeatureToggle.MULTI_FLUID_TANK, AllBlocks.MULTI_FLUID_TANK),
            new FeatureItem(FeatureToggle.HORIZONTAL_MULTI_FLUID_TANK, AllBlocks.HORIZONTAL_MULTI_FLUID_TANK),
            new FeatureItem(FeatureToggle.MULTI_FLUID_ACCESS_PORT, AllBlocks.MULTI_FLUID_ACCESS_PORT),
            new FeatureItem(FeatureToggle.SMART_HOPPER, AllBlocks.SMART_HOPPER),
            new FeatureItem(FeatureToggle.FLUID_PUMP, AllBlocks.FLUID_PUMP),
            new FeatureItem(FeatureToggle.INFINITE_FLUID_TANK, AllBlocks.INFINITE_FLUID_TANK),
            new FeatureItem(FeatureToggle.WATER_CONTAINING_COPPER_CASING, AllBlocks.WATER_CONTAINING_COPPER_CASING),
            new FeatureItem(FeatureToggle.COPPER_BASIN, AllBlocks.COPPER_BASIN),
            new FeatureItem(FeatureToggle.MECHANICAL_FLUID_GUN, AllBlocks.MECHANICAL_FLUID_GUN),
            new FeatureItem(FeatureToggle.HAND_POINTER, AllItems.HAND_POINTER),
            new FeatureItem(FeatureToggle.FLUID_PACKAGER, AllBlocks.FLUID_PACKAGER),
            new FeatureItem(FeatureToggle.FLUID_REPACKAGER, AllBlocks.FLUID_REPACKAGER),
            new FeatureItem(FeatureToggle.COMPRESSED_STORAGE_TANK, AllItems.COMPRESSED_STORAGE_TANK),
            new FeatureItem(FeatureToggle.RARE_FLUID_PACKAGE, AllItems.RARE_FLUID_PACKAGE),
            new FeatureItem(FeatureToggle.RARE_FLUID_PACKAGE, AllItems.FLUID_PACKAGE_2),
            new FeatureItem(FeatureToggle.FLUID_HATCH, AllBlocks.FLUID_HATCH),
    };

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("FluidLogistics server starting!");
    }

    private static ItemStack createWaterFluidPackage(int amount) {
        ItemStack packageStack = new ItemStack(AllItems.RARE_FLUID_PACKAGE.get());
        ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
        ItemStack tankStack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(tankStack, new FluidStack(Fluids.WATER, amount));
        contents.setStackInSlot(0, tankStack);
        packageStack.set(com.simibubi.create.AllDataComponents.PACKAGE_CONTENTS,
                com.simibubi.create.foundation.item.ItemHelper.containerContentsFromHandler(contents));
        return packageStack;
    }
}
