package com.yision.fluidlogistics.config;

import com.yision.fluidlogistics.FluidLogistics;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class FeatureToggle {

    // --- Feature constants ---
    public static final ResourceLocation FLUID_TRANSPORTER = FluidLogistics.asResource("fluid_transporter");
    public static final ResourceLocation FLUID_TRANSPORTER_GET_WATER = FluidLogistics.asResource("fluid_transporter_get_water_from_leaves");
    public static final ResourceLocation SMART_FAUCET = FluidLogistics.asResource("smart_faucet");
    public static final ResourceLocation FAUCET = FluidLogistics.asResource("faucet");
    public static final ResourceLocation MULTI_FLUID_TANK = FluidLogistics.asResource("multi_fluid_tank");
    public static final ResourceLocation HORIZONTAL_MULTI_FLUID_TANK = FluidLogistics.asResource("horizontal_multi_fluid_tank");
    public static final ResourceLocation MULTI_FLUID_ACCESS_PORT = FluidLogistics.asResource("multi_fluid_access_port");
    public static final ResourceLocation SMART_HOPPER = FluidLogistics.asResource("smart_hopper");
    public static final ResourceLocation FLUID_PUMP = FluidLogistics.asResource("fluid_pump");
    public static final ResourceLocation INFINITE_FLUID_TANK = FluidLogistics.asResource("infinite_fluid_tank");
    public static final ResourceLocation WATER_CONTAINING_COPPER_CASING = FluidLogistics.asResource("water_containing_copper_casing");
    public static final ResourceLocation COPPER_BASIN = FluidLogistics.asResource("copper_basin");
    public static final ResourceLocation MECHANICAL_FLUID_GUN = FluidLogistics.asResource("mechanical_fluid_gun");
    public static final ResourceLocation HAND_POINTER = FluidLogistics.asResource("hand_pointer");
    public static final ResourceLocation FLUID_HATCH = FluidLogistics.asResource("fluid_hatch");
    public static final ResourceLocation ADVANCED_LOGISTICS_NETWORK = FluidLogistics.asResource("advanced_logistics_network");

    // Advanced-logistics-only features (no independent config, mapped to the master switch)
    public static final ResourceLocation FLUID_PACKAGER = FluidLogistics.asResource("fluid_packager");
    public static final ResourceLocation FLUID_REPACKAGER = FluidLogistics.asResource("fluid_repackager");
    public static final ResourceLocation COMPRESSED_STORAGE_TANK = FluidLogistics.asResource("compressed_storage_tank");
    public static final ResourceLocation RARE_FLUID_PACKAGE = FluidLogistics.asResource("rare_fluid_package");

    private static final Map<ResourceLocation, BooleanSupplier> FEATURE_MAP;

    static {
        Map<ResourceLocation, BooleanSupplier> map = new LinkedHashMap<>();
        map.put(FLUID_TRANSPORTER, Config::isFluidTransporterEnabled);
        map.put(FLUID_TRANSPORTER_GET_WATER,Config::isFluidTransporterGetWaterFromLeaves);
        map.put(SMART_FAUCET, Config::isSmartFaucetEnabled);
        map.put(FAUCET, Config::isFaucetEnabled);
        map.put(MULTI_FLUID_TANK, Config::isMultiFluidTankEnabled);
        map.put(HORIZONTAL_MULTI_FLUID_TANK, Config::isHorizontalMultiFluidTankEnabled);
        map.put(MULTI_FLUID_ACCESS_PORT, Config::isMultiFluidAccessPortEnabled);
        map.put(SMART_HOPPER, Config::isSmartHopperEnabled);
        map.put(FLUID_PUMP, Config::isFluidPumpEnabled);
        map.put(INFINITE_FLUID_TANK, Config::isInfiniteFluidTankEnabled);
        map.put(WATER_CONTAINING_COPPER_CASING, Config::isWaterContainingCopperCasingEnabled);
        map.put(COPPER_BASIN, Config::isCopperBasinEnabled);
        map.put(MECHANICAL_FLUID_GUN, Config::isMechanicalFluidGunEnabled);
        map.put(HAND_POINTER, Config::isHandPointerEnabled);
        map.put(FLUID_HATCH, Config::isFluidHatchEnabled);
        map.put(ADVANCED_LOGISTICS_NETWORK, Config::isAdvancedLogisticsNetworkEnabled);
        // Advanced-logistics-only features share the master switch
        map.put(FLUID_PACKAGER, Config::isAdvancedLogisticsNetworkEnabled);
        map.put(FLUID_REPACKAGER, Config::isAdvancedLogisticsNetworkEnabled);
        map.put(COMPRESSED_STORAGE_TANK, Config::isAdvancedLogisticsNetworkEnabled);
        map.put(RARE_FLUID_PACKAGE, Config::isAdvancedLogisticsNetworkEnabled);
        FEATURE_MAP = Collections.unmodifiableMap(map);
    }

    private FeatureToggle() {
    }

    public static boolean isEnabled(ResourceLocation feature) {
        BooleanSupplier supplier = FEATURE_MAP.get(feature);
        return supplier != null ? supplier.getAsBoolean() : true;
    }

    public static boolean isAdvancedLogisticsNetworkEnabled() {
        return Config.isAdvancedLogisticsNetworkEnabled();
    }
}
