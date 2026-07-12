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
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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
        if (count == 0 || !CompressedTankItem.isFluidStack(stack)) {
            return;
        }

        if (totalCount < BigItemStack.INF) {
            totalCount += count;
        }

        List<BigItemStack> stacks = items.computeIfAbsent(stack.getItem(),
            $ -> com.google.common.collect.Lists.newArrayList());
        FluidStack targetFluid = CompressedTankItem.getFluid(stack);
        for (BigItemStack existing : stacks) {
            if (!CompressedTankItem.matchesFluid(existing.stack, targetFluid)) {
                continue;
            }
            if (existing.count < BigItemStack.INF) {
                existing.count += count;
            }
            ci.cancel();
            return;
        }

        if (count < 0) {
            ci.cancel();
            return;
        }

        ItemStack stackToAdd = stack.getCount() > stack.getMaxStackSize() ? stack.copyWithCount(1) : stack;
        stacks.add(new BigItemStack(stackToAdd, count));
        ci.cancel();
    }

    @Inject(method = "getCountOf", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$getCountOfFluidTank(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!CompressedTankItem.isFluidStack(stack)) {
            return;
        }

        List<BigItemStack> list = items.get(stack.getItem());
        if (list == null) {
            cir.setReturnValue(0);
            return;
        }

        FluidStack targetFluid = CompressedTankItem.getFluid(stack);
        int resultCount = 0;
        for (BigItemStack entry : list) {
            if (CompressedTankItem.matchesFluid(entry.stack, targetFluid)) {
                resultCount += entry.count;
            }
        }
        cir.setReturnValue(resultCount);
    }

    @Inject(method = "erase", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$eraseFluidTank(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!CompressedTankItem.isFluidStack(stack)) {
            return;
        }

        List<BigItemStack> stacks = items.get(stack.getItem());
        if (stacks == null) {
            cir.setReturnValue(false);
            return;
        }

        FluidStack targetFluid = CompressedTankItem.getFluid(stack);
        for (Iterator<BigItemStack> iterator = stacks.iterator(); iterator.hasNext();) {
            BigItemStack existing = iterator.next();
            if (!CompressedTankItem.matchesFluid(existing.stack, targetFluid)) {
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
