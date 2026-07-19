package com.yision.fluidlogistics.content.logistics.packageResource;

import java.util.Objects;

import com.simibubi.create.api.packager.InventoryIdentifier;

import net.createmod.catnip.math.BlockFace;

public final class ResourcePackagerInventoryIdentifier implements InventoryIdentifier {
    private final Object storageIdentity;

    public ResourcePackagerInventoryIdentifier(Object storageIdentity) {
        this.storageIdentity = Objects.requireNonNull(storageIdentity, "storageIdentity");
    }

    @Override
    public boolean contains(BlockFace face) {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this
                || obj instanceof ResourcePackagerInventoryIdentifier other
                && storageIdentity == other.storageIdentity;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(storageIdentity);
    }
}
