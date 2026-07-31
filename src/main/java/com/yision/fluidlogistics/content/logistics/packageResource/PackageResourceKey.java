package com.yision.fluidlogistics.content.logistics.packageResource;

import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.yision.fluidlogistics.api.packager.PackageResourceType;

import net.minecraft.world.item.ItemStack;

@ApiStatus.Internal
public final class PackageResourceKey {
    private final PackageResourceType type;
    private final ItemStack normalizedKey;
    private final int hash;

    PackageResourceKey(PackageResourceType type, ItemStack normalizedKey) {
        this.type = Objects.requireNonNull(type, "type");
        this.normalizedKey = copyWithCount(Objects.requireNonNull(normalizedKey, "normalizedKey"), 1);
        this.hash = 31 * System.identityHashCode(type) + type.identityHash(this.normalizedKey);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PackageResourceKey other) || type != other.type || hash != other.hash) {
            return false;
        }
        if (ItemStack.isSameItemSameTags(normalizedKey, other.normalizedKey)) {
            return true;
        }
        return type.matches(normalizedKey.copy(), other.normalizedKey.copy());
    }

    private static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
