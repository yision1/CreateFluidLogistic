package com.yision.fluidlogistics;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlockEntity;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.config.FeatureEnabledCondition;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.item.CompressedTankTooltipModifier;
import com.yision.fluidlogistics.item.InfiniteFluidTankItem;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.registry.FluidLogisticsUnpackingHandlers;
import com.yision.fluidlogistics.registry.AllBlocks;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.registry.AllMenuTypes;
import com.yision.fluidlogistics.registry.AllFluidAttributeTypes;
import com.yision.fluidlogistics.registry.AllMountedStorageTypes;
import com.yision.fluidlogistics.registry.AllFluidLogisticsParticleTypes;
import com.yision.fluidlogistics.registry.AllPartialModels;
import com.yision.fluidlogistics.registry.FluidLogisticsArmInteractionPointTypes;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.RegisterEvent;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Mod(FluidLogistics.MODID)
public class FluidLogistics
{
    public static final String MODID = "fluidlogistics";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<CreativeModeTab> FLUID_LOGISTICS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, asResource("fluidlogistics_tab"));
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> FLUID_LOGISTICS_CREATIVE_TAB =
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

    public FluidLogistics(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        CREATIVE_TABS.register(modEventBus);
        REGISTRATE.registerEventListeners(modEventBus);

        AllBlocks.register();
        AllBlockEntities.register();
        AllItems.register();
        AllMenuTypes.register();
        AllMountedStorageTypes.register();
        AllFluidLogisticsParticleTypes.register(modEventBus);
        FluidLogisticsArmInteractionPointTypes.ARM_INTERACTION_POINT_TYPES.register(modEventBus);
        FluidLogisticsPackets.register();

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        CraftingHelper.register(FeatureEnabledCondition.Serializer.INSTANCE);

        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::hideDisabledItems);

        MinecraftForge.EVENT_BUS.register(this);
        
        LOGGER.info("FluidLogistics initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> {
            AllPartialModels.register();
            ArmInteractionPointType.init();
            FluidLogisticsUnpackingHandlers.registerDefaults();
            BlockStressValues.IMPACTS.register(AllBlocks.FLUID_PUMP.get(), () -> 8.0);
            BlockStressValues.IMPACTS.register(AllBlocks.MECHANICAL_FLUID_GUN.get(), () -> 2.0);
            LOGGER.info("FluidLogistics partial models registered!");
        });
    }

    private void onRegister(final RegisterEvent event) {
        AllFluidAttributeTypes.init();
    }

    private void registerCapabilities(final RegisterCapabilitiesEvent event)
    {
        FluidPackagerBlockEntity.registerCapabilities(event);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static ItemStack createWaterFluidPackage(int amount) {
        ItemStackHandler packageContents = new ItemStackHandler(PackageItem.SLOTS);
        int tankCapacity = CompressedTankItem.getCapacity();
        int tanksCreated = 0;
        FluidStack remainingFluid = new FluidStack(Fluids.WATER, amount);
        while (!remainingFluid.isEmpty() && tanksCreated < PackageItem.SLOTS) {
            int fluidForTank = Math.min(remainingFluid.getAmount(), tankCapacity);
            FluidStack tankFluid = FluidHelper.copyStackWithAmount(remainingFluid, fluidForTank);
            ItemStack compressedTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluid(compressedTank, tankFluid);
            ItemHandlerHelper.insertItemStacked(packageContents, compressedTank, false);
            remainingFluid.shrink(fluidForTank);
            tanksCreated++;
        }
        ItemStack fluidPackage = new ItemStack(AllItems.RARE_FLUID_PACKAGE.get());
        CompoundTag compound = new CompoundTag();
        compound.put("Items", packageContents.serializeNBT());
        fluidPackage.setTag(compound);
        return fluidPackage;
    }

    private void hideDisabledItems(final BuildCreativeModeTabContentsEvent event) {
        if (!Objects.equals(event.getTabKey(), FLUID_LOGISTICS_TAB)
                && !Objects.equals(event.getTabKey(), CreativeModeTabs.SEARCH)) {
            return;
        }

        for (FeatureItem fi : FEATURE_ITEMS) {
            if (!FeatureToggle.isEnabled(fi.feature)) {
                // Collect entries to remove first to avoid ConcurrentModificationException
                List<ItemStack> toRemove = new ArrayList<>();
                for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> entry : event.getEntries()) {
                    if (entry.getKey().getItem() == fi.item.get().asItem()) {
                        toRemove.add(entry.getKey());
                    }
                }
                for (ItemStack stack : toRemove) {
                    event.getEntries().remove(stack);
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
            new FeatureItem(FeatureToggle.FLUID_HATCH, AllBlocks.FLUID_HATCH),
            new FeatureItem(FeatureToggle.HAND_POINTER, AllItems.HAND_POINTER),
            // Advanced-logistics-only items (controlled by the master switch)
            new FeatureItem(FeatureToggle.FLUID_PACKAGER, AllBlocks.FLUID_PACKAGER),
            new FeatureItem(FeatureToggle.FLUID_REPACKAGER, AllBlocks.FLUID_REPACKAGER),
            new FeatureItem(FeatureToggle.COMPRESSED_STORAGE_TANK, AllItems.COMPRESSED_STORAGE_TANK),
            new FeatureItem(FeatureToggle.RARE_FLUID_PACKAGE, AllItems.RARE_FLUID_PACKAGE),
            new FeatureItem(FeatureToggle.RARE_FLUID_PACKAGE, AllItems.FLUID_PACKAGE_2),
    };

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("FluidLogistics server starting!");
    }
}
