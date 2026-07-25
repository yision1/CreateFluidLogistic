package com.yision.fluidlogistics.api.packager;

import java.util.Objects;

import com.simibubi.create.content.logistics.box.PackageEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

public record PackageDestroyContext(
        ServerLevel level,
        PackageEntity entity,
        DamageSource source,
        ItemStack packageStack) {
    public PackageDestroyContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(packageStack, "packageStack");
        packageStack = packageStack.copy();
    }

    @Override
    public ItemStack packageStack() {
        return packageStack.copy();
    }
}
