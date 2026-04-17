package com.yision.fluidlogistics.mixin.logistics;

import java.util.Iterator;
import java.util.List;

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
import com.yision.fluidlogistics.item.CompressedTankItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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
    private void fluidlogistics$getTotalPromisedForVirtualTank(ItemStack stack, int expiryTime, 
            CallbackInfoReturnable<Integer> cir) {
        if (!(stack.getItem() instanceof CompressedTankItem)) {
            return;
        }
        
        if (!CompressedTankItem.isVirtual(stack)) {
            return;
        }
        
        FluidStack targetFluid = CompressedTankItem.getFluid(stack);
        if (targetFluid.isEmpty()) {
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
            if (!fluidlogistics$matchesVirtualFluid(promise.promisedStack.stack, targetFluid)) {
                continue;
            }
            if (expiryTime != -1 && promise.ticksExisted >= expiryTime) {
                iterator.remove();
                changed = true;
                continue;
            }
            promised += promise.promisedStack.count;
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
    private void fluidlogistics$forceClearVirtualTank(ItemStack stack, CallbackInfo ci) {
        if (!(stack.getItem() instanceof CompressedTankItem)) {
            return;
        }
        
        if (!CompressedTankItem.isVirtual(stack)) {
            return;
        }
        
        FluidStack targetFluid = CompressedTankItem.getFluid(stack);
        if (targetFluid.isEmpty()) {
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
            if (fluidlogistics$matchesVirtualFluid(promise.promisedStack.stack, targetFluid)) {
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
    private void fluidlogistics$itemEnteredSystemVirtualTank(ItemStack stack, int amount, CallbackInfo ci) {
        if (!(stack.getItem() instanceof CompressedTankItem)) {
            return;
        }
        
        if (!CompressedTankItem.isVirtual(stack)) {
            return;
        }
        
        FluidStack targetFluid = CompressedTankItem.getFluid(stack);
        if (targetFluid.isEmpty()) {
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
            if (!fluidlogistics$matchesVirtualFluid(requestPromise.promisedStack.stack, targetFluid)) {
                continue;
            }
            
            int toSubtract = Math.min(amount, requestPromise.promisedStack.count);
            amount -= toSubtract;
            requestPromise.promisedStack.count -= toSubtract;
            
            if (requestPromise.promisedStack.count <= 0) {
                iterator.remove();
                changed = true;
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

    @Unique
    private static boolean fluidlogistics$matchesVirtualFluid(ItemStack promiseStack, FluidStack targetFluid) {
        if (!(promiseStack.getItem() instanceof CompressedTankItem)) {
            return false;
        }
        
        if (!CompressedTankItem.isVirtual(promiseStack)) {
            return false;
        }
        
        FluidStack promiseFluid = CompressedTankItem.getFluid(promiseStack);
        if (promiseFluid.isEmpty()) {
            return false;
        }
        
        return FluidStack.isSameFluidSameComponents(promiseFluid, targetFluid);
    }
}
