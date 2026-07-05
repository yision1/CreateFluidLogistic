package com.yision.fluidlogistics.registry;

import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.foundation.data.AssetLookup;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.content.equipment.handPointer.HandPointerItem;
import com.yision.fluidlogistics.item.CompressedTankItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags.Items;
import net.neoforged.neoforge.registries.IRegistryExtension;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.concurrent.ThreadLocalRandom;

import static com.yision.fluidlogistics.FluidLogistics.REGISTRATE;

public class AllItems {

    public static final ItemEntry<CompressedTankItem> COMPRESSED_STORAGE_TANK = REGISTRATE
            .item("compressed_storage_tank", CompressedTankItem::new)
            .removeTab(ResourceKey.create(Registries.CREATIVE_MODE_TAB, FluidLogistics.asResource("fluidlogistics_tab")))
            .properties(p -> p.stacksTo(1))
            .model(AssetLookup.existingItemModel())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .register();

    public static final ItemEntry<FluidPackageItem> FLUID_PACKAGE = REGISTRATE
            .item("fluid_package", FluidPackageItem::new)
            .properties(p -> p.stacksTo(1))
            .tag(AllItemTags.PACKAGES.tag)
            .model(AssetLookup.existingItemModel())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .register();

    public static final ItemEntry<FluidPackageItem> FLUID_PACKAGE_EXPOSED = REGISTRATE
            .item("fluid_package_exposed", properties -> new FluidPackageItem(properties, FluidPackageItem.FLUID_EXPOSED_STYLE))
            .removeTab(ResourceKey.create(Registries.CREATIVE_MODE_TAB, FluidLogistics.asResource("fluidlogistics_tab")))
            .properties(p -> p.stacksTo(1))
            .tag(AllItemTags.PACKAGES.tag)
            .model(AssetLookup.existingItemModel())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .register();

    public static final ItemEntry<FluidPackageItem> FLUID_PACKAGE_OXIDIZED = REGISTRATE
            .item("fluid_package_oxidized", properties -> new FluidPackageItem(properties, FluidPackageItem.FLUID_OXIDIZED_STYLE))
            .removeTab(ResourceKey.create(Registries.CREATIVE_MODE_TAB, FluidLogistics.asResource("fluidlogistics_tab")))
            .properties(p -> p.stacksTo(1))
            .tag(AllItemTags.PACKAGES.tag)
            .model(AssetLookup.existingItemModel())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .register();

    public static final ItemEntry<FluidPackageItem> FLUID_PACKAGE_WEATHERED = REGISTRATE
            .item("fluid_package_weathered", properties -> new FluidPackageItem(properties, FluidPackageItem.FLUID_WEATHERED_STYLE))
            .removeTab(ResourceKey.create(Registries.CREATIVE_MODE_TAB, FluidLogistics.asResource("fluidlogistics_tab")))
            .properties(p -> p.stacksTo(1))
            .tag(AllItemTags.PACKAGES.tag)
            .model(AssetLookup.existingItemModel())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .register();

    public static final ItemEntry<HandPointerItem> HAND_POINTER = REGISTRATE
            .item("hand_pointer", HandPointerItem::new)
            .properties(p -> p.stacksTo(1))
            .tag(Items.TOOLS)
            .model(AssetLookup.existingItemModel())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .register();

    public static final ItemEntry<Item> PHANTOM_CHAIN = REGISTRATE
            .item("phantom_chain", Item::new)
            .model(AssetLookup.existingItemModel())
            .setData(ProviderType.LANG, NonNullBiConsumer.noop())
            .register();

    public static ItemStack createFluidPackage() {
        Item fluidPackage = switch (ThreadLocalRandom.current().nextInt(4)) {
            case 1 -> FLUID_PACKAGE_EXPOSED.get();
            case 2 -> FLUID_PACKAGE_OXIDIZED.get();
            case 3 -> FLUID_PACKAGE_WEATHERED.get();
            default -> FLUID_PACKAGE.get();
        };
        return new ItemStack(fluidPackage);
    }

    @SuppressWarnings("unchecked")
    public static void registerAliases(RegisterEvent event) {
        var registry = event.getRegistry(Registries.ITEM);
        if (registry == null) {
            return;
        }

        ((IRegistryExtension<Item>) registry).addAlias(
            FluidLogistics.asResource("rare_fluid_package"),
            FluidLogistics.asResource("fluid_package")
        );
        ((IRegistryExtension<Item>) registry).addAlias(
            FluidLogistics.asResource("rare_fluid_package_1"),
            FluidLogistics.asResource("fluid_package")
        );
    }

    public static void register() {
    }
}
