package com.yision.fluidlogistics;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlockEntity;
import com.yision.fluidlogistics.block.FluidTransporter.FluidTransporterBlockEntity;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.block.MultiFluidAccessPort.MultiFluidAccessPortBlockEntity;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.advancement.AllTriggers;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.registry.FluidLogisticsArmInteractionPointTypes;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.registry.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.item.CompressedTankFluidHandler;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.item.CompressedTankTooltipModifier;
import com.yision.fluidlogistics.registry.AllDataComponents;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.registry.AllMenuTypes;
import com.yision.fluidlogistics.registry.AllConditionCodecs;
import com.yision.fluidlogistics.config.FeatureToggle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Objects;
import org.slf4j.Logger;

@Mod(FluidLogistics.MODID)
public class FluidLogistics {
    public static final String MODID = "fluidlogistics";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<net.minecraft.world.item.CreativeModeTab> FLUID_LOGISTICS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, asResource("fluidlogistics_tab"));

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .setTooltipModifierFactory(item ->
                    item instanceof CompressedTankItem
                            ? new CompressedTankTooltipModifier(item, FontHelper.Palette.STANDARD_CREATE)
                            : new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
            )
            .defaultCreativeTab("fluidlogistics_tab", b -> b.icon(() -> createWaterFluidPackage(50000)))
            .build();

    public FluidLogistics(IEventBus modEventBus, ModContainer modContainer) {
        REGISTRATE.registerEventListeners(modEventBus);

        AllConditionCodecs.register(modEventBus);
        AllDataComponents.register(modEventBus);
        AllBlocks.register();
        AllBlockEntities.register();
        AllItems.register();
        AllMenuTypes.register();
        FluidLogisticsArmInteractionPointTypes.ARM_INTERACTION_POINT_TYPES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(this::hideDisabledItems);

        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("FluidLogistics initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FluidLogisticsPackets.register();
            ArmInteractionPointType.init();
            com.yision.fluidlogistics.registry.AllMountedStorageTypes.register();
            LOGGER.info("FluidLogistics mounted storage registered!");
        });
    }

    private void onRegister(final RegisterEvent event) {
        if (event.getRegistry() == BuiltInRegistries.TRIGGER_TYPES) {
            AllTriggers.register();
        }
    }

    private void registerCapabilities(final RegisterCapabilitiesEvent event) {
        FluidTransporterBlockEntity.registerCapabilities(event);
        FluidPackagerBlockEntity.registerCapabilities(event);
        MultiFluidTankBlockEntity.registerCapabilities(event);
        HorizontalMultiFluidTankBlockEntity.registerCapabilities(event);
        MultiFluidAccessPortBlockEntity.registerCapabilities(event);
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new CompressedTankFluidHandler(stack),
                AllItems.COMPRESSED_STORAGE_TANK.get());
    }

    private void hideDisabledItems(final BuildCreativeModeTabContentsEvent event) {
        if (!Objects.equals(event.getTabKey(), FLUID_LOGISTICS_TAB) && !Objects.equals(event.getTabKey(), CreativeModeTabs.SEARCH)) {
            return;
        }

        if (!FeatureToggle.isEnabled(FeatureToggle.FLUID_TRANSPORTER)) {
            ItemStack hiddenItem = event.getSearchEntries().stream()
                    .filter(stack -> stack.getItem() == AllBlocks.FLUID_TRANSPORTER.asItem())
                    .findFirst()
                    .orElseGet(() -> event.getParentEntries().stream()
                            .filter(stack -> stack.getItem() == AllBlocks.FLUID_TRANSPORTER.asItem())
                            .findFirst()
                            .orElse(ItemStack.EMPTY));

            if (!hiddenItem.isEmpty()) {
                event.remove(hiddenItem, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        }

        if (!ModList.get().isLoaded("create_mobile_packages") && !ModList.get().isLoaded("cmpackagecouriers")) {
            ItemStack tickerItem = event.getSearchEntries().stream()
                    .filter(stack -> stack.getItem() == AllItems.PORTABLE_STOCK_TICKER.asItem())
                    .findFirst()
                    .orElseGet(() -> event.getParentEntries().stream()
                            .filter(stack -> stack.getItem() == AllItems.PORTABLE_STOCK_TICKER.asItem())
                            .findFirst()
                            .orElse(ItemStack.EMPTY));

            if (!tickerItem.isEmpty()) {
                event.remove(tickerItem, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        }
    }

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
