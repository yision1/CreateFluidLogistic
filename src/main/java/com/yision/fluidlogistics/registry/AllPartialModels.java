package com.yision.fluidlogistics.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.yision.fluidlogistics.FluidLogistics;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class AllPartialModels {

    public static final PartialModel FLUID_PACKAGER_TRAY = block("fluid_packager/tray");
    public static final PartialModel FLUID_PACKAGER_HATCH_OPEN = block("fluid_packager/hatch_open");
    public static final PartialModel FLUID_PACKAGER_HATCH_CLOSED = block("fluid_packager/hatch_closed");
    public static final Map<Direction, PartialModel> SMART_FAUCET_SOURCE_INTERFACE = new EnumMap<>(Direction.class);

    public static final PartialModel FLUID_PACKAGE = item("rare_fluid_package");
    public static final PartialModel FLUID_PACKAGE_2 = item("rare_fluid_package_1");
    public static final PartialModel FLUID_PACKAGE_RIGGING = rigging("12x10");

    private static final ResourceLocation FLUID_PACKAGE_ID = ResourceLocation.fromNamespaceAndPath(FluidLogistics.MODID, "rare_fluid_package");
    private static final ResourceLocation FLUID_PACKAGE_2_ID = ResourceLocation.fromNamespaceAndPath(FluidLogistics.MODID, "rare_fluid_package_1");

    private static boolean registered = false;

    static {
        SMART_FAUCET_SOURCE_INTERFACE.put(Direction.NORTH, block("smart_faucet/source_interface/north"));
        SMART_FAUCET_SOURCE_INTERFACE.put(Direction.SOUTH, block("smart_faucet/source_interface/south"));
        SMART_FAUCET_SOURCE_INTERFACE.put(Direction.EAST, block("smart_faucet/source_interface/east"));
        SMART_FAUCET_SOURCE_INTERFACE.put(Direction.WEST, block("smart_faucet/source_interface/west"));
    }

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(FluidLogistics.MODID, "block/" + path));
    }

    private static PartialModel item(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(FluidLogistics.MODID, "item/" + path));
    }

    private static PartialModel rigging(String size) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath("create", "item/package/rigging_" + size));
    }

    public static List<ResourceLocation> customModelLocations() {
        return List.of(
                FLUID_PACKAGER_TRAY.modelLocation(),
                FLUID_PACKAGER_HATCH_OPEN.modelLocation(),
                FLUID_PACKAGER_HATCH_CLOSED.modelLocation(),
                FLUID_PACKAGE.modelLocation(),
                FLUID_PACKAGE_2.modelLocation(),
                SMART_FAUCET_SOURCE_INTERFACE.get(Direction.NORTH).modelLocation(),
                SMART_FAUCET_SOURCE_INTERFACE.get(Direction.SOUTH).modelLocation(),
                SMART_FAUCET_SOURCE_INTERFACE.get(Direction.EAST).modelLocation(),
                SMART_FAUCET_SOURCE_INTERFACE.get(Direction.WEST).modelLocation()
        );
    }

    public static void register() {
        if (registered) return;
        registered = true;

        com.simibubi.create.AllPartialModels.PACKAGES.put(FLUID_PACKAGE_ID, FLUID_PACKAGE);
        com.simibubi.create.AllPartialModels.PACKAGE_RIGGING.put(FLUID_PACKAGE_ID, FLUID_PACKAGE_RIGGING);
        com.simibubi.create.AllPartialModels.PACKAGES.put(FLUID_PACKAGE_2_ID, FLUID_PACKAGE_2);
        com.simibubi.create.AllPartialModels.PACKAGE_RIGGING.put(FLUID_PACKAGE_2_ID, FLUID_PACKAGE_RIGGING);
    }

    public static PartialModel getFluidPackageModel(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        PartialModel model = com.simibubi.create.AllPartialModels.PACKAGES.get(id);
        return model != null ? model : FLUID_PACKAGE;
    }
}
