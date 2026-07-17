package com.yision.fluidlogistics.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

@ApiStatus.Internal
public final class CreativeTabSectionRegistry {
    private static final int ROW_SIZE = 9;
    private static final CreativeTabSectionRegistry INSTANCE = new CreativeTabSectionRegistry();

    private final Map<ResourceLocation, Registration> registrations = new LinkedHashMap<>();
    private volatile List<PositionedSection> positionedSections = List.of();

    public static CreativeTabSectionRegistry instance() {
        return INSTANCE;
    }

    public static CreativeTabSectionRegistry create() {
        return new CreativeTabSectionRegistry();
    }

    public synchronized void register(
            ResourceLocation id,
            Component title,
            ResourceLocation bannerTexture,
            int frameCount,
            long frameDurationMillis,
            List<? extends Supplier<? extends ItemLike>> items) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(bannerTexture, "bannerTexture");
        Objects.requireNonNull(items, "items");
        if (frameCount <= 0) {
            throw new IllegalArgumentException("creative tab banner frame count must be positive: " + id);
        }
        if (frameDurationMillis <= 0L) {
            throw new IllegalArgumentException("creative tab banner frame duration must be positive: " + id);
        }
        if (registrations.containsKey(id)) {
            throw new IllegalStateException("duplicate creative tab section id: " + id);
        }

        List<Supplier<? extends ItemLike>> copiedItems = new ArrayList<>(items.size());
        for (Supplier<? extends ItemLike> item : items) {
            copiedItems.add(Objects.requireNonNull(item, "item supplier for " + id));
        }
        if (copiedItems.isEmpty()) {
            throw new IllegalArgumentException("creative tab section must contain at least one item: " + id);
        }
        registrations.put(id, new Registration(
                id, title.copy(), bannerTexture, frameCount, frameDurationMillis, List.copyOf(copiedItems)));
    }

    public synchronized List<ItemStack> rebuildDisplayItems(
            Collection<ItemStack> baseItems, Set<ItemStack> searchItems) {
        Objects.requireNonNull(baseItems, "baseItems");
        Objects.requireNonNull(searchItems, "searchItems");

        List<ItemStack> displayItems = new ArrayList<>(baseItems.size() + ROW_SIZE * (registrations.size() + 1));
        addEmptyRow(displayItems);
        displayItems.addAll(baseItems);

        List<PositionedSection> positions = new ArrayList<>(registrations.size());
        for (Registration registration : registrations.values()) {
            padToRowBoundary(displayItems);
            int bannerRow = displayItems.size() / ROW_SIZE;
            addEmptyRow(displayItems);

            for (Supplier<? extends ItemLike> supplier : registration.items()) {
                ItemLike item = Objects.requireNonNull(supplier.get(), "item for " + registration.id());
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) {
                    throw new IllegalStateException("empty creative tab item for " + registration.id());
                }
                displayItems.add(stack);
                searchItems.add(stack);
            }
            positions.add(new PositionedSection(
                    registration.id(), registration.title(), registration.bannerTexture(), registration.frameCount(),
                    registration.frameDurationMillis(), bannerRow));
        }
        positionedSections = List.copyOf(positions);
        return displayItems;
    }

    public List<PositionedSection> positionedSections() {
        return positionedSections;
    }

    private static void padToRowBoundary(List<ItemStack> items) {
        int remainder = items.size() % ROW_SIZE;
        if (remainder == 0) {
            return;
        }
        for (int slot = remainder; slot < ROW_SIZE; slot++) {
            items.add(ItemStack.EMPTY);
        }
    }

    private static void addEmptyRow(List<ItemStack> items) {
        for (int slot = 0; slot < ROW_SIZE; slot++) {
            items.add(ItemStack.EMPTY);
        }
    }

    private record Registration(
            ResourceLocation id,
            Component title,
            ResourceLocation bannerTexture,
            int frameCount,
            long frameDurationMillis,
            List<Supplier<? extends ItemLike>> items) {
    }

    public record PositionedSection(
            ResourceLocation id,
            Component title,
            ResourceLocation bannerTexture,
            int frameCount,
            long frameDurationMillis,
            int bannerRow) {
    }
}
