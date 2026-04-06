package com.yision.fluidlogistics.registry;

import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.foundation.data.AssetLookup;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.item.FluidPackageItem;
import com.yision.fluidlogistics.item.HandPointerItem;
import com.yision.fluidlogistics.item.PortableStockTickerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import static com.yision.fluidlogistics.FluidLogistics.REGISTRATE;

public class AllItems {

    public static final ItemEntry<CompressedTankItem> COMPRESSED_STORAGE_TANK = REGISTRATE
            .item("compressed_storage_tank", CompressedTankItem::new)
            .removeTab(ResourceKey.create(Registries.CREATIVE_MODE_TAB, FluidLogistics.asResource("fluidlogistics_tab")))
            .properties(p -> p.stacksTo(1))
            .model(AssetLookup.existingItemModel())
            .register();

    public static final ItemEntry<FluidPackageItem> RARE_FLUID_PACKAGE = REGISTRATE
            .item("rare_fluid_package", FluidPackageItem::new)
            .properties(p -> p.stacksTo(1))
            .tag(AllItemTags.PACKAGES.tag)
            .model(AssetLookup.existingItemModel())
            .lang("Rare Fluid Package")
            .register();

    public static final ItemEntry<HandPointerItem> HAND_POINTER = REGISTRATE
            .item("hand_pointer", HandPointerItem::new)
            .properties(p -> p.stacksTo(1))
            .model(AssetLookup.existingItemModel())
            .lang("Hand Pointer")
            .register();

    public static final ItemEntry<PortableStockTickerItem> PORTABLE_STOCK_TICKER = REGISTRATE
            .item("portable_stock_ticker", PortableStockTickerItem::new)
            .properties(p -> p.stacksTo(1))
            .model(AssetLookup.existingItemModel())
            .lang("Portable Stock Ticker")
            .register();

    public static void register() {
    }
}
