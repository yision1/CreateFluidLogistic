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

import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.BigItemStack;
import com.yision.fluidlogistics.api.packager.PackageResources;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Mixin(RequestPromiseQueue.class)
public abstract class RequestPromiseQueueMixin {

    @Shadow(remap = false)
    @Final
    private java.util.Map<Item, List<RequestPromise>> promisesByItem;

    @Shadow(remap = false)
    private Runnable onChanged;

    @Inject(
        method = "getTotalPromisedAndRemoveExpired",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getTotalPromisedForFluidTank(ItemStack stack, int expiryTime,
            CallbackInfoReturnable<Integer> cir) {
        if (!PackageResources.isBootstrapped() || PackageResources.findType(stack).isEmpty()) {
            return;
        }

        List<RequestPromise> list = promisesByItem.get(stack.getItem());
        if (list == null) {
            cir.setReturnValue(0);
            return;
        }
        
        int promised = 0;
        boolean changed = false;
        
        for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
            RequestPromise promise = iterator.next();
            if (!PackageResources.sameResource(promise.promisedStack.stack, stack)) {
                continue;
            }
            if (expiryTime != -1 && promise.ticksExisted >= expiryTime) {
                iterator.remove();
                changed = true;
                continue;
            }
            promised = (int) Math.min(BigItemStack.INF,
                    (long) promised + promise.promisedStack.count);
        }

        if (list.isEmpty()) {
            changed |= promisesByItem.remove(stack.getItem()) != null;
        }
        
        if (changed) {
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
        if (!PackageResources.isBootstrapped() || PackageResources.findType(stack).isEmpty()) {
            return;
        }

        List<RequestPromise> list = promisesByItem.get(stack.getItem());
        if (list == null) {
            ci.cancel();
            return;
        }
        
        boolean changed = false;
        for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
            RequestPromise promise = iterator.next();
            if (PackageResources.sameResource(promise.promisedStack.stack, stack)) {
                iterator.remove();
                changed = true;
            }
        }
        
        if (list.isEmpty()) {
            changed |= promisesByItem.remove(stack.getItem()) != null;
        }
        
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
        if (!PackageResources.isBootstrapped() || PackageResources.findType(stack).isEmpty()) {
            return;
        }

        List<RequestPromise> list = promisesByItem.get(stack.getItem());
        if (list == null) {
            ci.cancel();
            return;
        }
        
        boolean changed = false;
        for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
            RequestPromise requestPromise = iterator.next();
            if (!PackageResources.sameResource(requestPromise.promisedStack.stack, stack)) {
                continue;
            }
            
            int toSubtract = Math.min(amount, requestPromise.promisedStack.count);
            amount -= toSubtract;
            requestPromise.promisedStack.count -= toSubtract;
            changed |= toSubtract > 0;
            
            if (requestPromise.promisedStack.count <= 0) {
                iterator.remove();
            }
            if (amount <= 0) {
                break;
            }
        }
        
        if (list.isEmpty()) {
            changed |= promisesByItem.remove(stack.getItem()) != null;
        }
        
        if (changed) {
            onChanged.run();
        }
        
        ci.cancel();
    }

}
