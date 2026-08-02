package com.yision.fluidlogistics.mixin.logistics;

import java.util.ArrayList;
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

import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.BigItemStack;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.content.logistics.packageResource.PackageResourceKey;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Mixin(RequestPromiseQueue.class)
public abstract class RequestPromiseQueueMixin {

    @Shadow(remap = false)
    @Final
    private java.util.Map<Item, List<RequestPromise>> promisesByItem;

    @Shadow(remap = false)
    private Runnable onChanged;

    @Unique
    private Map<PackageResourceKey, List<RequestPromise>> fluidlogistics$resourcePromises;

    @Unique
    private Set<Item> fluidlogistics$indexedPromiseItems;

    @Inject(method = "add", at = @At("TAIL"), remap = false)
    private void fluidlogistics$indexAddedPromise(RequestPromise promise, CallbackInfo ci) {
        if (fluidlogistics$indexedPromiseItems == null) {
            return;
        }
        Item carrierItem = promise.promisedStack.stack.getItem();
        if (!fluidlogistics$indexedPromiseItems.contains(carrierItem)) {
            return;
        }
        PackageResources.keyOf(promise.promisedStack.stack).ifPresent(key ->
                fluidlogistics$indexPromise(key, promise));
    }

    @Inject(
        method = "getTotalPromisedAndRemoveExpired",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getTotalPromisedForFluidTank(ItemStack stack, int expiryTime,
            CallbackInfoReturnable<Integer> cir) {
        if (!PackageResources.isBootstrapped()) {
            return;
        }
        var keyResult = PackageResources.keyOf(stack);
        if (keyResult.isEmpty()) {
            return;
        }
        PackageResourceKey key = keyResult.orElseThrow();
        fluidlogistics$ensurePromiseItemIndexed(stack.getItem());
        List<RequestPromise> list = fluidlogistics$resourcePromises.get(key);
        if (list == null) {
            cir.setReturnValue(0);
            return;
        }
        
        int promised = 0;
        Set<RequestPromise> expired = null;
        
        for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
            RequestPromise promise = iterator.next();
            if (expiryTime != -1 && promise.ticksExisted >= expiryTime) {
                iterator.remove();
                if (expired == null) {
                    expired = Collections.newSetFromMap(new IdentityHashMap<>());
                }
                expired.add(promise);
                continue;
            }
            promised = (int) Math.min(BigItemStack.INF,
                    (long) promised + promise.promisedStack.count);
        }

        if (list.isEmpty()) {
            fluidlogistics$resourcePromises.remove(key);
        }
        if (expired != null) {
            fluidlogistics$removePromisesFromNativeList(stack.getItem(), expired);
            onChanged.run();
        }
        
        cir.setReturnValue(promised);
    }

    @Inject(
        method = "forceClear",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$forceClearFluidTank(ItemStack stack, CallbackInfo ci) {
        if (!PackageResources.isBootstrapped()) {
            return;
        }
        var keyResult = PackageResources.keyOf(stack);
        if (keyResult.isEmpty()) {
            return;
        }
        PackageResourceKey key = keyResult.orElseThrow();
        fluidlogistics$ensurePromiseItemIndexed(stack.getItem());
        List<RequestPromise> list = fluidlogistics$resourcePromises.remove(key);
        if (list == null) {
            ci.cancel();
            return;
        }
        
        boolean changed = !list.isEmpty();
        Set<RequestPromise> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        removed.addAll(list);
        fluidlogistics$removePromisesFromNativeList(stack.getItem(), removed);
        
        if (changed) {
            onChanged.run();
        }
        
        ci.cancel();
    }

    @Inject(
        method = "itemEnteredSystem",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$itemEnteredSystemFluidTank(ItemStack stack, int amount, CallbackInfo ci) {
        if (!PackageResources.isBootstrapped()) {
            return;
        }
        var keyResult = PackageResources.keyOf(stack);
        if (keyResult.isEmpty()) {
            return;
        }
        PackageResourceKey key = keyResult.orElseThrow();
        fluidlogistics$ensurePromiseItemIndexed(stack.getItem());
        List<RequestPromise> list = fluidlogistics$resourcePromises.get(key);
        if (list == null) {
            ci.cancel();
            return;
        }
        
        boolean changed = false;
        Set<RequestPromise> removed = null;
        for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
            RequestPromise requestPromise = iterator.next();
            int toSubtract = Math.min(amount, requestPromise.promisedStack.count);
            amount -= toSubtract;
            requestPromise.promisedStack.count -= toSubtract;
            changed |= toSubtract > 0;
            
            if (requestPromise.promisedStack.count <= 0) {
                iterator.remove();
                if (removed == null) {
                    removed = Collections.newSetFromMap(new IdentityHashMap<>());
                }
                removed.add(requestPromise);
            }
            if (amount <= 0) {
                break;
            }
        }
        
        if (list.isEmpty()) {
            fluidlogistics$resourcePromises.remove(key);
        }
        if (removed != null) {
            fluidlogistics$removePromisesFromNativeList(stack.getItem(), removed);
        }
        
        if (changed) {
            onChanged.run();
        }
        
        ci.cancel();
    }

    @Unique
    private void fluidlogistics$ensurePromiseItemIndexed(Item carrierItem) {
        if (fluidlogistics$resourcePromises == null) {
            fluidlogistics$resourcePromises = new HashMap<>();
            fluidlogistics$indexedPromiseItems = Collections.newSetFromMap(new IdentityHashMap<>());
        }
        if (fluidlogistics$indexedPromiseItems.contains(carrierItem)) {
            return;
        }
        List<RequestPromise> promises = promisesByItem.get(carrierItem);
        if (promises != null) {
            for (RequestPromise promise : promises) {
                PackageResources.keyOf(promise.promisedStack.stack).ifPresent(key ->
                        fluidlogistics$indexPromise(key, promise));
            }
        }
        fluidlogistics$indexedPromiseItems.add(carrierItem);
    }

    @Unique
    private void fluidlogistics$indexPromise(PackageResourceKey key, RequestPromise promise) {
        List<RequestPromise> promises = fluidlogistics$resourcePromises.computeIfAbsent(
                key, ignored -> new ArrayList<>());
        for (RequestPromise existing : promises) {
            if (existing == promise) {
                return;
            }
        }
        promises.add(promise);
    }

    @Unique
    private void fluidlogistics$removePromisesFromNativeList(Item carrierItem, Set<RequestPromise> removed) {
        List<RequestPromise> promises = promisesByItem.get(carrierItem);
        if (promises == null) {
            return;
        }
        promises.removeIf(removed::contains);
        if (promises.isEmpty()) {
            promisesByItem.remove(carrierItem);
        }
    }

}
