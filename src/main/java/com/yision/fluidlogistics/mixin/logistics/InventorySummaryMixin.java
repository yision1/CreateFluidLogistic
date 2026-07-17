package com.yision.fluidlogistics.mixin.logistics;

import java.util.Iterator;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.yision.fluidlogistics.api.packager.PackageResources;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Mixin(InventorySummary.class)
public abstract class InventorySummaryMixin {

    @Shadow(remap = false)
    @Final
    private java.util.Map<Item, List<BigItemStack>> items;

    @Shadow(remap = false)
    private int totalCount;

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
            if (!ItemStack.isSameItemSameComponents(existing.stack, stack)) {
                continue;
            }
            if (existing.count < BigItemStack.INF) {
                existing.count = (int) Math.min(BigItemStack.INF, (long) existing.count + count);
            }
            ci.cancel();
            return;
        }

        ItemStack stored = stack.getCount() > stack.getMaxStackSize()
                ? stack.copyWithCount(1)
                : stack.copy();
        stacks.add(new BigItemStack(stored, count));
        ci.cancel();
    }

    @Inject(method = "getCountOf", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$getCountOfResource(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!PackageResources.isBootstrapped() || PackageResources.findType(stack).isEmpty()) {
            return;
        }

        List<BigItemStack> list = items.get(stack.getItem());
        if (list == null) {
            cir.setReturnValue(0);
            return;
        }

        int resultCount = 0;
        for (BigItemStack entry : list) {
            if (PackageResources.sameResource(entry.stack, stack)) {
                resultCount = (int) Math.min(BigItemStack.INF, (long) resultCount + entry.count);
            }
        }
        cir.setReturnValue(resultCount);
    }

    @Inject(method = "erase", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$eraseResource(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!PackageResources.isBootstrapped() || PackageResources.findType(stack).isEmpty()) {
            return;
        }

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
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(false);
    }
}
