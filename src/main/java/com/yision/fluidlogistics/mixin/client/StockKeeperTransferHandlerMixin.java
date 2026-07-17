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
import com.yision.fluidlogistics.api.packager.PackageResourceCrafting;
import com.yision.fluidlogistics.api.packager.PackageResourceCraftingData;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.content.logistics.stockTicker.FluidCraftableBigItemStack;
import com.yision.fluidlogistics.registry.AllItems;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.common.transfer.RecipeTransferErrorInternal;
import mezz.jei.library.transfer.RecipeTransferErrorTooltip;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

@Mixin(StockKeeperTransferHandler.class)
public abstract class StockKeeperTransferHandlerMixin {

    @Inject(method = "transferRecipeOnClient", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$handleFluidRecipes(StockKeeperRequestMenu container, RecipeHolder<Recipe<?>> recipeHolder,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer,
            CallbackInfoReturnable<@Nullable IRecipeTransferError> cir) {
        if (!fluidlogistics$hasFluidIngredients(recipeSlots)) {
            return;
        }

        if (!(container.screenReference instanceof StockKeeperRequestScreen screen)) {
            cir.setReturnValue(RecipeTransferErrorInternal.INSTANCE);
            return;
        }

        InventorySummary summary = screen.getMenu().contentHolder.getLastClientsideStockSnapshotAsSummary();
        if (summary == null) {
            cir.setReturnValue(RecipeTransferErrorInternal.INSTANCE);
            return;
        }

        List<BigItemStack> selectedRequirements = fluidlogistics$selectRequirements(recipeSlots, summary, screen.itemsToOrder);
        if (selectedRequirements.isEmpty()) {
            cir.setReturnValue(new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.not_in_stock").component()));
            return;
        }

        if (!doTransfer) {
            cir.setReturnValue(null);
            return;
        }

        Recipe<?> recipe = recipeHolder.value();
        OutputTarget outputTarget = fluidlogistics$getOutputTarget(recipeSlots, player, recipe);
        if (outputTarget == null) {
            cir.setReturnValue(new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.recipe_result_empty").component()));
            return;
        }

        if (fluidlogistics$hasRecipeEntry(screen, recipe)) {
            cir.setReturnValue(new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.already_ordering_recipe").component()));
            return;
        }

        if (!fluidlogistics$canFitNewTypes(screen.itemsToOrder, selectedRequirements)) {
            cir.setReturnValue(new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.slots_full").component()));
            return;
        }

        int requestedSets = maxTransfer
            ? Math.max(1, Mth.ceil(outputTarget.transferLimit() / (float) outputTarget.outputCount()))
            : 1;
        int craftableSets = fluidlogistics$getCraftableSets(summary, screen.itemsToOrder, selectedRequirements);
        int setsToAdd = Math.min(requestedSets, craftableSets);
        if (setsToAdd <= 0) {
            cir.setReturnValue(new RecipeTransferErrorTooltip(CreateLang.translate("gui.stock_keeper.not_in_stock").component()));
            return;
        }

        CraftableBigItemStack craftableEntry = new FluidCraftableBigItemStack(outputTarget.displayStack().copy(), recipe);
        PackageResourceCrafting.set(craftableEntry, new PackageResourceCraftingData(
                outputTarget.outputCount(), outputTarget.transferLimit(), selectedRequirements));
        screen.recipesToOrder.add(craftableEntry);
        screen.requestCraftable(craftableEntry, outputTarget.outputCount() * setsToAdd);

        screen.searchBox.setValue("");
        screen.refreshSearchNextTick = true;
        cir.setReturnValue(null);
    }

    @Unique
    private static boolean fluidlogistics$hasFluidIngredients(IRecipeSlotsView recipeSlots) {
        return fluidlogistics$hasFluidIngredients(recipeSlots, RecipeIngredientRole.INPUT)
            || fluidlogistics$hasFluidIngredients(recipeSlots, RecipeIngredientRole.OUTPUT);
    }

    @Unique
    private static boolean fluidlogistics$hasFluidIngredients(IRecipeSlotsView recipeSlots, RecipeIngredientRole role) {
        for (IRecipeSlotView slotView : recipeSlots.getSlotViews(role)) {
            if (slotView.getIngredients(NeoForgeTypes.FLUID_STACK).anyMatch(fluid -> !fluid.isEmpty())) {
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

            Optional<FluidStack> fluidOutput = slotView.getIngredients(NeoForgeTypes.FLUID_STACK)
                .filter(fluid -> !fluid.isEmpty())
                .findFirst();
            if (fluidOutput.isPresent()) {
                ItemStack fluidTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
                CompressedTankItem.setFluid(fluidTank, fluidOutput.get().copyWithAmount(1));
                return new OutputTarget(fluidTank, Math.max(1, fluidOutput.get().getAmount()), Config.getFluidPerPackage());
            }
        }

        ItemStack result = recipe.getResultItem(player.level().registryAccess());
        if (result.isEmpty()) {
            return null;
        }

        return new OutputTarget(result.copyWithCount(1), Math.max(1, result.getCount()), result.getMaxStackSize());
    }

    @Unique
    private static List<BigItemStack> fluidlogistics$selectRequirements(IRecipeSlotsView recipeSlots, @Nullable InventorySummary summary,
            @Nullable List<BigItemStack> existingOrders) {
        List<BigItemStack> selectedRequirements = new ArrayList<>();

        for (IRecipeSlotView slotView : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<BigItemStack> candidates = fluidlogistics$getCandidates(slotView);
            if (candidates.isEmpty()) {
                continue;
            }

            if (summary == null) {
                fluidlogistics$mergeRequirement(selectedRequirements, candidates.getFirst());
                continue;
            }

            BigItemStack chosen = fluidlogistics$chooseBestCandidate(candidates, summary, selectedRequirements, existingOrders);
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

        slotView.getIngredients(NeoForgeTypes.FLUID_STACK).forEach(fluid -> {
            if (fluid.isEmpty()) {
                return;
            }

            ItemStack fluidTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluid(fluidTank, fluid.copyWithAmount(1));
            candidates.add(new BigItemStack(fluidTank, fluid.getAmount()));
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
    private static boolean fluidlogistics$canFitNewTypes(List<BigItemStack> existingOrders, List<BigItemStack> requirements) {
        int totalTypes = existingOrders.size();
        List<ItemStack> newTypes = new ArrayList<>();

        for (BigItemStack requirement : requirements) {
            if (fluidlogistics$hasMatchingStack(existingOrders, requirement.stack) || fluidlogistics$hasMatchingStack(newTypes, requirement.stack)) {
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
            if (ItemStack.isSameItemSameComponents(entry.stack, target)) {
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
            if (ItemStack.isSameItemSameComponents(entry.stack, target)) {
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
            if (ItemStack.isSameItemSameComponents(stack, target)) {
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
    private record OutputTarget(ItemStack displayStack, int outputCount, int transferLimit) {
    }
}
