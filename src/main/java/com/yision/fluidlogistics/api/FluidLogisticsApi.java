package com.yision.fluidlogistics.api;

import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.FluidAmountHelper;
import com.yision.fluidlogistics.util.FluidDisplayHelper;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class FluidLogisticsApi {

    private FluidLogisticsApi() {
    }

    public static boolean isFluidStack(ItemStack stack) {
        return CompressedTankItem.isFluidStack(stack);
    }

    public static ItemStack createFluidKey(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(stack, fluid.copyWithAmount(1));
        return stack;
    }

    public static FluidStack getFluid(ItemStack stack) {
        if (!isFluidStack(stack)) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(stack).copy();
    }

    public static boolean shouldDisplayAsFluidInPackage(ItemStack stack) {
        return FluidDisplayHelper.shouldDisplayAsFluidInPackage(stack);
    }

    public static FluidStack getPackageDisplayFluid(ItemStack stack) {
        return FluidDisplayHelper.getPackageDisplayFluid(stack);
    }

    public static int getPackageFluidAmount(ItemStack stack) {
        IFluidHandler handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return 0;
        }
        int total = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            total = Math.addExact(total, handler.getFluidInTank(tank).getAmount());
        }
        return total;
    }

    public static int getFluidPerPackage() {
        return Config.getFluidPerPackage();
    }

    public static int adjustFluidRequestAmount(int currentAmount, boolean forward, boolean shift,
            boolean control, int minAmount, int maxAmount, int steps) {
        return FluidAmountHelper.adjustFluidRequestAmount(currentAmount, forward, shift, control,
                minAmount, maxAmount, steps);
    }

    public static int adjustFluidRequestAmount(int currentAmount, boolean forward, boolean shift,
            boolean control, int minAmount, int maxAmount) {
        return FluidAmountHelper.adjustFluidRequestAmount(currentAmount, forward, shift, control,
                minAmount, maxAmount);
    }

    public static String formatFluidAmount(int amount) {
        return FluidAmountHelper.formatStockKeeper(amount);
    }
}
