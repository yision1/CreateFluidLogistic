package com.yision.fluidlogistics.registry;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.jetbrains.annotations.ApiStatus.Internal;

import com.mojang.serialization.Codec;
import com.yision.fluidlogistics.datacomponent.FluidTankContent;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllDataComponents {

    private static final DeferredRegister.DataComponents DATA_COMPONENTS = 
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "fluidlogistics");

    public static final DataComponentType<FluidTankContent> FLUID_TANK_CONTENT = register(
            "fluid_tank_content",
            builder -> builder.persistent(FluidTankContent.CODEC)
                    .networkSynchronized(FluidTankContent.STREAM_CODEC)
    );

    public static final DataComponentType<CustomData> PORTABLE_STOCK_TICKER_FREQ = register(
            "portable_stock_ticker_freq",
            builder -> builder.persistent(CustomData.CODEC)
                    .networkSynchronized(CustomData.STREAM_CODEC)
    );

    public static final DataComponentType<String> PORTABLE_STOCK_TICKER_ADDRESS = register(
            "portable_stock_ticker_address",
            builder -> builder.persistent(Codec.STRING)
    );

    public static final DataComponentType<List<ItemStack>> PORTABLE_STOCK_TICKER_CATEGORIES = register(
            "portable_stock_ticker_categories",
            builder -> builder.persistent(ItemStack.CODEC.listOf())
    );

    public static final DataComponentType<Map<UUID, List<Integer>>> PORTABLE_STOCK_TICKER_HIDDEN_CATEGORIES = register(
            "portable_stock_ticker_hidden_categories",
            builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT.listOf()))
    );

    private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        DataComponentType<T> type = builder.apply(DataComponentType.builder()).build();
        DATA_COMPONENTS.register(name, () -> type);
        return type;
    }

    @Internal
    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
