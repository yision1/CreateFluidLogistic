package com.yision.fluidlogistics.api.packager;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;

import com.yision.fluidlogistics.content.logistics.packageResource.PackageResourceRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class PackageResources {
    private static final PackageResourceRegistry REGISTRY = PackageResourceRegistry.create();

    private PackageResources() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static void register(PackageResourceType type) {
        REGISTRY.register(type);
    }

    public static void registerRequestSelector(
            Supplier<? extends Item> selectorItem, Supplier<ItemStack> resourceKey) {
        REGISTRY.registerRequestSelector(selectorItem, resourceKey);
    }

    public static void bootstrap() {
        REGISTRY.bootstrap();
    }

    public static boolean isBootstrapped() {
        return REGISTRY.isBootstrapped();
    }

    public static Optional<PackageResourceType> get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Optional<PackageResourceType> findType(ItemStack carrierStack) {
        return REGISTRY.findType(carrierStack);
    }

    public static Optional<PackageResource> readResource(ItemStack carrierStack) {
        return REGISTRY.readResource(carrierStack);
    }

    public static Optional<ItemStack> normalizeKey(ItemStack carrierStack) {
        return REGISTRY.normalizeKey(carrierStack);
    }

    public static Optional<ItemStack> resolveRequestKey(ItemStack carrierOrSelector) {
        return REGISTRY.resolveRequestKey(carrierOrSelector);
    }

    public static boolean sameResource(ItemStack first, ItemStack second) {
        return REGISTRY.sameResource(first, second);
    }

    public static PackageInspection inspectPackage(ItemStack packageStack) {
        return REGISTRY.inspectPackage(packageStack);
    }

    public static ItemStack createPackage(ItemStack normalizedKey, int amount) {
        return REGISTRY.createPackage(normalizedKey, amount);
    }

    public static List<ItemStack> splitPackage(ItemStack packageStack) {
        return REGISTRY.splitPackage(packageStack);
    }

    public static boolean unpackPackage(PackageUnpackContext context, boolean simulate) {
        return REGISTRY.unpackPackage(context, simulate);
    }

    public static Optional<PackageResourceDisplay> displayOf(ItemStack carrierOrKey) {
        return REGISTRY.displayOf(carrierOrKey);
    }

    public static Optional<Component> nameOf(ItemStack carrierOrKey) {
        return REGISTRY.nameOf(carrierOrKey);
    }

    public static Optional<ItemStack> iconOf(ItemStack carrierOrKey) {
        return REGISTRY.iconOf(carrierOrKey);
    }

    public static Optional<List<Component>> tooltipOf(
            ItemStack carrierOrKey, int amount, boolean advanced) {
        return REGISTRY.tooltipOf(carrierOrKey, amount, advanced);
    }

    public static Optional<List<Component>> tooltipOf(
            ItemStack carrierOrKey,
            int amount,
            boolean advanced,
            PackageResourceDisplay.TooltipContext context) {
        return REGISTRY.tooltipOf(carrierOrKey, amount, advanced, context);
    }

    public static Optional<String> formatAmount(
            ItemStack carrierOrKey, int amount, PackageResourceDisplay.Format format) {
        return REGISTRY.formatAmount(carrierOrKey, amount, format);
    }

    public static OptionalInt adjustAmount(
            ItemStack carrierOrKey, PackageResourceDisplay.Adjustment adjustment) {
        return REGISTRY.adjustAmount(carrierOrKey, adjustment);
    }

    public static boolean blocksManualOpen(ItemStack packageStack) {
        return REGISTRY.blocksManualOpen(packageStack);
    }

    public static boolean survivesWater(ItemStack packageStack) {
        return REGISTRY.survivesWater(packageStack);
    }

    public static PackageResourceType.SawAction sawAction(ItemStack packageStack) {
        return REGISTRY.sawAction(packageStack);
    }

    public static Set<ResourceLocation> handleDestroyed(PackageDestroyContext context) {
        return REGISTRY.handleDestroyed(context);
    }
}
