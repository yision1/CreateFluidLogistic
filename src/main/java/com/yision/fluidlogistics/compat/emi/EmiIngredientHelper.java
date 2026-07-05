package com.yision.fluidlogistics.compat.emi;

import javax.annotation.Nullable;

import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

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
                CompoundTag tag = stack.getNbt() == null ? null : stack.getNbt().copy();
                return new FluidStack(fluid, 1, tag);
            }
        }
        return null;
    }

    @Nullable
    public static ItemStack toGhostStack(EmiIngredient ingredient) {
        FluidStack fluid = getFirstFluid(ingredient);
        if (fluid != null) {
            ItemStack tankStack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluidVirtual(tankStack, fluid.copy());
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
