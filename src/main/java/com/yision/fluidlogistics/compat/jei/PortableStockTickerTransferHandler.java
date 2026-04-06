package com.yision.fluidlogistics.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.portableticker.PortableStockTickerMenu;
import com.yision.fluidlogistics.portableticker.PortableStockTickerScreen;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.registry.AllMenuTypes;
import com.yision.fluidlogistics.util.IFluidCraftableBigItemStack;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.common.transfer.RecipeTransferErrorInternal;
import mezz.jei.library.transfer.RecipeTransferErrorTooltip;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

public class PortableStockTickerTransferHandler implements IUniversalRecipeTransferHandler<PortableStockTickerMenu> {

    @Override
    public Class<? extends PortableStockTickerMenu> getContainerClass() {
        return PortableStockTickerMenu.class;
    }

    @Override
    public Optional<MenuType<PortableStockTickerMenu>> getMenuType() {
        return Optional.of(AllMenuTypes.PORTABLE_STOCK_TICKER.get());
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(PortableStockTickerMenu container, Object object,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        Level level = player.level();
        if (!(object instanceof RecipeHolder<?> recipe)) {
            return null;
        }

        MutableObject<IRecipeTransferError> result = new MutableObject<>();
        if (level.isClientSide()) {
            CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> result
                    .setValue(transferRecipeOnClient(container, (RecipeHolder<Recipe<?>>) recipe, recipeSlots, player,
                            maxTransfer, doTransfer)));
        }
        return result.getValue();
    }

    private @Nullable IRecipeTransferError transferRecipeOnClient(PortableStockTickerMenu container,
            RecipeHolder<Recipe<?>> recipeHolder, IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer,
            boolean doTransfer) {
        if (!(container.screenReference instanceof PortableStockTickerScreen screen)) {
            return RecipeTransferErrorInternal.INSTANCE;
        }

        Recipe<?> recipe = recipeHolder.value();

        if (recipe.getIngredients().size() > 9)
            return RecipeTransferErrorInternal.INSTANCE;

        if (screen.hasRecipeEntry(recipe)) {
            return new RecipeTransferErrorTooltip(
                    CreateLang.translate("gui.stock_keeper.already_ordering_recipe").component());
        }

        InventorySummary summary = screen.stockSnapshot();

        List<BigItemStack> selectedRequirements = selectRequirements(recipeSlots, summary, screen.itemsToOrder());
        if (selectedRequirements.isEmpty()) {
            return new RecipeTransferErrorTooltip(
                    CreateLang.translate("gui.stock_keeper.not_in_stock").component());
        }

        boolean hasFluidIngredients = hasFluidIngredients(recipeSlots);
        OutputTarget outputTarget = getOutputTarget(recipeSlots, player, recipe, hasFluidIngredients);
        if (outputTarget == null) {
            return new RecipeTransferErrorTooltip(
                    CreateLang.translate("gui.stock_keeper.recipe_result_empty").component());
        }

        if (!canFitNewTypes(screen.itemsToOrder(), selectedRequirements)) {
            return new RecipeTransferErrorTooltip(
                    CreateLang.translate("gui.stock_keeper.slots_full").component());
        }

        int requestedSets = maxTransfer
                ? Math.max(1, Mth.ceil(outputTarget.transferLimit() / (float) outputTarget.outputCount()))
                : 1;
        int craftableSets = getCraftableSets(summary, screen.itemsToOrder(), selectedRequirements);
        int setsToAdd = Math.min(requestedSets, craftableSets);
        if (setsToAdd <= 0) {
            return new RecipeTransferErrorTooltip(
                    CreateLang.translate("gui.stock_keeper.not_in_stock").component());
        }

        if (!doTransfer) {
            return null;
        }

        CraftableBigItemStack craftableEntry = new CraftableBigItemStack(outputTarget.displayStack().copy(), recipe);
        if (outputTarget.customRecipeData()) {
            ((IFluidCraftableBigItemStack) craftableEntry).fluidlogistics$setCustomRecipeData(
                    outputTarget.outputCount(), outputTarget.transferLimit(), selectedRequirements);
        }

        screen.recipesToOrder().add(craftableEntry);
        screen.clearSearchAndRefresh();
        screen.requestCraftable(craftableEntry, outputTarget.outputCount() * setsToAdd);
        return null;
    }

    private static List<BigItemStack> selectRequirements(IRecipeSlotsView recipeSlots, InventorySummary summary,
            List<BigItemStack> existingOrders) {
        List<BigItemStack> selectedRequirements = new ArrayList<>();

        for (IRecipeSlotView slotView : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<BigItemStack> candidates = getCandidates(slotView);
            if (candidates.isEmpty()) {
                continue;
            }

            BigItemStack chosen = chooseBestCandidate(candidates, summary, selectedRequirements, existingOrders);
            if (chosen == null) {
                return List.of();
            }

            mergeRequirement(selectedRequirements, chosen);
        }

        return selectedRequirements;
    }

    private static boolean hasFluidIngredients(IRecipeSlotsView recipeSlots) {
        return hasFluidIngredients(recipeSlots, RecipeIngredientRole.INPUT)
                || hasFluidIngredients(recipeSlots, RecipeIngredientRole.OUTPUT);
    }

