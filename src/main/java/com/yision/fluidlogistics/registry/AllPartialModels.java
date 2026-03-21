package com.yision.fluidlogistics.registry;

import java.util.List;

import com.yision.fluidlogistics.FluidLogistics;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class AllPartialModels {

    public static final PartialModel FLUID_PACKAGER_TRAY = block("fluid_packager/tray");
    public static final PartialModel FLUID_PACKAGER_HATCH_OPEN = block("fluid_packager/hatch_open");
    public static final PartialModel FLUID_PACKAGER_HATCH_CLOSED = block("fluid_packager/hatch_closed");

    public static final PartialModel FLUID_PACKAGE = item("rare_fluid_package");
    public static final PartialModel FLUID_PACKAGE_RIGGING = rigging("12x10");

    private static final ResourceLocation FLUID_PACKAGE_ID = ResourceLocation.fromNamespaceAndPath(FluidLogistics.MODID, "rare_fluid_package");

    private static boolean registered = false;

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
                FLUID_PACKAGE.modelLocation()
        );
    }

    public static void register() {
        if (registered) return;
        registered = true;

        com.simibubi.create.AllPartialModels.PACKAGES.put(FLUID_PACKAGE_ID, FLUID_PACKAGE);
        com.simibubi.create.AllPartialModels.PACKAGE_RIGGING.put(FLUID_PACKAGE_ID, FLUID_PACKAGE_RIGGING);
    }
}
