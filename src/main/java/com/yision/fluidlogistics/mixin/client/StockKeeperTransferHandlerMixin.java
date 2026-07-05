package com.yision.fluidlogistics.mixin.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.compat.jei.StockKeeperTransferHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestMenu;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.IFluidCraftableBigItemStack;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;

@Mixin(value = StockKeeperTransferHandler.class, remap = false)
public abstract class StockKeeperTransferHandlerMixin {

    @Inject(method = "transferRecipeOnClient", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$handleFluidRecipes(StockKeeperRequestMenu container, Recipe<?> recipe,
        IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer,
        CallbackInfoReturnable<@Nullable IRecipeTransferError> cir) {
        if (!fluidlogistics$hasFluidIngredients(recipeSlots)) {
            return;
        }

        if (!(container.screenReference instanceof StockKeeperRequestScreen screen)) {
            cir.setReturnValue(fluidlogistics$internalError());
            return;
        }

        InventorySummary summary = screen.getMenu().contentHolder.getLastClientsideStockSnapshotAsSummary();
        if (summary == null) {
            cir.setReturnValue(fluidlogistics$internalError());
            return;
        }

        List<BigItemStack> selectedRequirements =
            fluidlogistics$selectRequirements(recipeSlots, summary, screen.itemsToOrder);
        if (selectedRequirements.isEmpty()) {
            cir.setReturnValue(fluidlogistics$userError(CreateLang.translate("gui.stock_keeper.not_in_stock").component()));
            return;
        }

        if (!doTransfer) {
            cir.setReturnValue(null);
            return;
        }

        OutputTarget outputTarget = fluidlogistics$getOutputTarget(recipeSlots, player, recipe);
        if (outputTarget == null) {
            cir.setReturnValue(
                fluidlogistics$userError(CreateLang.translate("gui.stock_keeper.recipe_result_empty").component()));
            return;
        }

        if (fluidlogistics$hasRecipeEntry(screen, recipe)) {
            cir.setReturnValue(
                fluidlogistics$userError(CreateLang.translate("gui.stock_keeper.already_ordering_recipe").component()));
            return;
        }

        if (!fluidlogistics$canFitNewTypes(screen.itemsToOrder, selectedRequirements)) {
            cir.setReturnValue(fluidlogistics$userError(CreateLang.translate("gui.stock_keeper.slots_full").component()));
            return;
        }

        int requestedSets = maxTransfer
            ? Math.max(1, Mth.ceil(outputTarget.transferLimit() / (float) outputTarget.outputCount()))
            : 1;
        int craftableSets = fluidlogistics$getCraftableSets(summary, screen.itemsToOrder, selectedRequirements);
        int setsToAdd = Math.min(requestedSets, craftableSets);
        if (setsToAdd <= 0) {
            cir.setReturnValue(fluidlogistics$userError(CreateLang.translate("gui.stock_keeper.not_in_stock").component()));
            return;
        }

        CraftableBigItemStack cbis = new CraftableBigItemStack(outputTarget.displayStack().copy(), recipe);
        ((IFluidCraftableBigItemStack) cbis).fluidlogistics$setCustomRecipeData(
            outputTarget.outputCount(), outputTarget.transferLimit(), selectedRequirements);
        screen.recipesToOrder.add(cbis);
        screen.searchBox.setValue("");
        screen.refreshSearchNextTick = true;
        screen.requestCraftable(cbis, outputTarget.outputCount() * setsToAdd);
        cir.setReturnValue(null);
    }

    @Unique
    private static IRecipeTransferError fluidlogistics$internalError() {
        return InternalTransferError.INSTANCE;
    }

    @Unique
    private static IRecipeTransferError fluidlogistics$userError(Component message) {
        return new TooltipTransferError(message);
    }

    @Unique
    private static boolean fluidlogistics$hasFluidIngredients(IRecipeSlotsView recipeSlots) {
        return fluidlogistics$hasFluidIngredients(recipeSlots, RecipeIngredientRole.INPUT)
            || fluidlogistics$hasFluidIngredients(recipeSlots, RecipeIngredientRole.OUTPUT);
    }

    @Unique
    private static boolean fluidlogistics$hasFluidIngredients(IRecipeSlotsView recipeSlots, RecipeIngredientRole role) {
        for (IRecipeSlotView slotView : recipeSlots.getSlotViews(role)) {
            if (slotView.getIngredients(ForgeTypes.FLUID_STACK).anyMatch(fluid -> !fluid.isEmpty())) {
                return true;
            }
        }
        return false;
    }

    @Unique
    @Nullable
    private static OutputTarget fluidlogistics$getOutputTarget(IRecipeSlotsView recipeSlots, Player player, Recipe<?> recipe) {
        for (IRecipeSlotView slotView : recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            Optional<ItemStack> itemOutput = slotView.getItemStacks()
                .filter(stack -> !stack.isEmpty())
                .findFirst();
            if (itemOutput.isPresent()) {
                ItemStack stack = itemOutput.get();
                return new OutputTarget(stack.copyWithCount(1), Math.max(1, stack.getCount()), stack.getMaxStackSize());
            }

            Optional<FluidStack> fluidOutput = slotView.getIngredients(ForgeTypes.FLUID_STACK)
                .filter(fluid -> !fluid.isEmpty())
                .findFirst();
            if (fluidOutput.isPresent()) {
                ItemStack virtualTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
                FluidStack template = fluidOutput.get().copy();
                template.setAmount(1);
                CompressedTankItem.setFluidVirtual(virtualTank, template);
                return new OutputTarget(virtualTank, Math.max(1, fluidOutput.get().getAmount()), Config.getFluidPerPackage());
            }
        }

        ItemStack result = recipe.getResultItem(player.level().registryAccess());
        if (result.isEmpty()) {
            return null;
        }

        return new OutputTarget(result.copyWithCount(1), Math.max(1, result.getCount()), result.getMaxStackSize());
    }

    @Unique
    private static List<BigItemStack> fluidlogistics$selectRequirements(IRecipeSlotsView recipeSlots,
        @Nullable InventorySummary summary, @Nullable List<BigItemStack> existingOrders) {
        List<BigItemStack> selectedRequirements = new ArrayList<>();

        for (IRecipeSlotView slotView : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<BigItemStack> candidates = fluidlogistics$getCandidates(slotView);
            if (candidates.isEmpty()) {
                continue;
            }

            BigItemStack chosen = summary == null ? candidates.get(0)
                : fluidlogistics$chooseBestCandidate(candidates, summary, selectedRequirements, existingOrders);
            if (chosen == null) {
                return List.of();
            }

            fluidlogistics$mergeRequirement(selectedRequirements, chosen);
        }

        return selectedRequirements;
    }

    @Unique
    private static List<BigItemStack> fluidlogistics$getCandidates(IRecipeSlotView slotView) {
        List<BigItemStack> candidates = new ArrayList<>();

        slotView.getItemStacks().forEach(stack -> {
            if (!stack.isEmpty()) {
                candidates.add(new BigItemStack(stack.copyWithCount(1), stack.getCount()));
            }
        });

        slotView.getIngredients(ForgeTypes.FLUID_STACK).forEach(fluid -> {
            if (fluid.isEmpty()) {
                return;
            }

            ItemStack virtualTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            FluidStack template = fluid.copy();
            template.setAmount(1);
            CompressedTankItem.setFluidVirtual(virtualTank, template);
            candidates.add(new BigItemStack(virtualTank, fluid.getAmount()));
        });

        return candidates;
    }

    @Unique
    @Nullable
    private static BigItemStack fluidlogistics$chooseBestCandidate(List<BigItemStack> candidates, InventorySummary summary,
        List<BigItemStack> selectedRequirements, @Nullable List<BigItemStack> existingOrders) {
        BigItemStack best = null;
        int bestAvailable = -1;
        boolean bestPrefersExisting = false;

        for (BigItemStack candidate : candidates) {
            int alreadySelected = fluidlogistics$getMatchingCount(selectedRequirements, candidate.stack);
            int available = summary.getCountOf(candidate.stack) - alreadySelected;
            if (available < candidate.count) {
                continue;
            }

            boolean prefersExisting = fluidlogistics$hasMatchingStack(selectedRequirements, candidate.stack)
                || fluidlogistics$hasMatchingStack(existingOrders, candidate.stack);

            if (best != null && prefersExisting == bestPrefersExisting && available <= bestAvailable) {
                continue;
            }

            if (best != null && !prefersExisting && bestPrefersExisting) {
                continue;
            }

            best = new BigItemStack(candidate.stack.copyWithCount(1), candidate.count);
            bestAvailable = available;
            bestPrefersExisting = prefersExisting;
        }

        return best;
    }

    @Unique
    private static int fluidlogistics$getCraftableSets(InventorySummary summary, List<BigItemStack> existingOrders,
        List<BigItemStack> requirements) {
        int craftableSets = Integer.MAX_VALUE;
        for (BigItemStack requirement : requirements) {
            int orderedCount = fluidlogistics$getMatchingCount(existingOrders, requirement.stack);
            int available = summary.getCountOf(requirement.stack) - orderedCount;
            craftableSets = Math.min(craftableSets, available / requirement.count);
        }
        return craftableSets == Integer.MAX_VALUE ? 0 : craftableSets;
    }

    @Unique
    private static boolean fluidlogistics$canFitNewTypes(List<BigItemStack> existingOrders,
        List<BigItemStack> requirements) {
        int totalTypes = existingOrders.size();
        List<ItemStack> newTypes = new ArrayList<>();

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
    private static void fluidlogistics$mergeRequirement(List<BigItemStack> requirements, BigItemStack candidate) {
        BigItemStack existing = fluidlogistics$findMatchingOrder(requirements, candidate.stack);
        if (existing == null) {
            requirements.add(new BigItemStack(candidate.stack.copyWithCount(1), candidate.count));
            return;
        }
        existing.count += candidate.count;
    }

    @Unique
    @Nullable
    private static BigItemStack fluidlogistics$findMatchingOrder(List<BigItemStack> stacks, ItemStack target) {
        for (BigItemStack entry : stacks) {
            if (ItemStack.isSameItemSameTags(entry.stack, target)) {
                return entry;
            }
        }
        return null;
    }

    @Unique
    private static int fluidlogistics$getMatchingCount(@Nullable List<BigItemStack> stacks, ItemStack target) {
        if (stacks == null) {
            return 0;
        }

        int total = 0;
        for (BigItemStack entry : stacks) {
            if (ItemStack.isSameItemSameTags(entry.stack, target)) {
                total += entry.count;
            }
        }
        return total;
    }

    @Unique
    private static boolean fluidlogistics$hasMatchingStack(@Nullable List<?> stacks, ItemStack target) {
        if (stacks == null) {
            return false;
        }

        for (Object entry : stacks) {
            ItemStack stack = entry instanceof BigItemStack bigItemStack ? bigItemStack.stack : (ItemStack) entry;
            if (ItemStack.isSameItemSameTags(stack, target)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean fluidlogistics$hasRecipeEntry(StockKeeperRequestScreen screen, Recipe<?> recipe) {
        for (CraftableBigItemStack entry : screen.recipesToOrder) {
            if (entry.recipe == recipe) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private enum InternalTransferError implements IRecipeTransferError {
        INSTANCE;

        @Override
        public Type getType() {
            return Type.INTERNAL;
        }
    }

    @Unique
    private record TooltipTransferError(Component message) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.USER_FACING;
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(message);
        }
    }

    @Unique
    private record OutputTarget(ItemStack displayStack, int outputCount, int transferLimit) {
    }
}
