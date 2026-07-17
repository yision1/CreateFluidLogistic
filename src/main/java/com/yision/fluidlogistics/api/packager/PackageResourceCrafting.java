package com.yision.fluidlogistics.api.packager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;

public final class PackageResourceCrafting {
    private static final List<Entry> DATA = new ArrayList<>();

    private PackageResourceCrafting() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static boolean has(@Nullable CraftableBigItemStack stack) {
        return get(stack).isPresent();
    }

    public static synchronized Optional<PackageResourceCraftingData> get(
            @Nullable CraftableBigItemStack stack) {
        if (stack == null) {
            return Optional.empty();
        }
        for (int index = DATA.size() - 1; index >= 0; index--) {
            Entry entry = DATA.get(index);
            CraftableBigItemStack candidate = entry.stack().get();
            if (candidate == null) {
                DATA.remove(index);
                continue;
            }
            if (candidate == stack) {
                return entry.data().isValid() ? Optional.of(entry.data()) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static synchronized void set(
            CraftableBigItemStack stack, PackageResourceCraftingData data) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(data, "data");
        clear(stack);
        DATA.add(new Entry(new WeakReference<>(stack), data.copy()));
    }

    public static synchronized void clear(@Nullable CraftableBigItemStack stack) {
        for (int index = DATA.size() - 1; index >= 0; index--) {
            CraftableBigItemStack candidate = DATA.get(index).stack().get();
            if (candidate == null || candidate == stack) {
                DATA.remove(index);
            }
        }
    }

    private record Entry(
            WeakReference<CraftableBigItemStack> stack,
            PackageResourceCraftingData data) {
    }
}
