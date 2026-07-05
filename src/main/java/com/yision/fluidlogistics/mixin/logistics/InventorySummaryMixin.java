package com.yision.fluidlogistics.mixin.logistics;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.Lists;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

@Mixin(value = InventorySummary.class, remap = false)
public abstract class InventorySummaryMixin {

    @Shadow
    @Final
    private Map<Item, List<BigItemStack>> items;

    @Shadow
    private int totalCount;

    @Shadow
    private List<BigItemStack> stacksByCount;

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"), cancellable = true)
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

        List<BigItemStack> stacks = items.computeIfAbsent(stack.getItem(), $ -> Lists.newArrayList());
        stacksByCount = null;

        if (CompressedTankItem.isVirtual(stack)) {
            FluidStack targetFluid = CompressedTankItem.getFluid(stack);
            if (targetFluid.isEmpty()) {
                ci.cancel();
                return;
            }

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
                Iterator<BigItemStack> iterator = stacks.iterator();
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
                stacks.add(new BigItemStack(singleTank, 1));
            }
            ci.cancel();
            return;
        }

        ItemStack stackToAdd = stack.getCount() > stack.getMaxStackSize() ? stack.copyWithCount(1) : stack;
        stacks.add(new BigItemStack(stackToAdd, count));
        ci.cancel();
    }

    @Inject(method = "getCountOf", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$getCountOfVirtualTank(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!(stack.getItem() instanceof CompressedTankItem)) {
            return;
        }
        if (!CompressedTankItem.isVirtual(stack)) {
            return;
        }

        FluidStack targetFluid = CompressedTankItem.getFluid(stack);

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

    @Inject(method = "erase", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$eraseVirtualTank(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof CompressedTankItem)) {
            return;
        }
        if (!CompressedTankItem.isVirtual(stack)) {
            return;
        }

        FluidStack targetFluid = CompressedTankItem.getFluid(stack);

        List<BigItemStack> stacks = items.get(stack.getItem());
        if (stacks == null) {
            cir.setReturnValue(false);
            return;
        }

        stacksByCount = null;
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
    private boolean fluidlogistics$matchesVirtualFluid(ItemStack stackInSummary, FluidStack targetFluid) {
        if (!(stackInSummary.getItem() instanceof CompressedTankItem)) {
            return false;
        }
        if (!CompressedTankItem.isVirtual(stackInSummary)) {
            return false;
        }

        FluidStack fluidInSummary = CompressedTankItem.getFluid(stackInSummary);
        return FluidHelper.isSame(fluidInSummary, targetFluid);
    }

    @Unique
    private boolean fluidlogistics$matchesRealTankExact(ItemStack stackInSummary, ItemStack targetStack) {
        if (!(stackInSummary.getItem() instanceof CompressedTankItem)) {
            return false;
        }
        if (CompressedTankItem.isVirtual(stackInSummary)) {
            return false;
        }

        return ItemStack.isSameItemSameTags(stackInSummary, targetStack);
    }
}
