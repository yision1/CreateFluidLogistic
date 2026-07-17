package com.yision.fluidlogistics.api.packager;

import org.jetbrains.annotations.ApiStatus;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageResourceType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class PackageResourceTypes {
    public static final ResourceLocation FLUID = FluidLogistics.asResource("fluid");

    private PackageResourceTypes() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static PackageResourceType fluid() {
        return FluidPackageResourceType.fluid();
    }

    public static ItemStack createFluidKey(FluidStack fluid) {
        return FluidPackageResourceType.createFluidKey(fluid);
    }

    public static FluidStack getFluid(ItemStack carrierOrKey) {
        return FluidPackageResourceType.getFluid(carrierOrKey);
    }

    public static int getFluidPerPackage() {
        return FluidPackageResourceType.getFluidPerPackage();
    }

    @ApiStatus.Internal
    public static void registerBuiltIns() {
        FluidPackageResourceType.registerBuiltIns();
    }
}
