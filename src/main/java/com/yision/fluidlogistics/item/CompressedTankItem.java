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

    public static void setFluidVirtual(ItemStack stack, FluidStack fluid) {
        com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem.setFluidVirtual(stack, fluid);
    }

    public static boolean isVirtual(ItemStack stack) {
        return com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem.isVirtual(stack);
    }

    public static int getCapacity() {
        return com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem.getCapacity();
    }
}
