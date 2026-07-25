package com.yision.fluidlogistics.api.packager;

import java.util.List;
import java.util.Objects;

import com.simibubi.create.content.logistics.BigItemStack;

public record PackageResourceCraftingData(
        int outputCount,
        int transferLimit,
        List<BigItemStack> requirements) {
    public PackageResourceCraftingData {
        requirements = copyRequirements(requirements);
    }

    @Override
    public List<BigItemStack> requirements() {
        return copyRequirements(requirements);
    }

    public boolean isValid() {
        return outputCount > 0 && !requirements.isEmpty();
    }

    PackageResourceCraftingData copy() {
        return new PackageResourceCraftingData(outputCount, transferLimit, requirements);
    }

    private static List<BigItemStack> copyRequirements(List<BigItemStack> requirements) {
        Objects.requireNonNull(requirements, "requirements");
        return requirements.stream()
                .map(requirement -> {
                    Objects.requireNonNull(requirement, "requirement");
                    net.minecraft.world.item.ItemStack copy = requirement.stack.copy();
                    copy.setCount(1);
                    return new BigItemStack(copy, requirement.count);
                })
                .toList();
    }
}
