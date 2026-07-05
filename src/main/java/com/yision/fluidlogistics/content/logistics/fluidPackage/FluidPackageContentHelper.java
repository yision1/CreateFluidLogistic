package com.yision.fluidlogistics.content.logistics.fluidPackage;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.fluid.FluidHelper;
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

        ItemStackHandler contents = PackageItem.getContents(packageStack);
        FluidStack result = FluidStack.EMPTY;

        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack slotStack = contents.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                continue;
            }
            if (!(slotStack.getItem() instanceof CompressedTankItem)) {
                return FluidStack.EMPTY;
            }

            FluidStack fluid = CompressedTankItem.getFluid(slotStack);
            if (fluid.isEmpty()) {
                continue;
            }

            int totalAmount = fluid.getAmount() * slotStack.getCount();
            if (result.isEmpty()) {
                result = FluidHelper.copyStackWithAmount(fluid, totalAmount);
                continue;
            }

            if (!result.isFluidEqual(fluid) || !FluidStack.areFluidStackTagsEqual(result, fluid)) {
                return FluidStack.EMPTY;
            }
            result.grow(totalAmount);
        }

        return result;
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
                writeSingleFluid(packageStack, FluidHelper.copyStackWithAmount(contained, remaining));
            }
        }
        return drained;
    }

    private static void writeSingleFluid(ItemStack packageStack, FluidStack remainingFluid) {
        ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
        int remaining = remainingFluid.getAmount();
        int slot = 0;

        while (remaining > 0 && slot < PackageItem.SLOTS) {
            int amount = Math.min(remaining, CompressedTankItem.getCapacity());
            ItemStack tank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
            CompressedTankItem.setFluid(tank, FluidHelper.copyStackWithAmount(remainingFluid, amount));
            contents.setStackInSlot(slot++, tank);
            remaining -= amount;
        }

        CompoundTag tag = packageStack.getOrCreateTag();
        tag.put("Items", contents.serializeNBT());
        packageStack.setTag(tag);
    }
}
