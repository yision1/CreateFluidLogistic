package com.yision.fluidlogistics.content.logistics.packageResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.jetbrains.annotations.ApiStatus;

import com.yision.fluidlogistics.api.packager.PackageInspection;
import com.yision.fluidlogistics.api.packager.PackageResource;
import com.yision.fluidlogistics.api.packager.PackageResourceGoggleInformation;
import com.yision.fluidlogistics.api.packager.PackageResources;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@ApiStatus.Internal
public final class PackageResourceGoggleDispatcher {
    private PackageResourceGoggleDispatcher() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static boolean append(
            ItemStack packageStack, List<Component> tooltip, boolean isPlayerSneaking) {
        if (packageStack == null || packageStack.isEmpty() || !PackageResources.isBootstrapped()) {
            return false;
        }
        PackageInspection inspection = PackageResources.inspectPackage(packageStack);
        if (!inspection.hasResources()) {
            return false;
        }
        return dispatch(
                packageStack,
                inspection,
                tooltip,
                isPlayerSneaking,
                id -> PackageResources.get(id)
                        .filter(PackageResourceGoggleInformation.class::isInstance)
                        .map(PackageResourceGoggleInformation.class::cast));
    }

    static boolean dispatch(
            ItemStack packageStack,
            PackageInspection inspection,
            List<Component> tooltip,
            boolean isPlayerSneaking,
            Function<ResourceLocation, Optional<PackageResourceGoggleInformation>> providerResolver) {
        Objects.requireNonNull(packageStack, "packageStack");
        Objects.requireNonNull(inspection, "inspection");
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(providerResolver, "providerResolver");

        Map<ResourceLocation, List<PackageResource>> grouped = new LinkedHashMap<>();
        for (PackageResource resource : inspection.resources()) {
            grouped.computeIfAbsent(resource.typeId(), ignored -> new ArrayList<>()).add(resource);
        }

        boolean added = false;
        for (Map.Entry<ResourceLocation, List<PackageResource>> entry : grouped.entrySet()) {
            Optional<PackageResourceGoggleInformation> provider =
                    Objects.requireNonNull(providerResolver.apply(entry.getKey()), "resolved provider");
            if (provider.isEmpty()) {
                continue;
            }
            added |= provider.orElseThrow().addToGoggleTooltip(
                    tooltip,
                    isPlayerSneaking,
                    packageStack.copy(),
                    List.copyOf(entry.getValue()));
        }
        return added;
    }
}
