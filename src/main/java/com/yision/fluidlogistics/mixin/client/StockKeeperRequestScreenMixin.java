package com.yision.fluidlogistics.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.render.FluidSlotAmountRenderer;
import com.yision.fluidlogistics.util.FluidAmountHelper;
import com.yision.fluidlogistics.util.IFluidCraftableBigItemStack;

import net.createmod.catnip.data.Couple;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;
import net.createmod.catnip.data.Pair;

@Mixin(StockKeeperRequestScreen.class)
public abstract class StockKeeperRequestScreenMixin {

    @Shadow(remap = false)
    public List<BigItemStack> itemsToOrder;

    @Shadow(remap = false)
    public List<CraftableBigItemStack> recipesToOrder;

    @Shadow(remap = false)
    StockTickerBlockEntity blockEntity;

    @Shadow(remap = false)
    private boolean canRequestCraftingPackage;

    @Shadow(remap = false)
    protected abstract BigItemStack getOrderForItem(ItemStack stack);

    @Shadow(remap = false)
    protected abstract Pair<Integer, List<List<BigItemStack>>> maxCraftable(CraftableBigItemStack cbis, InventorySummary summary,
            java.util.function.Function<ItemStack, Integer> countModifier, int newTypeLimit);

    @Shadow(remap = false)
    protected abstract Couple<Integer> getHoveredSlot(int mouseX, int mouseY);

    @Shadow(remap = false)
    private void drawItemCount(GuiGraphics graphics, int count, int customCount) {
    }

    @Unique
    private boolean fluidlogistics$isCompressedTank = false;

    @Unique
    private int fluidlogistics$fluidAmount = 0;

    @Inject(
        method = "renderItemEntry",
        at = @At("HEAD"),
        remap = false,
        cancellable = true
    )
    private void fluidlogistics$onRenderItemEntryHead(GuiGraphics graphics, float scale, BigItemStack entry, 
            boolean isStackHovered, boolean isRenderingOrders, CallbackInfo ci) {
        fluidlogistics$isCompressedTank = false;
        fluidlogistics$fluidAmount = 0;

        ItemStack stack = entry.stack;
        if (stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(stack);
            if (!fluid.isEmpty()) {
                fluidlogistics$isCompressedTank = true;
                fluidlogistics$fluidAmount = entry.count;
            }
        }
    }

