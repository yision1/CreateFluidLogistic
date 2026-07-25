package com.yision.fluidlogistics.api.packager;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@ApiStatus.Experimental
public interface PackageResourceType {
    enum SawAction {
        DEFAULT,
        DESTROY_WITHOUT_DROPS
    }

    enum DropAction {
        PASS,
        CONSUME_CARRIERS
    }

    ResourceLocation id();

    Supplier<? extends Item> carrierItem();

    boolean isValidCarrier(ItemStack stack);

    ItemStack normalizeKey(ItemStack stack);

    boolean matches(ItemStack firstNormalizedKey, ItemStack secondNormalizedKey);

    int amountOf(ItemStack carrierStack);

    ItemStack createCarrier(ItemStack normalizedKey, int amount);

    int maxPerPackage(ItemStack normalizedKey);

    ItemStack createPackage(ItemStack normalizedKey, int amount);

    Optional<PackageResource> readCanonicalPackage(ItemStack packageStack);

    boolean unpack(PackageUnpackContext context, PackageResource resource, boolean simulate);

    PackageResourceDisplay display();

    default boolean survivesWater(ItemStack packageStack, PackageInspection inspection) {
        return false;
    }

    default SawAction sawAction(ItemStack packageStack, PackageInspection inspection) {
        return SawAction.DEFAULT;
    }

    default DropAction onDestroyed(PackageDestroyContext context, List<PackageResource> resources) {
        return DropAction.PASS;
    }
}
