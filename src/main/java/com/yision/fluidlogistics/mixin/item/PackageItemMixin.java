package com.yision.fluidlogistics.mixin.item;

import java.util.ArrayList;
import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackageItem.class)
public abstract class PackageItemMixin {

    @Shadow
    public static ItemStackHandler getContents(ItemStack stack) {
        throw new AssertionError();
    }

    @Inject(
            method = "open",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fluidlogistics$blockManualOpenForCompressedTanks(Level world, Player player, InteractionHand hand,
                                                                  CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack box = player.getItemInHand(hand);
        if (!fluidlogistics$containsCompressedTank(box)) {
            return;
        }

        cir.setReturnValue(InteractionResultHolder.pass(box));
    }

    @WrapOperation(
            method = "appendHoverText",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/items/ItemStackHandler;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack fluidlogistics$hideFluidTooltipEntries(ItemStackHandler contents, int slot, Operation<ItemStack> original,
                                                             @Share("fluidlogistics$mergedFluids") LocalRef<List<FluidStack>> mergedFluidsRef) {
        ItemStack itemStack = original.call(contents, slot);
        if (!fluidlogistics$shouldRenderAsFluidTooltip(itemStack)) {
            return itemStack;
        }

        List<FluidStack> mergedFluids = mergedFluidsRef.get();
        if (mergedFluids == null) {
            mergedFluids = new ArrayList<>();
            mergedFluidsRef.set(mergedFluids);
        }

        FluidStack fluid = CompressedTankItem.getFluid(itemStack).copy();
        fluid.setAmount(fluid.getAmount() * itemStack.getCount());
        fluidlogistics$mergeFluid(mergedFluids, fluid);
        return ItemStack.EMPTY;
    }

    @Inject(
            method = "appendHoverText",
            at = @At("TAIL")
    )
    private void fluidlogistics$appendFluidHoverText(ItemStack stack, Item.TooltipContext tooltipContext,
                                                     List<Component> tooltipComponents, TooltipFlag tooltipFlag,
                                                     CallbackInfo ci,
                                                     @Share("fluidlogistics$mergedFluids") LocalRef<List<FluidStack>> mergedFluidsRef) {
        List<FluidStack> mergedFluids = mergedFluidsRef.get();
        if (mergedFluids == null || mergedFluids.isEmpty() || !stack.has(AllDataComponents.PACKAGE_CONTENTS)) {
            return;
        }

        ItemStackHandler contents = getContents(stack);
        int visibleNames = 0;
        int skippedNames = 0;
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack itemStack = contents.getStackInSlot(i);
            if (itemStack.isEmpty()) {
                continue;
            }
            if (itemStack.getItem() instanceof SpawnEggItem) {
                continue;
            }
            if (fluidlogistics$shouldRenderAsFluidTooltip(itemStack)) {
                continue;
            }
            if (visibleNames > 2) {
                skippedNames++;
                continue;
            }
            visibleNames++;
        }

        int insertionIndex = tooltipComponents.size();
        if (skippedNames > 0) {
            String originalMoreLine = Component.translatable("container.shulkerBox.more", skippedNames).getString();
            for (int i = tooltipComponents.size() - 1; i >= 0; i--) {
                if (tooltipComponents.get(i).getString().equals(originalMoreLine)) {
                    tooltipComponents.remove(i);
                    insertionIndex = i;
                    break;
                }
            }
        }

        int remainingSlots = Math.max(0, 3 - visibleNames);
        int addedFluids = Math.min(remainingSlots, mergedFluids.size());
        for (int i = 0; i < addedFluids; i++) {
            FluidStack fluid = mergedFluids.get(i);
            tooltipComponents.add(insertionIndex + i, Component.literal("")
                    .append(fluid.getHoverName())
                    .append(" " + FluidAmountHelper.format(fluid.getAmount()))
                    .withStyle(ChatFormatting.GRAY));
        }

        int totalSkippedNames = skippedNames + Math.max(0, mergedFluids.size() - addedFluids);
        if (totalSkippedNames > 0) {
            tooltipComponents.add(insertionIndex + addedFluids,
                    Component.translatable("container.shulkerBox.more", totalSkippedNames)
                            .withStyle(ChatFormatting.ITALIC));
        }
    }

    @Unique
    private static boolean fluidlogistics$shouldRenderAsFluidTooltip(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof CompressedTankItem)) {
            return false;
        }
        return !CompressedTankItem.getFluid(itemStack).isEmpty();
    }

    @Unique
    private static boolean fluidlogistics$containsCompressedTank(ItemStack box) {
        if (!box.has(AllDataComponents.PACKAGE_CONTENTS)) {
            return false;
        }

        ItemStackHandler contents = getContents(box);
        for (int i = 0; i < contents.getSlots(); i++) {
            if (contents.getStackInSlot(i).getItem() instanceof CompressedTankItem) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private static void fluidlogistics$mergeFluid(List<FluidStack> mergedFluids, FluidStack fluid) {
        for (FluidStack existing : mergedFluids) {
            if (FluidStack.isSameFluidSameComponents(existing, fluid)) {
                existing.grow(fluid.getAmount());
                return;
            }
        }
        mergedFluids.add(fluid);
    }
}
