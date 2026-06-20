package com.yision.fluidlogistics.config;

import com.simibubi.create.content.logistics.box.PackageItem;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = "fluidlogistics", bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final boolean FLUID_TRANSPORTER_ENABLED_DEFAULT = true;
    private static final boolean SMART_FAUCET_ENABLED_DEFAULT = true;
    private static final boolean FAUCET_ENABLED_DEFAULT = true;
    private static final boolean MULTI_FLUID_TANK_ENABLED_DEFAULT = true;
    private static final boolean HORIZONTAL_MULTI_FLUID_TANK_ENABLED_DEFAULT = true;
    private static final boolean MULTI_FLUID_ACCESS_PORT_ENABLED_DEFAULT = true;
    private static final boolean SMART_HOPPER_ENABLED_DEFAULT = true;
    private static final boolean FLUID_PUMP_ENABLED_DEFAULT = true;
    private static final boolean INFINITE_FLUID_TANK_ENABLED_DEFAULT = true;
    private static final boolean WATER_CONTAINING_COPPER_CASING_ENABLED_DEFAULT = true;
    private static final boolean COPPER_BASIN_ENABLED_DEFAULT = true;
    private static final boolean MECHANICAL_FLUID_GUN_ENABLED_DEFAULT = true;
    private static final boolean HAND_POINTER_ENABLED_DEFAULT = true;
    private static final boolean ADVANCED_LOGISTICS_NETWORK_ENABLED_DEFAULT = true;
    private static final boolean FLUID_HATCH_ENABLED_DEFAULT = true;

    private static final int COMPRESSED_TANK_CAPACITY_DEFAULT = 10000;
    private static final int COMPRESSED_TANK_CAPACITY_MIN = 1000;
    private static final int COMPRESSED_TANK_CAPACITY_MAX = 10000;
    private static final int FLUID_PACKAGE_CAPACITY_DEFAULT = 10000;
    private static final int FLUID_PACKAGE_CAPACITY_MIN = 1;
    private static final int FLUID_PACKAGE_CAPACITY_MAX = COMPRESSED_TANK_CAPACITY_MAX * PackageItem.SLOTS;
    private static final int FLUID_PUMP_RANGE_DEFAULT = 24;
    private static final int FLUID_PUMP_RANGE_MIN = 1;
    private static final int FLUID_PUMP_RANGE_MAX = Integer.MAX_VALUE;

    public static final ForgeConfigSpec.BooleanValue FLUID_TRANSPORTER_ENABLED = BUILDER
            .comment("Enables fluid transporter")
            .translation("fluidlogistics.configuration.fluidTransporterEnabled")
            .define("fluidTransporterEnabled", FLUID_TRANSPORTER_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue SMART_FAUCET_ENABLED = BUILDER
            .comment("Enables smart faucet")
            .translation("fluidlogistics.configuration.smartFaucetEnabled")
            .define("smartFaucetEnabled", SMART_FAUCET_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue FAUCET_ENABLED = BUILDER
            .comment("Enables faucet")
            .translation("fluidlogistics.configuration.faucetEnabled")
            .define("faucetEnabled", FAUCET_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue MULTI_FLUID_TANK_ENABLED = BUILDER
            .comment("Enables multi-fluid tank")
            .translation("fluidlogistics.configuration.multiFluidTankEnabled")
            .define("multiFluidTankEnabled", MULTI_FLUID_TANK_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue HORIZONTAL_MULTI_FLUID_TANK_ENABLED = BUILDER
            .comment("Enables horizontal multi-fluid tank")
            .translation("fluidlogistics.configuration.horizontalMultiFluidTankEnabled")
            .define("horizontalMultiFluidTankEnabled", HORIZONTAL_MULTI_FLUID_TANK_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue MULTI_FLUID_ACCESS_PORT_ENABLED = BUILDER
            .comment("Enables multi-fluid access port")
            .translation("fluidlogistics.configuration.multiFluidAccessPortEnabled")
            .define("multiFluidAccessPortEnabled", MULTI_FLUID_ACCESS_PORT_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue SMART_HOPPER_ENABLED = BUILDER
            .comment("Enables smart hopper")
            .translation("fluidlogistics.configuration.smartHopperEnabled")
            .define("smartHopperEnabled", SMART_HOPPER_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue FLUID_PUMP_ENABLED = BUILDER
            .comment("Enables fluid pump")
            .translation("fluidlogistics.configuration.fluidPumpEnabled")
            .define("fluidPumpEnabled", FLUID_PUMP_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.IntValue FLUID_PUMP_RANGE = BUILDER
            .comment("The maximum distance a fluid pump can push or pull fluids on either side")
            .translation("fluidlogistics.configuration.fluidPumpRange")
            .defineInRange("fluidPumpRange",
                    FLUID_PUMP_RANGE_DEFAULT,
                    FLUID_PUMP_RANGE_MIN,
                    FLUID_PUMP_RANGE_MAX);

    public static final ForgeConfigSpec.BooleanValue INFINITE_FLUID_TANK_ENABLED = BUILDER
            .comment("Enables infinite fluid tank")
            .translation("fluidlogistics.configuration.infiniteFluidTankEnabled")
            .define("infiniteFluidTankEnabled", INFINITE_FLUID_TANK_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue WATER_CONTAINING_COPPER_CASING_ENABLED = BUILDER
            .comment("Enables water containing copper casing")
            .translation("fluidlogistics.configuration.waterContainingCopperCasingEnabled")
            .define("waterContainingCopperCasingEnabled", WATER_CONTAINING_COPPER_CASING_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue COPPER_BASIN_ENABLED = BUILDER
            .comment("Enables copper basin")
            .translation("fluidlogistics.configuration.copperBasinEnabled")
            .define("copperBasinEnabled", COPPER_BASIN_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue MECHANICAL_FLUID_GUN_ENABLED = BUILDER
            .comment("Enables mechanical fluid gun")
            .translation("fluidlogistics.configuration.mechanicalFluidGunEnabled")
            .define("mechanicalFluidGunEnabled", MECHANICAL_FLUID_GUN_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue HAND_POINTER_ENABLED = BUILDER
            .comment("Enables hand pointer")
            .translation("fluidlogistics.configuration.handPointerEnabled")
            .define("handPointerEnabled", HAND_POINTER_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue FLUID_HATCH_ENABLED = BUILDER
            .comment("Enables fluid hatch")
            .translation("fluidlogistics.configuration.fluidHatchEnabled")
            .define("fluidHatchEnabled", FLUID_HATCH_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.BooleanValue ADVANCED_LOGISTICS_NETWORK_ENABLED = BUILDER
            .comment("Enables advanced logistics network integration (fluid packager, fluid packages, compressed tank, and Create logistics mixins)")
            .translation("fluidlogistics.configuration.advancedLogisticsNetworkEnabled")
            .define("advancedLogisticsNetworkEnabled", ADVANCED_LOGISTICS_NETWORK_ENABLED_DEFAULT);

    public static final ForgeConfigSpec.IntValue COMPRESSED_TANK_CAPACITY = BUILDER
            .comment("The maximum amount of fluid (in mB) a compressed tank can hold")
            .translation("fluidlogistics.configuration.compressedTankCapacity")
            .defineInRange("compressedTankCapacity",
                    COMPRESSED_TANK_CAPACITY_DEFAULT,
                    COMPRESSED_TANK_CAPACITY_MIN,
                    COMPRESSED_TANK_CAPACITY_MAX);

    public static final ForgeConfigSpec.IntValue FLUID_PACKAGE_CAPACITY = BUILDER
            .comment("The maximum amount of fluid (in mB) a fluid package can hold",
                    "The effective maximum is limited by both 90000 mB and 9x the configured compressed tank capacity")
            .translation("fluidlogistics.configuration.fluidPackageCapacity")
            .defineInRange("fluidPackageCapacity",
                    FLUID_PACKAGE_CAPACITY_DEFAULT,
                    FLUID_PACKAGE_CAPACITY_MIN,
                    FLUID_PACKAGE_CAPACITY_MAX);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static boolean fluidTransporterEnabled = FLUID_TRANSPORTER_ENABLED_DEFAULT;
    private static boolean smartFaucetEnabled = SMART_FAUCET_ENABLED_DEFAULT;
    private static boolean faucetEnabled = FAUCET_ENABLED_DEFAULT;
    private static boolean multiFluidTankEnabled = MULTI_FLUID_TANK_ENABLED_DEFAULT;
    private static boolean horizontalMultiFluidTankEnabled = HORIZONTAL_MULTI_FLUID_TANK_ENABLED_DEFAULT;
    private static boolean multiFluidAccessPortEnabled = MULTI_FLUID_ACCESS_PORT_ENABLED_DEFAULT;
    private static boolean smartHopperEnabled = SMART_HOPPER_ENABLED_DEFAULT;
    private static boolean fluidPumpEnabled = FLUID_PUMP_ENABLED_DEFAULT;
    private static boolean infiniteFluidTankEnabled = INFINITE_FLUID_TANK_ENABLED_DEFAULT;
    private static boolean waterContainingCopperCasingEnabled = WATER_CONTAINING_COPPER_CASING_ENABLED_DEFAULT;
    private static boolean copperBasinEnabled = COPPER_BASIN_ENABLED_DEFAULT;
    private static boolean mechanicalFluidGunEnabled = MECHANICAL_FLUID_GUN_ENABLED_DEFAULT;
    private static boolean handPointerEnabled = HAND_POINTER_ENABLED_DEFAULT;
    private static boolean fluidHatchEnabled = FLUID_HATCH_ENABLED_DEFAULT;
    private static boolean advancedLogisticsNetworkEnabled = ADVANCED_LOGISTICS_NETWORK_ENABLED_DEFAULT;
    private static int compressedTankCapacity = COMPRESSED_TANK_CAPACITY_DEFAULT;
    private static int fluidPackageCapacity = FLUID_PACKAGE_CAPACITY_DEFAULT;
    private static int fluidPumpRange = FLUID_PUMP_RANGE_DEFAULT;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        fluidTransporterEnabled = FLUID_TRANSPORTER_ENABLED.get();
        smartFaucetEnabled = SMART_FAUCET_ENABLED.get();
        faucetEnabled = FAUCET_ENABLED.get();
        multiFluidTankEnabled = MULTI_FLUID_TANK_ENABLED.get();
        horizontalMultiFluidTankEnabled = HORIZONTAL_MULTI_FLUID_TANK_ENABLED.get();
        multiFluidAccessPortEnabled = MULTI_FLUID_ACCESS_PORT_ENABLED.get();
        smartHopperEnabled = SMART_HOPPER_ENABLED.get();
        fluidPumpEnabled = FLUID_PUMP_ENABLED.get();
        infiniteFluidTankEnabled = INFINITE_FLUID_TANK_ENABLED.get();
        waterContainingCopperCasingEnabled = WATER_CONTAINING_COPPER_CASING_ENABLED.get();
        copperBasinEnabled = COPPER_BASIN_ENABLED.get();
        mechanicalFluidGunEnabled = MECHANICAL_FLUID_GUN_ENABLED.get();
        handPointerEnabled = HAND_POINTER_ENABLED.get();
        fluidHatchEnabled = FLUID_HATCH_ENABLED.get();
        advancedLogisticsNetworkEnabled = ADVANCED_LOGISTICS_NETWORK_ENABLED.get();
        compressedTankCapacity = COMPRESSED_TANK_CAPACITY.get();
        fluidPackageCapacity = FLUID_PACKAGE_CAPACITY.get();
        fluidPumpRange = FLUID_PUMP_RANGE.get();
    }

    public static boolean isFluidTransporterEnabled() { return fluidTransporterEnabled; }
    public static boolean isSmartFaucetEnabled() { return smartFaucetEnabled; }
    public static boolean isFaucetEnabled() { return faucetEnabled; }
    public static boolean isMultiFluidTankEnabled() { return multiFluidTankEnabled; }
    public static boolean isHorizontalMultiFluidTankEnabled() { return horizontalMultiFluidTankEnabled; }
    public static boolean isMultiFluidAccessPortEnabled() { return multiFluidAccessPortEnabled; }
    public static boolean isSmartHopperEnabled() { return smartHopperEnabled; }
    public static boolean isFluidPumpEnabled() { return fluidPumpEnabled; }
    public static boolean isInfiniteFluidTankEnabled() { return infiniteFluidTankEnabled; }
    public static boolean isWaterContainingCopperCasingEnabled() { return waterContainingCopperCasingEnabled; }
    public static boolean isCopperBasinEnabled() { return copperBasinEnabled; }
    public static boolean isMechanicalFluidGunEnabled() { return mechanicalFluidGunEnabled; }
    public static boolean isHandPointerEnabled() { return handPointerEnabled; }
    public static boolean isFluidHatchEnabled() { return fluidHatchEnabled; }
    public static boolean isAdvancedLogisticsNetworkEnabled() { return advancedLogisticsNetworkEnabled; }

    public static int getCompressedTankCapacity() {
        return compressedTankCapacity;
    }

    public static int getFluidPumpRange() { return fluidPumpRange; }

    public static int getFluidPerPackage() {
        int maxFluidPerPackage = compressedTankCapacity * PackageItem.SLOTS;
        return Math.max(FLUID_PACKAGE_CAPACITY_MIN, Math.min(fluidPackageCapacity, maxFluidPerPackage));
    }
}
