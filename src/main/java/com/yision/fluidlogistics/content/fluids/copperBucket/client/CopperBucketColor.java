package com.yision.fluidlogistics.content.fluids.copperBucket.client;

import com.yision.fluidlogistics.registry.AllDataComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

public class CopperBucketColor implements ItemColor {

    private static final int FLUID_TINT_INDEX = 1;

    @Override
    public int getColor(ItemStack stack, int tintIndex) {
        FluidStack fluid = stack
                .getOrDefault(AllDataComponents.COPPER_BUCKET_CONTENT, SimpleFluidContent.EMPTY)
                .copy();
        if (fluid.isEmpty()) {
            return 0xFFFFFFFF;
        }

        if (tintIndex == FLUID_TINT_INDEX) {
            return IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
        }

        Item bucketItem = fluid.getFluid().getBucket();
        if (bucketItem != Items.AIR && bucketItem != stack.getItem()) {
            int bucketColor = Minecraft.getInstance().getItemColors()
                    .getColor(new ItemStack(bucketItem), tintIndex);
            if (bucketColor != -1) {
                return bucketColor;
            }
        }
        return 0xFFFFFFFF;
    }
}
