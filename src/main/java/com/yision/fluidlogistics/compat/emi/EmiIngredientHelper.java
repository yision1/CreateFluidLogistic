package com.yision.fluidlogistics.compat.emi;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class EmiIngredientHelper {

    private EmiIngredientHelper() {
    }

    @Nullable
    public static FluidStack getFirstFluid(EmiIngredient ingredient) {
        if (ingredient.isEmpty()) {
            return null;
        }
        for (EmiStack stack : ingredient.getEmiStacks()) {
            if (stack.isEmpty()) {
                continue;
            }
            Object key = stack.getKey();
            if (key instanceof Fluid fluid) {
                return new FluidStack(BuiltInRegistries.FLUID.wrapAsHolder(fluid), 1, stack.getComponentChanges());
            }
        }
        return null;
    }

    @Nullable
    public static ItemStack toGhostStack(EmiIngredient ingredient) {
        FluidStack fluid = getFirstFluid(ingredient);
        if (fluid != null) {
            ItemStack tankStack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluid(tankStack, fluid.copyWithAmount(1));
            return tankStack;
        }

        for (EmiStack stack : ingredient.getEmiStacks()) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack itemStack = stack.getItemStack();
            if (!itemStack.isEmpty()) {
                ItemStack copy = itemStack.copy();
                copy.setCount(1);
                return copy;
            }
        }

        return null;
    }
}
