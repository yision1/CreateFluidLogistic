package com.yision.fluidlogistics.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.IFluidTank;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SmartMultiFluidTank implements IFluidHandler, IFluidTank, SharedCapacityFluidHandler {

    private final Consumer<FluidStack[]> updateCallback;
    private final int tanks;
    protected int capacity;
    protected Predicate<FluidStack> validator;

    @NotNull
    protected FluidStack[] multi_fluid;

    public SmartMultiFluidTank(int capacity, int tanks, Consumer<FluidStack[]> updateCallback) {
        this.capacity = capacity;
        this.validator = e -> true;
        this.updateCallback = updateCallback;
        this.tanks = tanks;
        multi_fluid = new FluidStack[tanks];
        resetTanks();
    }

    public SmartMultiFluidTank setCapacity(int capacity) {
        this.capacity = capacity;
        return this;
    }

    public SmartMultiFluidTank setValidator(Predicate<FluidStack> validator) {
        if (validator != null) {
            this.validator = validator;
        }
        return this;
    }

    @Override
    public int getFluidAmount() {
        int amount = 0;
        for (int i = 0; i < getTanks(); i++) {
            amount += multi_fluid[i].getAmount();
        }
        return amount;
    }

    public int getFluidAmount(int tank) {
        if (tank < getTanks())
            return multi_fluid[tank].getAmount();

        return 0;
    }

    public void resetTanks() {
        for (int i = 0; i < getTanks(); i++) {
            multi_fluid[i] = FluidStack.EMPTY;
        }
    }

    protected void onContentsChanged() {
        updateCallback.accept(getFluids());
    }

    public void setFluid(FluidStack stack) {
        for (int i = 1; i < tanks; i++) {
            multi_fluid[i] = FluidStack.EMPTY;
        }
        setFluid(0, stack);
    }

    public void setFluid(int tank, FluidStack stack) {
        if (tank < this.tanks) {
            multi_fluid[tank] = stack;
            updateCallback.accept(getFluids());
        }
    }

    public SmartMultiFluidTank load(HolderLookup.Provider registries, CompoundTag nbt) {
        resetTanks();
        for (int i = 0; i < getTanks(); i++) {
            if (!nbt.contains(Integer.toString(i), Tag.TAG_COMPOUND))
                continue;
            CompoundTag fluidNbt = nbt.getCompound(Integer.toString(i));
            FluidStack fluid = FluidStack.parseOptional(registries, fluidNbt);
            multi_fluid[i] = fluid;
        }
        return this;
    }

    public CompoundTag save(HolderLookup.Provider registries, CompoundTag nbt) {
        for (int i = 0; i < getTanks(); i++) {
            if (multi_fluid[i].isEmpty())
                continue;
            Tag fluidNbt = multi_fluid[i].save(registries);
            nbt.put(Integer.toString(i), fluidNbt);
        }
        return nbt;
    }

    @Override
    public int getTanks() {
        return tanks;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        if (tank < getTanks()) {
            return multi_fluid[tank];
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return isFluidValid(stack);
    }

    OptionalInt getFirstAvailableTank(FluidStack resource) {
        int firstEmpty = -1;
        for (int i = 0; i < getTanks(); i++) {
            if (multi_fluid[i].isEmpty() && firstEmpty < 0)
                    firstEmpty = i;

            if (FluidStack.isSameFluidSameComponents(multi_fluid[i], resource))
                return OptionalInt.of(i);
        }
        if (firstEmpty >= 0)
            return OptionalInt.of(firstEmpty);
        return OptionalInt.empty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isFluidValid(resource)) {
            return 0;
        }
        if (getSpace() == 0)
            return 0;

        OptionalInt targetTankOpt = getFirstAvailableTank(resource);
        if (action.simulate()) {
            if (targetTankOpt.isEmpty()) return 0;
            return Math.min(getSpace(), resource.getAmount());
        }

        int targetTank = targetTankOpt.getAsInt();

        if (multi_fluid[targetTank].isEmpty()) {
            multi_fluid[targetTank] = resource.copyWithAmount(Math.min(getSpace(), resource.getAmount()));
            onContentsChanged();
            return multi_fluid[targetTank].getAmount();
        }

        int filled = getSpace();

        if (resource.getAmount() < filled) {
            multi_fluid[targetTank].grow(resource.getAmount());
            filled = resource.getAmount();
        } else {
            multi_fluid[targetTank].grow(filled);
        }
        if (filled > 0)
            onContentsChanged();
        return filled;
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        OptionalInt targetTankOpt = getFirstAvailableTank(resource);
        if (targetTankOpt.isEmpty()) return FluidStack.EMPTY;
        int targetTank = targetTankOpt.getAsInt();

        if (multi_fluid[targetTank].isEmpty()) return FluidStack.EMPTY;

        int drained = Math.min(multi_fluid[targetTank].getAmount(), resource.getAmount());
        FluidStack stack = multi_fluid[targetTank].copyWithAmount(drained);

        if (action.execute() && drained > 0) {
            multi_fluid[targetTank].shrink(drained);
            onContentsChanged();
        }
        return stack;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        for (int i = 0; i < getTanks(); i++) {
            if (!multi_fluid[i].isEmpty()) {
                int drained = Math.min(maxDrain, multi_fluid[i].getAmount());
                FluidStack stack = multi_fluid[i].copyWithAmount(drained);
                if (action.execute() && drained > 0) {
                    multi_fluid[i].shrink(drained);
                    onContentsChanged();
                }
                return stack;
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack getFluid() {
        for (int i = 0; i < getTanks(); i++) {
            if (!multi_fluid[i].isEmpty()) return multi_fluid[i];
        }
        return FluidStack.EMPTY;
    }

    public @NotNull FluidStack[] getFluids() {
        return multi_fluid;
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public boolean isFluidValid(FluidStack stack) {
        return validator.test(stack);
    }

    public boolean isEmpty() {
        for (int i = 0; i < getTanks(); i++) {
            if (!multi_fluid[i].isEmpty()) return false;
        }
        return true;
    }

    public int getSpace() {
        return Math.max(0, capacity - getFluidAmount());
    }

    @Override
    public boolean canFillAll(List<FluidStack> fluids) {
        if (fluids.isEmpty()) {
            return true;
        }

        FluidStack[] simulatedFluids = new FluidStack[tanks];
        int totalAmount = 0;
        for (int i = 0; i < tanks; i++) {
            simulatedFluids[i] = multi_fluid[i].copy();
            totalAmount += simulatedFluids[i].getAmount();
        }

        for (FluidStack fluid : fluids) {
            if (fluid.isEmpty() || !isFluidValid(fluid)) {
                return false;
            }

            if (totalAmount + fluid.getAmount() > capacity) {
                return false;
            }

            int targetTank = -1;
            for (int i = 0; i < tanks; i++) {
                if (FluidStack.isSameFluidSameComponents(simulatedFluids[i], fluid)) {
                    targetTank = i;
                    break;
                }
                if (targetTank == -1 && simulatedFluids[i].isEmpty()) {
                    targetTank = i;
                }
            }

            if (targetTank == -1) {
                return false;
            }

            if (simulatedFluids[targetTank].isEmpty()) {
                simulatedFluids[targetTank] = fluid.copy();
            } else {
                simulatedFluids[targetTank].grow(fluid.getAmount());
            }
            totalAmount += fluid.getAmount();
        }

        return true;
    }
}
