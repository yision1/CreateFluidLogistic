package com.yision.fluidlogistics.content.logistics.fluidPackage;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.registry.AllItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.items.ItemStackHandler;

public final class FluidPackageContentHelper {
    public static final int PACKAGE_DRAIN_AMOUNT = FluidType.BUCKET_VOLUME;

    private FluidPackageContentHelper() {
    }

    public static FluidStack getSingleContainedFluid(ItemStack packageStack) {
        if (packageStack == null || !PackageItem.isPackage(packageStack)) {
            return FluidStack.EMPTY;
        }

        ItemStackHandler contents = readRawContents(packageStack);
        if (!isCanonicalContents(contents, Config.getFluidPerPackage())) {
            return FluidStack.EMPTY;
        }
        return CompressedTankItem.getFluid(contents.getStackInSlot(0)).copy();
    }

    public static boolean isCanonicalPackage(ItemStack packageStack) {
        return packageStack != null && PackageItem.isPackage(packageStack)
            && isCanonicalContents(readRawContents(packageStack), Config.getFluidPerPackage());
    }

    public static ItemStackHandler readRawContents(ItemStack packageStack) {
        return PackageItem.getContents(packageStack);
    }

    public static boolean isCanonicalContents(ItemStackHandler contents, int capacity) {
        if (contents == null || contents.getSlots() != PackageItem.SLOTS) {
            return false;
        }

        ItemStack tank = contents.getStackInSlot(0);
        if (tank.getCount() != 1 || !CompressedTankItem.isFluidStack(tank)) {
            return false;
        }
        if (!CompressedTankRules.isStoredFluidAmountValid(CompressedTankItem.getFluid(tank).getAmount(), capacity)) {
            return false;
        }
        for (int slot = 1; slot < contents.getSlots(); slot++) {
            if (!contents.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static ItemStackHandler createCanonicalContents(FluidStack fluid) {
        int capacity = Config.getFluidPerPackage();
        if (fluid.isEmpty() || !CompressedTankRules.isStoredFluidAmountValid(fluid.getAmount(), capacity)) {
            throw new IllegalArgumentException("fluid amount must be between 1 and " + capacity + " mB");
        }

        ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
        ItemStack tank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(tank, fluid);
        contents.setStackInSlot(0, tank);
        return contents;
    }

    public static ItemStack createCanonicalPackage(FluidStack fluid) {
        ItemStack packageStack = AllItems.createFluidPackage();
        setCanonicalContents(packageStack, fluid);
        return packageStack;
    }

    public static void setCanonicalContents(ItemStack packageStack, FluidStack fluid) {
        CompoundTag tag = packageStack.getOrCreateTag();
        tag.put("Items", createCanonicalContents(fluid).serializeNBT());
    }

    public static FluidStack peekDrainOneBucket(ItemStack packageStack) {
        FluidStack contained = getSingleContainedFluid(packageStack);
        if (contained.isEmpty() || contained.getAmount() < PACKAGE_DRAIN_AMOUNT) {
            return FluidStack.EMPTY;
        }
        return FluidHelper.copyStackWithAmount(contained, PACKAGE_DRAIN_AMOUNT);
    }

    public static FluidStack drainOneBucket(ItemStack packageStack, boolean simulate) {
        FluidStack contained = getSingleContainedFluid(packageStack);
        if (contained.isEmpty() || contained.getAmount() < PACKAGE_DRAIN_AMOUNT) {
            return FluidStack.EMPTY;
        }

        FluidStack drained = FluidHelper.copyStackWithAmount(contained, PACKAGE_DRAIN_AMOUNT);
        if (!simulate) {
            int remaining = contained.getAmount() - PACKAGE_DRAIN_AMOUNT;
            if (remaining <= 0) {
                packageStack.shrink(1);
            } else {
                setCanonicalContents(packageStack, FluidHelper.copyStackWithAmount(contained, remaining));
            }
        }
        return drained;
    }
}
