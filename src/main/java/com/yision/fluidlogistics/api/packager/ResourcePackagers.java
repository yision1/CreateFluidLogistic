package com.yision.fluidlogistics.api.packager;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.yision.fluidlogistics.content.logistics.packageResource.ResourcePackagerEngine;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ResourcePackagers {
    private ResourcePackagers() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static Optional<ResourcePackager> of(@Nullable BlockEntity blockEntity) {
        return ResourcePackagerEngine.of(blockEntity);
    }

    public static Optional<ResourcePackager> ownerOf(@Nullable PackagerBlockEntity owner) {
        return ResourcePackagerEngine.ownerOf(owner);
    }

    public static Optional<ResourcePackager> fromLink(LogisticallyLinkedBehaviour link) {
        return ResourcePackagerEngine.fromLink(link);
    }

    public static Optional<ResourcePackager> fromLink(
            LogisticallyLinkedBehaviour link,
            PackageResourceType type,
            ItemStack normalizedKey) {
        return ResourcePackagerEngine.fromLink(link, type, normalizedKey);
    }

    public static boolean supports(
            ResourcePackager packager, PackageResourceType type, ItemStack normalizedKey) {
        return ResourcePackagerEngine.supports(packager, type, normalizedKey);
    }

    public static InventorySummary getAvailableResources(ResourcePackager packager) {
        return ResourcePackagerEngine.getAvailableResources(packager);
    }

    public static void triggerStockCheck(ResourcePackager packager) {
        ResourcePackagerEngine.triggerStockCheck(packager);
    }

    public static void attemptToSend(
            ResourcePackager packager, @Nullable List<PackagingRequest> queuedRequests) {
        ResourcePackagerEngine.attemptToSend(packager, queuedRequests);
    }

    public static boolean unpack(ResourcePackager packager, ItemStack packageStack, boolean simulate) {
        return ResourcePackagerEngine.unpack(packager, packageStack, simulate);
    }
}
