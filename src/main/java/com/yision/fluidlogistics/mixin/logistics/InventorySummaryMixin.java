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

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;

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
    private void fluidlogistics$addCompressedTank(ItemStack stack, int count, CallbackInfo ci) {
        if (count == 0 || stack.isEmpty()) {
            return;
        }
        
        if (!(stack.getItem() instanceof CompressedTankItem)) {
            return;
        }
        
        if (totalCount < BigItemStack.INF) {
            totalCount += count;
        }
        
        List<BigItemStack> stacks = items.computeIfAbsent(stack.getItem(), $ -> com.google.common.collect.Lists.newArrayList());
        
        if (CompressedTankItem.isVirtual(stack)) {
            net.neoforged.neoforge.fluids.FluidStack targetFluid = CompressedTankItem.getFluid(stack);

            for (BigItemStack existing : stacks) {
                if (fluidlogistics$matchesVirtualFluid(existing.stack, targetFluid)) {
                    if (existing.count < BigItemStack.INF) {
                        existing.count += count;
                    }
                    ci.cancel();
                    return;
                }
            }
            
            if (count < 0) {
                ci.cancel();
                return;
            }
        } else {
            if (count < 0) {
                int toRemove = -count;
                java.util.Iterator<BigItemStack> iterator = stacks.iterator();
                while (iterator.hasNext() && toRemove > 0) {
                    BigItemStack existing = iterator.next();
                    if (fluidlogistics$matchesRealTankExact(existing.stack, stack)) {
                        iterator.remove();
                        toRemove--;
                    }
                }
                ci.cancel();
                return;
            }
            
            for (int i = 0; i < count; i++) {
                ItemStack singleTank = stack.getCount() > stack.getMaxStackSize() ? stack.copyWithCount(1) : stack.copy();
                BigItemStack newEntry = new BigItemStack(singleTank, 1);
                stacks.add(newEntry);
            }
            ci.cancel();
            return;
        }
        
        ItemStack stackToAdd = stack;
        if (stack.getCount() > stack.getMaxStackSize()) {
            stackToAdd = stack.copyWithCount(1);
        }
        
        BigItemStack newEntry = new BigItemStack(stackToAdd, count);
        stacks.add(newEntry);
        ci.cancel();
    }

    @Inject(
        method = "getCountOf",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getCountOfVirtualTank(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!(stack.getItem() instanceof CompressedTankItem)) {
            return;
        }
        
        if (!CompressedTankItem.isVirtual(stack)) {
            return;
        }
        
        net.neoforged.neoforge.fluids.FluidStack targetFluid = CompressedTankItem.getFluid(stack);

        List<BigItemStack> list = items.get(stack.getItem());
        if (list == null) {
            cir.setReturnValue(0);
            return;
        }
        
        int resultCount = 0;
        for (BigItemStack entry : list) {
            if (fluidlogistics$matchesVirtualFluid(entry.stack, targetFluid)) {
                resultCount += entry.count;
            }
        }
        
        cir.setReturnValue(resultCount);
    }

    @Inject(
        method = "erase",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$eraseVirtualTank(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof CompressedTankItem)) {
            return;
        }
        
        if (!CompressedTankItem.isVirtual(stack)) {
            return;
        }
        
        net.neoforged.neoforge.fluids.FluidStack targetFluid = CompressedTankItem.getFluid(stack);

        List<BigItemStack> stacks = items.get(stack.getItem());
        if (stacks == null) {
            cir.setReturnValue(false);
            return;
        }
        
        for (Iterator<BigItemStack> iterator = stacks.iterator(); iterator.hasNext();) {
            BigItemStack existing = iterator.next();
            if (fluidlogistics$matchesVirtualFluid(existing.stack, targetFluid)) {
                totalCount -= existing.count;
                iterator.remove();
                cir.setReturnValue(true);
                return;
            }
        }
        
        cir.setReturnValue(false);
    }

    @Unique
    private boolean fluidlogistics$matchesVirtualFluid(ItemStack stackInSummary, net.neoforged.neoforge.fluids.FluidStack targetFluid) {
        if (!(stackInSummary.getItem() instanceof CompressedTankItem)) {
            return false;
        }
        
        if (!CompressedTankItem.isVirtual(stackInSummary)) {
            return false;
        }
        
        net.neoforged.neoforge.fluids.FluidStack fluidInSummary = CompressedTankItem.getFluid(stackInSummary);

        return net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(fluidInSummary, targetFluid);
    }

    @Unique
    private boolean fluidlogistics$matchesRealTank(ItemStack stackInSummary, ItemStack targetStack) {
        if (!(stackInSummary.getItem() instanceof CompressedTankItem)) {
            return false;
        }
        
        if (CompressedTankItem.isVirtual(stackInSummary)) {
            return false;
        }
        
        net.neoforged.neoforge.fluids.FluidStack fluidInSummary = CompressedTankItem.getFluid(stackInSummary);
        net.neoforged.neoforge.fluids.FluidStack targetFluid = CompressedTankItem.getFluid(targetStack);

        return net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(fluidInSummary, targetFluid)
                && fluidInSummary.getAmount() == targetFluid.getAmount();
    }

    @Unique
    private boolean fluidlogistics$matchesRealTankExact(ItemStack stackInSummary, ItemStack targetStack) {
        if (!(stackInSummary.getItem() instanceof CompressedTankItem)) {
            return false;
        }
        
        if (CompressedTankItem.isVirtual(stackInSummary)) {
            return false;
        }
        
        return ItemStack.isSameItemSameComponents(stackInSummary, targetStack);
    }
}