    private static boolean hasFluidIngredients(IRecipeSlotsView recipeSlots, RecipeIngredientRole role) {
        for (IRecipeSlotView slotView : recipeSlots.getSlotViews(role)) {
            if (slotView.getIngredients(NeoForgeTypes.FLUID_STACK).anyMatch(fluid -> !fluid.isEmpty())) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable OutputTarget getOutputTarget(IRecipeSlotsView recipeSlots, Player player, Recipe<?> recipe,
            boolean customRecipeData) {
        for (IRecipeSlotView slotView : recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            Optional<ItemStack> itemOutput = slotView.getItemStacks().filter(stack -> !stack.isEmpty()).findFirst();
            if (itemOutput.isPresent()) {
                ItemStack stack = itemOutput.get();
                return new OutputTarget(stack.copyWithCount(1), Math.max(1, stack.getCount()), stack.getMaxStackSize(),
                        customRecipeData);
            }

            Optional<FluidStack> fluidOutput = slotView.getIngredients(NeoForgeTypes.FLUID_STACK)
                    .filter(fluid -> !fluid.isEmpty())
                    .findFirst();
            if (fluidOutput.isPresent()) {
                ItemStack virtualTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
                CompressedTankItem.setFluidVirtual(virtualTank, fluidOutput.get().copyWithAmount(1));
                return new OutputTarget(virtualTank, Math.max(1, fluidOutput.get().getAmount()),
                        Config.getFluidPerPackage(), true);
            }
        }

        ItemStack result = recipe.getResultItem(player.level().registryAccess());
        if (result.isEmpty()) {
            return null;
        }

        return new OutputTarget(result.copyWithCount(1), Math.max(1, result.getCount()), result.getMaxStackSize(),
                customRecipeData);
    }

    private static List<BigItemStack> getCandidates(IRecipeSlotView slotView) {
        List<BigItemStack> candidates = new ArrayList<>();

        slotView.getItemStacks().forEach(stack -> {
            if (!stack.isEmpty()) {
                candidates.add(new BigItemStack(stack.copyWithCount(1), Math.max(1, stack.getCount())));
            }
        });

        slotView.getIngredients(NeoForgeTypes.FLUID_STACK).forEach(fluid -> {
            if (fluid.isEmpty()) {
                return;
            }
            ItemStack virtualTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluidVirtual(virtualTank, fluid.copyWithAmount(1));
            candidates.add(new BigItemStack(virtualTank, Math.max(1, fluid.getAmount())));
        });

        return candidates;
    }

    private static @Nullable BigItemStack chooseBestCandidate(List<BigItemStack> candidates, InventorySummary summary,
            List<BigItemStack> selectedRequirements, List<BigItemStack> existingOrders) {
        BigItemStack best = null;
        int bestAvailable = -1;
        boolean bestPrefersExisting = false;

        for (BigItemStack candidate : candidates) {
            int alreadySelected = getMatchingCount(selectedRequirements, candidate.stack);
            int available = summary.getCountOf(candidate.stack) - alreadySelected;
            if (available < candidate.count) {
                continue;
            }

            boolean prefersExisting = hasMatchingStack(selectedRequirements, candidate.stack)
                    || hasMatchingStack(existingOrders, candidate.stack);
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

    private static int getCraftableSets(InventorySummary summary, List<BigItemStack> existingOrders,
            List<BigItemStack> requirements) {
        int craftableSets = Integer.MAX_VALUE;
        for (BigItemStack requirement : requirements) {
            int orderedCount = getMatchingCount(existingOrders, requirement.stack);
            int available = summary.getCountOf(requirement.stack) - orderedCount;
            craftableSets = Math.min(craftableSets, available / requirement.count);
        }
        return craftableSets == Integer.MAX_VALUE ? 0 : Math.max(0, craftableSets);
    }

    private static boolean canFitNewTypes(List<BigItemStack> existingOrders, List<BigItemStack> requirements) {
        int totalTypes = existingOrders.size();
        List<ItemStack> newTypes = new ArrayList<>();

        for (BigItemStack requirement : requirements) {
            if (hasMatchingStack(existingOrders, requirement.stack) || hasMatchingStack(newTypes, requirement.stack)) {
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

    private static void mergeRequirement(List<BigItemStack> requirements, BigItemStack candidate) {
        BigItemStack existing = findMatchingOrder(requirements, candidate.stack);
        if (existing == null) {
            requirements.add(new BigItemStack(candidate.stack.copyWithCount(1), candidate.count));
            return;
        }
        existing.count += candidate.count;
    }

    private static @Nullable BigItemStack findMatchingOrder(List<BigItemStack> stacks, ItemStack target) {
        for (BigItemStack entry : stacks) {
            if (ItemStack.isSameItemSameComponents(entry.stack, target)) {
                return entry;
            }
        }
        return null;
    }

    private static int getMatchingCount(List<BigItemStack> stacks, ItemStack target) {
        int total = 0;
        for (BigItemStack entry : stacks) {
            if (ItemStack.isSameItemSameComponents(entry.stack, target)) {
                total += entry.count;
            }
        }
        return total;
    }

    private static boolean hasMatchingStack(List<?> stacks, ItemStack target) {
        for (Object entry : stacks) {
            ItemStack stack = entry instanceof BigItemStack bigItemStack ? bigItemStack.stack : (ItemStack) entry;
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                return true;
            }
        }
        return false;
    }

    private record OutputTarget(ItemStack displayStack, int outputCount, int transferLimit, boolean customRecipeData) {
    }
}
