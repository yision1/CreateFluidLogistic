package com.yision.fluidlogistics.api.packager;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record PackageResource(ResourceLocation typeId, ItemStack key, int amount) {
    public PackageResource {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(key, "key");
        if (key.isEmpty() || amount <= 0) {
            throw new IllegalArgumentException("resource key must be non-empty and amount must be positive");
        }
        key = key.copyWithCount(1);
    }

    @Override
    public ItemStack key() {
        return key.copy();
    }
}
