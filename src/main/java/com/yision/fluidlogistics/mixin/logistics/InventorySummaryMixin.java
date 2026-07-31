package com.yision.fluidlogistics.mixin.logistics;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.content.logistics.packageResource.PackageResourceKey;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Mixin(InventorySummary.class)
public abstract class InventorySummaryMixin {

    @Shadow(remap = false)
    @Final
    private java.util.Map<Item, List<BigItemStack>> items;

    @Shadow(remap = false)
    private int totalCount;

    @Unique
    private Map<PackageResourceKey, Long> fluidlogistics$resourceCounts;

    @Unique
    private Set<Item> fluidlogistics$indexedResourceItems;

    @Inject(
        method = "add(Lnet/minecraft/world/item/ItemStack;I)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$addResource(ItemStack stack, int count, CallbackInfo ci) {
        if (count == 0 || !PackageResources.isBootstrapped()) {
            return;
        }
        if (PackageResources.findType(stack).isEmpty()) {
            return;
        }

        if (totalCount < BigItemStack.INF) {
            totalCount = (int) Math.min(BigItemStack.INF, (long) totalCount + count);
        }

        List<BigItemStack> stacks = items.computeIfAbsent(stack.getItem(),
            $ -> com.google.common.collect.Lists.newArrayList());
        for (BigItemStack existing : stacks) {
            if (!ItemStack.isSameItemSameTags(existing.stack, stack)) {
                continue;
            }
            int previousCount = existing.count;
            if (existing.count < BigItemStack.INF) {
                existing.count = (int) Math.min(BigItemStack.INF, (long) existing.count + count);
            }
            fluidlogistics$addIndexedCount(stack, (long) existing.count - previousCount);
            ci.cancel();
            return;
        }

        ItemStack stored = stack.copy();
        if (stored.getCount() > stored.getMaxStackSize()) {
            stored.setCount(1);
        }
        stacks.add(new BigItemStack(stored, count));
        fluidlogistics$addIndexedCount(stored, count);
        ci.cancel();
    }

    @Inject(method = "getCountOf", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$getCountOfResource(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!PackageResources.isBootstrapped()) {
            return;
        }
        var keyResult = PackageResources.keyOf(stack);
        if (keyResult.isEmpty()) {
            return;
        }

        fluidlogistics$ensureResourceItemIndexed(stack.getItem());
        long count = fluidlogistics$resourceCounts.getOrDefault(keyResult.orElseThrow(), 0L);
        cir.setReturnValue((int) Math.min(BigItemStack.INF, count));
    }

    @Inject(method = "erase", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$eraseResource(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!PackageResources.isBootstrapped()) {
            return;
        }
        var keyResult = PackageResources.keyOf(stack);
        if (keyResult.isEmpty()) {
            return;
        }
        PackageResourceKey key = keyResult.orElseThrow();

        List<BigItemStack> stacks = items.get(stack.getItem());
        if (stacks == null) {
            cir.setReturnValue(false);
            return;
        }

        for (Iterator<BigItemStack> iterator = stacks.iterator(); iterator.hasNext();) {
            BigItemStack existing = iterator.next();
            if (!PackageResources.sameResource(existing.stack, stack)) {
                continue;
            }
            totalCount -= existing.count;
            iterator.remove();
            fluidlogistics$removeIndexedCount(stack.getItem(), key, existing.count);
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }

    @Unique
    private void fluidlogistics$ensureResourceItemIndexed(Item carrierItem) {
        if (fluidlogistics$resourceCounts == null) {
            fluidlogistics$resourceCounts = new HashMap<>();
            fluidlogistics$indexedResourceItems = Collections.newSetFromMap(new IdentityHashMap<>());
        }
        if (fluidlogistics$indexedResourceItems.contains(carrierItem)) {
            return;
        }
        List<BigItemStack> stacks = items.get(carrierItem);
        if (stacks != null) {
            for (BigItemStack entry : stacks) {
                PackageResources.keyOf(entry.stack).ifPresent(key ->
                        fluidlogistics$resourceCounts.merge(key, (long) entry.count, Math::addExact));
            }
        }
        fluidlogistics$indexedResourceItems.add(carrierItem);
    }

    @Unique
    private void fluidlogistics$addIndexedCount(ItemStack stack, long count) {
        if (count == 0 || fluidlogistics$indexedResourceItems == null
                || !fluidlogistics$indexedResourceItems.contains(stack.getItem())) {
            return;
        }
        PackageResources.keyOf(stack).ifPresent(key ->
                fluidlogistics$resourceCounts.merge(key, count, Math::addExact));
    }

    @Unique
    private void fluidlogistics$removeIndexedCount(Item carrierItem, PackageResourceKey key, int count) {
        if (fluidlogistics$indexedResourceItems == null
                || !fluidlogistics$indexedResourceItems.contains(carrierItem)) {
            return;
        }
        long remaining = fluidlogistics$resourceCounts.getOrDefault(key, 0L) - count;
        if (remaining <= 0) {
            fluidlogistics$resourceCounts.remove(key);
            return;
        }
        fluidlogistics$resourceCounts.put(key, remaining);
    }
}
