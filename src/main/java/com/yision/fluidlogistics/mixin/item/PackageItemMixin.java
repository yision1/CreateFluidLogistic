package com.yision.fluidlogistics.mixin.item;

import java.util.ArrayList;
import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.util.FluidAmountHelper;
import com.yision.fluidlogistics.util.VirtualFluidDisplayHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

@Mixin(PackageItem.class)
public abstract class PackageItemMixin {

    @Unique
    private static final ThreadLocal<List<FluidStack>> fluidlogistics$capturedFluids =
        ThreadLocal.withInitial(ArrayList::new);

    @Shadow(remap = false)
    public static ItemStackHandler getContents(ItemStack box) {
        throw new AssertionError();
    }

    @Inject(method = "getContents", at = @At("RETURN"), remap = false)
    private static void fluidlogistics$displayCompressedTanksAsFluidsInPackages(ItemStack box,
        CallbackInfoReturnable<ItemStackHandler> cir) {
        if (fluidlogistics$isRepackagingContents()) {
            return;
        }

        ItemStackHandler contents = cir.getReturnValue();
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack slotStack = contents.getStackInSlot(i);
            ItemStack displayStack = VirtualFluidDisplayHelper.getPackageDisplayStack(slotStack);
            if (displayStack != slotStack) {
                contents.setStackInSlot(i, displayStack);
            }
        }
    }

    @WrapOperation(
        method = "appendHoverText",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/items/ItemStackHandler;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;",
            remap = false
        )
    )
    private ItemStack fluidlogistics$hideFluidTooltipEntries(ItemStackHandler contents, int slot,
                                                             Operation<ItemStack> original) {
        ItemStack itemStack = original.call(contents, slot);
        if (!fluidlogistics$shouldRenderAsFluidTooltip(itemStack)) {
            return itemStack;
        }

        List<FluidStack> capturedFluids = fluidlogistics$capturedFluids.get();
        FluidStack fluid = VirtualFluidDisplayHelper.getPackageDisplayFluid(itemStack);
        fluid.setAmount(fluid.getAmount() * itemStack.getCount());
        fluidlogistics$mergeFluid(capturedFluids, fluid);
        return ItemStack.EMPTY;
    }

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void fluidlogistics$appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents,
        TooltipFlag tooltipFlag, CallbackInfo ci) {
        CompoundTag tag = stack.getOrCreateTag();
        List<FluidStack> mergedFluids = fluidlogistics$capturedFluids.get();

        try {
            if (mergedFluids.isEmpty() || !tag.contains("Items", Tag.TAG_COMPOUND)) {
                return;
            }

            ItemStackHandler contents = getContents(stack);
            int visibleNames = 0;
            int skippedNames = 0;
            for (int i = 0; i < contents.getSlots(); i++) {
                ItemStack itemStack = contents.getStackInSlot(i);
                if (itemStack.isEmpty() || itemStack.getItem() instanceof SpawnEggItem) {
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

            int remainingVisibleLines = Math.max(0, 3 - visibleNames);
            int addedFluids = Math.min(remainingVisibleLines, mergedFluids.size());
            for (int i = 0; i < addedFluids; i++) {
                FluidStack fluid = mergedFluids.get(i);
                tooltipComponents.add(insertionIndex + i, Component.literal("")
                    .append(fluid.getDisplayName())
                    .append(" " + FluidAmountHelper.format(fluid.getAmount()))
                    .withStyle(ChatFormatting.GRAY));
            }

            int totalSkippedNames = skippedNames + Math.max(0, mergedFluids.size() - addedFluids);
            if (totalSkippedNames > 0) {
                tooltipComponents.add(insertionIndex + addedFluids,
                    Component.translatable("container.shulkerBox.more", totalSkippedNames)
                        .withStyle(ChatFormatting.ITALIC));
            }
        } finally {
            mergedFluids.clear();
        }
    }

    @Inject(method = "open", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$blockManualOpenForCompressedTanks(Level world, Player player, InteractionHand hand,
        CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack box = player.getItemInHand(hand);
        if (!fluidlogistics$containsCompressedTank(box)) {
            return;
        }

        cir.setReturnValue(InteractionResultHolder.pass(box));
    }

    @Unique
    private static boolean fluidlogistics$isRepackagingContents() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if ("com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper"
                    .equals(element.getClassName())
                    && "repack".equals(element.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean fluidlogistics$shouldRenderAsFluidTooltip(ItemStack itemStack) {
        return VirtualFluidDisplayHelper.shouldDisplayAsFluidInPackage(itemStack);
    }

    @Unique
    private static boolean fluidlogistics$containsCompressedTank(ItemStack box) {
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
            if (existing.isFluidEqual(fluid) && FluidStack.areFluidStackTagsEqual(existing, fluid)) {
                existing.grow(fluid.getAmount());
                return;
            }
        }
        mergedFluids.add(fluid);
    }
}