    @Redirect(
        method = "renderItemEntry",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;" +
                     "drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            remap = false
        ),
        remap = false
    )
    private void fluidlogistics$redirectDrawItemCount(StockKeeperRequestScreen instance, 
            GuiGraphics graphics, int count, int customCount) {
        if (fluidlogistics$isCompressedTank) {
            if (fluidlogistics$fluidAmount > 1) {
                FluidSlotAmountRenderer.renderInStockKeeper(graphics, fluidlogistics$fluidAmount);
            }
            return;
        }
        drawItemCount(graphics, count, customCount);
    }

    @ModifyExpressionValue(
        method = "mouseClicked",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I",
            remap = true
        ),
        remap = false
    )
    private int fluidlogistics$modifyTransferShiftForFluid(int original, @Local BigItemStack entry) {
        if (fluidlogistics$isVirtualCompressedTank(entry.stack)) {
            return FluidAmountHelper.getFluidRequestTransferAmount(true, false);
        }
        return original;
    }

    @ModifyExpressionValue(
        method = "mouseClicked",
        at = @At(
            value = "CONSTANT",
            args = "intValue=10"
        ),
        remap = false
    )
    private int fluidlogistics$modifyTransferCtrlForFluid(int original, @Local BigItemStack entry) {
        if (fluidlogistics$isVirtualCompressedTank(entry.stack)) {
            return FluidAmountHelper.getFluidRequestTransferAmount(false, true);
        }
        return original;
    }

    @ModifyExpressionValue(
        method = "mouseClicked",
        at = @At(
            value = "CONSTANT",
            args = "intValue=1"
        ),
        remap = false
    )
    private int fluidlogistics$modifyTransferNormalForFluid(int original, @Local BigItemStack entry) {
        if (fluidlogistics$isVirtualCompressedTank(entry.stack)) {
            return FluidAmountHelper.getFluidRequestTransferAmount(false, false);
        }
        return original;
    }

    @ModifyExpressionValue(
        method = "mouseScrolled",
        at = @At(
            value = "CONSTANT",
            args = "intValue=10"
        ),
        remap = false
    )
    private int fluidlogistics$modifyScrollTransferCtrlForFluid(int original, @Local BigItemStack entry) {
        if (fluidlogistics$isVirtualCompressedTank(entry.stack)) {
            return FluidAmountHelper.getFluidRequestTransferAmount(false, true);
        }
        return original;
    }

    @ModifyExpressionValue(
        method = "mouseScrolled",
        at = @At(
            value = "CONSTANT",
            args = "intValue=1"
        ),
        remap = false
    )
    private int fluidlogistics$modifyScrollTransferNormalForFluid(int original, @Local BigItemStack entry) {
        if (fluidlogistics$isVirtualCompressedTank(entry.stack)) {
            return FluidAmountHelper.getFluidRequestTransferAmount(false, false);
        }
        return original;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$handleCustomFluidRecipeClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 && button != 1) {
            return;
        }

        Couple<Integer> hoveredSlot = getHoveredSlot((int) mouseX, (int) mouseY);
        if (hoveredSlot.getFirst() != -2) {
            return;
        }

        CraftableBigItemStack cbis = recipesToOrder.get(hoveredSlot.getSecond());
        if (!fluidlogistics$isCustomFluidCraftable(cbis)) {
            return;
        }

        IFluidCraftableBigItemStack data = (IFluidCraftableBigItemStack) cbis;
        int delta = fluidlogistics$getRecipeStepAmount(data);
        if (button == 1) {
            delta = -delta;
        }

        fluidlogistics$handleCustomFluidCraftableRequest(cbis, delta);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$handleCustomFluidRecipeScroll(double mouseX, double mouseY, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        Couple<Integer> hoveredSlot = getHoveredSlot((int) mouseX, (int) mouseY);
        if (hoveredSlot.getFirst() != -2) {
            return;
        }

        CraftableBigItemStack cbis = recipesToOrder.get(hoveredSlot.getSecond());
        if (!fluidlogistics$isCustomFluidCraftable(cbis)) {
            return;
        }

        int steps = Mth.ceil(Math.abs(scrollY));
        if (steps <= 0) {
            cir.setReturnValue(true);
            return;
        }

        IFluidCraftableBigItemStack data = (IFluidCraftableBigItemStack) cbis;
        int delta = fluidlogistics$getRecipeStepAmount(data) * steps;
        if (scrollY < 0) {
            delta = -delta;
        }

        fluidlogistics$handleCustomFluidCraftableRequest(cbis, delta);
        cir.setReturnValue(true);
    }

    @Inject(method = "requestCraftable", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$requestCustomFluidRecipe(CraftableBigItemStack cbis, int requestedDifference, CallbackInfo ci) {
        if (!fluidlogistics$hasCustomRecipeData(cbis)) {
            return;
        }

        fluidlogistics$handleCustomFluidCraftableRequest(cbis, requestedDifference);
        ci.cancel();
    }

    @Inject(method = "updateCraftableAmounts", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$handleCustomRecipeAmountUpdates(CallbackInfo ci) {
        for (CraftableBigItemStack cbis : recipesToOrder) {
            if (cbis instanceof IFluidCraftableBigItemStack data && data.fluidlogistics$hasCustomRecipeData()) {
                fluidlogistics$updateCraftableAmountsWithCustomEntries();
                ci.cancel();
                return;
            }
        }
    }

    @Unique
    private void fluidlogistics$updateCraftableAmountsWithCustomEntries() {
        InventorySummary usedItems = new InventorySummary();
        InventorySummary availableItems = new InventorySummary();

        for (BigItemStack ordered : itemsToOrder) {
            availableItems.add(ordered.stack, ordered.count);
        }

        boolean hasCustomEntries = false;

        for (CraftableBigItemStack cbis : recipesToOrder) {
            if (cbis instanceof IFluidCraftableBigItemStack data && data.fluidlogistics$hasCustomRecipeData()) {
                hasCustomEntries = true;

                int outputCount = data.fluidlogistics$getCustomOutputCount();
                if (outputCount <= 0) {
                    cbis.count = 0;
                    continue;
                }

                int maxSets = fluidlogistics$getCustomCraftableSets(
                    availableItems, usedItems, data.fluidlogistics$getCustomRequirements());
                cbis.count = Math.min(cbis.count, maxSets * outputCount);

                int committedSets = cbis.count / outputCount;
                for (BigItemStack requirement : data.fluidlogistics$getCustomRequirements()) {
                    usedItems.add(requirement.stack, requirement.count * committedSets);
                }
                continue;
            }

            Pair<Integer, List<List<BigItemStack>>> craftingResult =
                maxCraftable(cbis, availableItems, stack -> -usedItems.getCountOf(stack), -1);
            int maxCraftable = craftingResult.getFirst();
            List<List<BigItemStack>> validEntriesByIngredient = craftingResult.getSecond();
            int outputCount = cbis.getOutputCount(blockEntity.getLevel());

            cbis.count = Math.min(cbis.count, maxCraftable);

            for (List<BigItemStack> list : validEntriesByIngredient) {
                int remaining = cbis.count / outputCount;
                for (BigItemStack entry : list) {
                    if (remaining <= 0) {
                        break;
                    }
                    usedItems.add(entry.stack, Math.min(remaining, entry.count));
                    remaining -= entry.count;
                }
            }
        }

        canRequestCraftingPackage = false;
        if (hasCustomEntries) {
            return;
        }

        for (BigItemStack ordered : itemsToOrder) {
            if (usedItems.getCountOf(ordered.stack) != ordered.count) {
                return;
            }
        }
        canRequestCraftingPackage = true;
    }

    @Unique
    private static int fluidlogistics$getCustomCraftableSets(InventorySummary availableItems, List<BigItemStack> existingOrders,
            List<BigItemStack> requirements) {
        int craftableSets = Integer.MAX_VALUE;
        for (BigItemStack requirement : requirements) {
            int orderedCount = fluidlogistics$getMatchingCount(existingOrders, requirement.stack);
            int available = availableItems.getCountOf(requirement.stack) - orderedCount;
            craftableSets = Math.min(craftableSets, available / requirement.count);
        }
        return craftableSets == Integer.MAX_VALUE ? 0 : Math.max(0, craftableSets);
    }

    @Unique
    private static int fluidlogistics$getCustomCraftableSets(InventorySummary availableItems, InventorySummary usedItems,
            List<BigItemStack> requirements) {
        int craftableSets = Integer.MAX_VALUE;
        for (BigItemStack requirement : requirements) {
            int available = availableItems.getCountOf(requirement.stack) - usedItems.getCountOf(requirement.stack);
            craftableSets = Math.min(craftableSets, available / requirement.count);
        }
        return craftableSets == Integer.MAX_VALUE ? 0 : Math.max(0, craftableSets);
    }

    @Unique
    private static boolean fluidlogistics$canFitCustomRecipe(List<BigItemStack> existingOrders, List<BigItemStack> requirements) {
        int totalTypes = existingOrders.size();
        List<ItemStack> newTypes = new java.util.ArrayList<>();

        for (BigItemStack requirement : requirements) {
            if (fluidlogistics$hasMatchingStack(existingOrders, requirement.stack)
                    || fluidlogistics$hasMatchingStack(newTypes, requirement.stack)) {
                continue;
            }
            newTypes.add(requirement.stack);
            totalTypes++;
            if (totalTypes > 9) {
                return false;
            }
        }

        return true;
    }

    @Unique
    private static int fluidlogistics$getMatchingCount(List<BigItemStack> stacks, ItemStack target) {
        int total = 0;
        for (BigItemStack entry : stacks) {
            if (ItemStack.isSameItemSameComponents(entry.stack, target)) {
                total += entry.count;
            }
        }
        return total;
    }

    @Unique
    private static boolean fluidlogistics$hasMatchingStack(List<?> stacks, ItemStack target) {
        for (Object entry : stacks) {
            ItemStack stack = entry instanceof BigItemStack bigItemStack ? bigItemStack.stack : (ItemStack) entry;
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean fluidlogistics$hasCustomRecipeData(CraftableBigItemStack cbis) {
        return cbis instanceof IFluidCraftableBigItemStack data
            && data.fluidlogistics$hasCustomRecipeData();
    }

    @Unique
    private boolean fluidlogistics$isCustomFluidCraftable(CraftableBigItemStack cbis) {
        return fluidlogistics$hasCustomRecipeData(cbis)
            && fluidlogistics$isVirtualCompressedTank(cbis.stack);
    }

    @Unique
    private static boolean fluidlogistics$isVirtualCompressedTank(ItemStack stack) {
        return stack.getItem() instanceof CompressedTankItem
            && CompressedTankItem.isVirtual(stack);
    }

    @Unique
    private int fluidlogistics$getRecipeStepAmount(IFluidCraftableBigItemStack data) {
        int outputCount = Math.max(1, data.fluidlogistics$getCustomOutputCount());
        if (Screen.hasShiftDown()) {
            return Math.max(outputCount, data.fluidlogistics$getCustomTransferLimit());
        }
        if (Screen.hasControlDown()) {
            return outputCount * 10;
        }
        return outputCount;
    }

    @Unique
    private void fluidlogistics$handleCustomFluidCraftableRequest(CraftableBigItemStack cbis, int requestedDifference) {
        IFluidCraftableBigItemStack data = (IFluidCraftableBigItemStack) cbis;
        int outputCount = data.fluidlogistics$getCustomOutputCount();
        if (outputCount <= 0) {
            return;
        }

        boolean takeOrdersAway = requestedDifference < 0;
        if (takeOrdersAway) {
            requestedDifference = Math.max(-cbis.count, requestedDifference);
        }
        if (requestedDifference == 0) {
            return;
        }

        int requestedSets = Mth.ceil(Math.abs(requestedDifference) / (float) outputCount);
        int applicableSets;

        if (takeOrdersAway) {
            applicableSets = Math.min(requestedSets, cbis.count / outputCount);
        } else {
            InventorySummary availableItems = blockEntity.getLastClientsideStockSnapshotAsSummary();
            if (availableItems == null) {
                return;
            }

            if (!fluidlogistics$canFitCustomRecipe(itemsToOrder, data.fluidlogistics$getCustomRequirements())) {
                return;
            }

            applicableSets = fluidlogistics$getCustomCraftableSets(
                availableItems, itemsToOrder, data.fluidlogistics$getCustomRequirements());
            applicableSets = Math.min(requestedSets, applicableSets);
        }

        if (applicableSets <= 0) {
            return;
        }

        int amountDelta = applicableSets * outputCount;
        cbis.count += takeOrdersAway ? -amountDelta : amountDelta;

        for (BigItemStack requirement : data.fluidlogistics$getCustomRequirements()) {
            int delta = requirement.count * applicableSets;
            BigItemStack existingOrder = getOrderForItem(requirement.stack);

            if (takeOrdersAway) {
                if (existingOrder == null) {
                    continue;
                }
                existingOrder.count -= delta;
                if (existingOrder.count <= 0) {
                    itemsToOrder.remove(existingOrder);
                }
                continue;
            }

            if (existingOrder == null) {
                existingOrder = new BigItemStack(requirement.stack.copyWithCount(1), 0);
                itemsToOrder.add(existingOrder);
            }
            existingOrder.count += delta;
        }

        if (cbis.count <= 0) {
            recipesToOrder.remove(cbis);
        }

        fluidlogistics$updateCraftableAmountsWithCustomEntries();
    }
}
