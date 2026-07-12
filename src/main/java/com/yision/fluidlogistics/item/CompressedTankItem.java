package com.yision.fluidlogistics.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

@Deprecated(forRemoval = false)
public class CompressedTankItem extends com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem {

    public CompressedTankItem(Item.Properties properties) {
        super(properties);
    }

    public static FluidStack getFluid(ItemStack stack) {
        return com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem.getFluid(stack);
    }

    public static void setFluid(ItemStack stack, FluidStack fluid) {
        com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem.setFluid(stack, fluid);
    }

    public static boolean isFluidStack(ItemStack stack) {
        return com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem.isFluidStack(stack);
    }

    public static boolean matchesFluid(ItemStack stack, FluidStack fluid) {
        return com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem.matchesFluid(stack, fluid);
    }

    public static int getCapacity() {
        return com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem.getCapacity();
    }
}
