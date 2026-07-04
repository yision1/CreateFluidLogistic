package com.yision.fluidlogistics.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = "fluidlogistics", bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

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

    private static final int FLUID_PACKAGE_CAPACITY_DEFAULT = 10000;
    private static final int FLUID_PACKAGE_CAPACITY_MIN = 1;
    private static final int FLUID_PACKAGE_CAPACITY_MAX = Integer.MAX_VALUE;
    private static final int FLUID_PUMP_RANGE_DEFAULT = 24;
    private static final int FLUID_PUMP_RANGE_MIN = 1;
    private static final int FLUID_PUMP_RANGE_MAX = Integer.MAX_VALUE;

    private static final boolean FLUID_TRANSPORTER_INFINITE_WATER_ENABLED_DEFAULT = true;
    private static final boolean FAUCET_INFINITE_WATER_ENABLED_DEFAULT = true;
    private static final boolean SMART_HOPPER_INFINITE_WATER_ENABLED_DEFAULT = true;
    private static final int MILLIBUCKETS_PER_BUCKET = 1000;
    private static final int INFINITE_FLUID_TANK_CAPACITY_DEFAULT = -1;
    private static final int INFINITE_FLUID_TANK_CAPACITY_MIN = -1;
    private static final int INFINITE_FLUID_TANK_CAPACITY_MAX = Integer.MAX_VALUE / MILLIBUCKETS_PER_BUCKET;

    static {
        BUILDER.translation("fluidlogistics.configuration.section.featureToggles")
                .push("featureToggles");
    }

    public static final ModConfigSpec.BooleanValue FLUID_TRANSPORTER_ENABLED = BUILDER
            .comment("Enables fluid transporter")
            .translation("block.fluidlogistics.fluid_transporter")
            .define("fluidTransporterEnabled", FLUID_TRANSPORTER_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue SMART_FAUCET_ENABLED = BUILDER
            .comment("Enables smart faucet")
            .translation("block.fluidlogistics.smart_faucet")
            .define("smartFaucetEnabled", SMART_FAUCET_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue FAUCET_ENABLED = BUILDER
            .comment("Enables faucet")
            .translation("block.fluidlogistics.faucet")
            .define("faucetEnabled", FAUCET_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue MULTI_FLUID_TANK_ENABLED = BUILDER
            .comment("Enables multi-fluid tank")
            .translation("block.fluidlogistics.multi_fluid_tank")
            .define("multiFluidTankEnabled", MULTI_FLUID_TANK_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue HORIZONTAL_MULTI_FLUID_TANK_ENABLED = BUILDER
            .comment("Enables horizontal multi-fluid tank")
            .translation("block.fluidlogistics.horizontal_multi_fluid_tank")
            .define("horizontalMultiFluidTankEnabled", HORIZONTAL_MULTI_FLUID_TANK_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue MULTI_FLUID_ACCESS_PORT_ENABLED = BUILDER
            .comment("Enables multi-fluid access port")
            .translation("block.fluidlogistics.multi_fluid_access_port")
            .define("multiFluidAccessPortEnabled", MULTI_FLUID_ACCESS_PORT_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue SMART_HOPPER_ENABLED = BUILDER
            .comment("Enables smart hopper")
            .translation("block.fluidlogistics.smart_hopper")
            .define("smartHopperEnabled", SMART_HOPPER_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue FLUID_PUMP_ENABLED = BUILDER
            .comment("Enables fluid pump")
            .translation("block.fluidlogistics.fluid_pump")
            .define("fluidPumpEnabled", FLUID_PUMP_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue INFINITE_FLUID_TANK_ENABLED = BUILDER
            .comment("Enables infinite fluid tank")
            .translation("block.fluidlogistics.infinite_fluid_tank")
            .define("infiniteFluidTankEnabled", INFINITE_FLUID_TANK_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue WATER_CONTAINING_COPPER_CASING_ENABLED = BUILDER
            .comment("Enables water containing copper casing")
            .translation("block.fluidlogistics.water_containing_copper_casing")
            .define("waterContainingCopperCasingEnabled", WATER_CONTAINING_COPPER_CASING_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue COPPER_BASIN_ENABLED = BUILDER
            .comment("Enables copper basin")
            .translation("block.fluidlogistics.copper_basin")
            .define("copperBasinEnabled", COPPER_BASIN_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue MECHANICAL_FLUID_GUN_ENABLED = BUILDER
            .comment("Enables mechanical fluid gun")
            .translation("block.fluidlogistics.mechanical_fluid_gun")
            .define("mechanicalFluidGunEnabled", MECHANICAL_FLUID_GUN_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue HAND_POINTER_ENABLED = BUILDER
            .comment("Enables hand pointer")
            .translation("item.fluidlogistics.hand_pointer")
            .define("handPointerEnabled", HAND_POINTER_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue ADVANCED_LOGISTICS_NETWORK_ENABLED = BUILDER
            .comment("After disabling, packages cannot be used to transport fluids")
            .translation("fluidlogistics.configuration.advancedLogisticsNetworkEnabled")
            .define("advancedLogisticsNetworkEnabled", ADVANCED_LOGISTICS_NETWORK_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue FLUID_HATCH_ENABLED = BUILDER
            .comment("Enables fluid hatch")
            .translation("block.fluidlogistics.fluid_hatch")
            .define("fluidHatchEnabled", FLUID_HATCH_ENABLED_DEFAULT);

    static {
        BUILDER.pop();
        BUILDER.translation("fluidlogistics.configuration.section.blockProperties")
                .push("blockProperties");
    }

    public static final ModConfigSpec.IntValue FLUID_PACKAGE_CAPACITY = BUILDER
            .comment("Maximum fluid amount a fluid package can hold (in mB)")
            .translation("fluidlogistics.configuration.fluidPackageCapacity")
            .defineInRange("fluidPackageCapacity",
                    FLUID_PACKAGE_CAPACITY_DEFAULT,
                    FLUID_PACKAGE_CAPACITY_MIN,
                    FLUID_PACKAGE_CAPACITY_MAX);

    public static final ModConfigSpec.IntValue FLUID_PUMP_RANGE = BUILDER
            .comment("Maximum distance a Fluid Pump can push or pull fluids on either side")
            .translation("fluidlogistics.configuration.fluidPumpRange")
            .defineInRange("fluidPumpRange",
                    FLUID_PUMP_RANGE_DEFAULT,
                    FLUID_PUMP_RANGE_MIN,
                    FLUID_PUMP_RANGE_MAX);

    public static final ModConfigSpec.BooleanValue FLUID_TRANSPORTER_INFINITE_WATER_ENABLED = BUILDER
            .comment("Allow the fluid transporter to extract infinite water from waterlogged leaves")
            .translation("fluidlogistics.configuration.fluidTransporterInfiniteWater")
            .define("fluidTransporterInfiniteWaterEnabled", FLUID_TRANSPORTER_INFINITE_WATER_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue FAUCET_INFINITE_WATER_ENABLED = BUILDER
            .comment("Allow the faucet and smart faucet to extract infinite water from waterlogged leaves")
            .translation("fluidlogistics.configuration.faucetInfiniteWater")
            .define("faucetInfiniteWaterEnabled", FAUCET_INFINITE_WATER_ENABLED_DEFAULT);

    public static final ModConfigSpec.BooleanValue SMART_HOPPER_INFINITE_WATER_ENABLED = BUILDER
            .comment("Allow the smart hopper to extract infinite water from waterlogged leaves")
            .translation("fluidlogistics.configuration.smartHopperInfiniteWater")
            .define("smartHopperInfiniteWaterEnabled", SMART_HOPPER_INFINITE_WATER_ENABLED_DEFAULT);

    public static final ModConfigSpec.IntValue INFINITE_FLUID_TANK_CAPACITY = BUILDER
            .comment("Infinite tank capacity (in B/buckets). -1 = follow Create's infinite fluid capacity config option")
            .translation("fluidlogistics.configuration.infiniteFluidTankCapacity")
            .defineInRange("infiniteFluidTankCapacity",
                    INFINITE_FLUID_TANK_CAPACITY_DEFAULT,
                    INFINITE_FLUID_TANK_CAPACITY_MIN,
                    INFINITE_FLUID_TANK_CAPACITY_MAX);

    public static final ModConfigSpec.EnumValue<InfiniteTankFluidMode> INFINITE_FLUID_TANK_ALLOWED_FLUIDS = BUILDER
            .comment("FOLLOW_CREATE = follow Create's bottomless fluid config option")
            .translation("fluidlogistics.configuration.infiniteFluidTankAllowedFluids")
            .defineEnum("infiniteFluidTankAllowedFluids", InfiniteTankFluidMode.FOLLOW_CREATE);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

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
    private static boolean advancedLogisticsNetworkEnabled = ADVANCED_LOGISTICS_NETWORK_ENABLED_DEFAULT;
    private static boolean fluidHatchEnabled = FLUID_HATCH_ENABLED_DEFAULT;
    private static int fluidPackageCapacity = FLUID_PACKAGE_CAPACITY_DEFAULT;
    private static int fluidPumpRange = FLUID_PUMP_RANGE_DEFAULT;
    private static boolean fluidTransporterInfiniteWaterEnabled = FLUID_TRANSPORTER_INFINITE_WATER_ENABLED_DEFAULT;
    private static boolean faucetInfiniteWaterEnabled = FAUCET_INFINITE_WATER_ENABLED_DEFAULT;
    private static boolean smartHopperInfiniteWaterEnabled = SMART_HOPPER_INFINITE_WATER_ENABLED_DEFAULT;
    private static int infiniteFluidTankCapacity = INFINITE_FLUID_TANK_CAPACITY_DEFAULT;
    private static InfiniteTankFluidMode infiniteFluidTankAllowedFluids = InfiniteTankFluidMode.FOLLOW_CREATE;

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
        advancedLogisticsNetworkEnabled = ADVANCED_LOGISTICS_NETWORK_ENABLED.get();
        fluidHatchEnabled = FLUID_HATCH_ENABLED.get();
        fluidPackageCapacity = FLUID_PACKAGE_CAPACITY.get();
        fluidPumpRange = FLUID_PUMP_RANGE.get();
        fluidTransporterInfiniteWaterEnabled = FLUID_TRANSPORTER_INFINITE_WATER_ENABLED.get();
        faucetInfiniteWaterEnabled = FAUCET_INFINITE_WATER_ENABLED.get();
        smartHopperInfiniteWaterEnabled = SMART_HOPPER_INFINITE_WATER_ENABLED.get();
        infiniteFluidTankCapacity = bucketsToMillibuckets(INFINITE_FLUID_TANK_CAPACITY.get());
        infiniteFluidTankAllowedFluids = INFINITE_FLUID_TANK_ALLOWED_FLUIDS.get();
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
    public static boolean isAdvancedLogisticsNetworkEnabled() { return advancedLogisticsNetworkEnabled; }
    public static boolean isFluidHatchEnabled() { return fluidHatchEnabled; }

    public static int getFluidPumpRange() { return fluidPumpRange; }

    public static int getFluidPerPackage() {
        return Math.max(FLUID_PACKAGE_CAPACITY_MIN, fluidPackageCapacity);
    }

    public static boolean isFluidTransporterInfiniteWaterEnabled() { return fluidTransporterInfiniteWaterEnabled; }
    public static boolean isFaucetInfiniteWaterEnabled() { return faucetInfiniteWaterEnabled; }
    public static boolean isSmartHopperInfiniteWaterEnabled() { return smartHopperInfiniteWaterEnabled; }
    public static int getInfiniteFluidTankCapacity() { return infiniteFluidTankCapacity; }
    public static InfiniteTankFluidMode getInfiniteFluidTankAllowedFluids() { return infiniteFluidTankAllowedFluids; }

    private static int bucketsToMillibuckets(int buckets) {
        if (buckets <= 0) {
            return buckets;
        }
        return buckets * MILLIBUCKETS_PER_BUCKET;
    }
}
