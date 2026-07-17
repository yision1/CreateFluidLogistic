package com.yision.fluidlogistics.api.packager;

import java.util.List;
import java.util.Objects;

import net.minecraft.world.item.ItemStack;

public record PackageInspection(
        ItemStack packageStack,
        List<PackageResource> resources,
        List<ItemStack> ordinaryItems,
        boolean canonical) {
    public PackageInspection {
        Objects.requireNonNull(packageStack, "packageStack");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(ordinaryItems, "ordinaryItems");
        packageStack = packageStack.copy();
        resources = List.copyOf(resources);
        ordinaryItems = ordinaryItems.stream()
                .map(stack -> Objects.requireNonNull(stack, "ordinaryItem").copy())
                .toList();
    }

    @Override
    public ItemStack packageStack() {
        return packageStack.copy();
    }

    @Override
    public List<ItemStack> ordinaryItems() {
        return ordinaryItems.stream().map(ItemStack::copy).toList();
    }

    public boolean hasResources() {
        return !resources.isEmpty();
    }

    public boolean hasOrdinaryItems() {
        return !ordinaryItems.isEmpty();
    }

    public boolean isMixed() {
        return hasResources() && hasOrdinaryItems() || resources.size() > 1;
    }
}
