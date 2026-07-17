package com.yision.fluidlogistics.api.packager;

import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@ApiStatus.Experimental
public interface ResourcePackager {
    record Snapshot(@Nullable Object storageIdentity, InventorySummary resources) {
        public Snapshot {
            resources = Objects.requireNonNull(resources, "resources").copy();
        }

        @Override
        public InventorySummary resources() {
            return resources.copy();
        }

        public static Snapshot empty(@Nullable Object storageIdentity) {
            return new Snapshot(storageIdentity, InventorySummary.EMPTY);
        }
    }

    PackagerBlockEntity owner();

    ResourceLocation resourceTypeId();

    Snapshot scan();

    int extract(ItemStack normalizedKey, int maxAmount, boolean simulate);

    int insert(ItemStack normalizedKey, int maxAmount, boolean simulate);
}
