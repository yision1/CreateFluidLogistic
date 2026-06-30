package com.yision.fluidlogistics.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

import com.yision.fluidlogistics.item.CompressedTankItem;

import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
final class VirtualFluidTankRecipeLookupPlugin implements IRecipeManagerPlugin {

    private final IFocusFactory focusFactory;
    private final Supplier<IJeiRuntime> runtimeSupplier;

    VirtualFluidTankRecipeLookupPlugin(IJeiHelpers jeiHelpers, Supplier<IJeiRuntime> runtimeSupplier) {
        this.focusFactory = jeiHelpers.getFocusFactory();
        this.runtimeSupplier = runtimeSupplier;
    }

    @Override
    public <V> List<RecipeType<?>> getRecipeTypes(IFocus<V> focus) {
        Optional<IFocus<FluidStack>> fluidFocus = createFluidFocus(focus);
        if (fluidFocus.isEmpty()) {
            return List.of();
        }

        IJeiRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            return List.of();
        }

        List<RecipeType<?>> recipeTypes = new ArrayList<>();
        runtime.getRecipeManager()
            .createRecipeCategoryLookup()
            .limitFocus(List.of(fluidFocus.get()))
            .get()
            .forEach(category -> recipeTypes.add(category.getRecipeType()));
        return recipeTypes;
    }

    @Override
    public <T, V> List<T> getRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
        Optional<IFocus<FluidStack>> fluidFocus = createFluidFocus(focus);
        if (fluidFocus.isEmpty()) {
            return List.of();
        }

        IJeiRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            return List.of();
        }

        return runtime.getRecipeManager()
            .createRecipeLookup(recipeCategory.getRecipeType())
            .limitFocus(List.of(fluidFocus.get()))
            .get()
            .toList();
    }

    @Override
    public <T> List<T> getRecipes(IRecipeCategory<T> recipeCategory) {
        return List.of();
    }

    private Optional<IFocus<FluidStack>> createFluidFocus(IFocus<?> focus) {
        Optional<ItemStack> itemStack = focus.getTypedValue().getItemStack();
        if (itemStack.isEmpty()) {
            return Optional.empty();
        }

        ItemStack stack = itemStack.get();
        if (!(stack.getItem() instanceof CompressedTankItem) || !CompressedTankItem.isVirtual(stack)) {
            return Optional.empty();
        }

        FluidStack fluid = CompressedTankItem.getFluid(stack);
        if (fluid.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(focusFactory.createFocus(focus.getRole(), NeoForgeTypes.FLUID_STACK, fluid.copy()));
    }
}
