package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

public class SmartMultiFluidTank implements IFluidHandler, IFluidTank, SharedCapacityFluidHandler {

    private final Consumer<FluidStack[]> updateCallback;
    private final int tanks;
    protected int capacity;
    protected Predicate<FluidStack> validator;

    @NotNull
    protected FluidStack[] multiFluid;

    public SmartMultiFluidTank(int capacity, int tanks, Consumer<FluidStack[]> updateCallback) {
        this.capacity = capacity;
        this.validator = stack -> true;
        this.updateCallback = updateCallback;
        this.tanks = tanks;
        this.multiFluid = new FluidStack[tanks];
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
            amount += multiFluid[i].getAmount();
        }
        return amount;
    }

    public int getFluidAmount(int tank) {
        return tank >= 0 && tank < getTanks() ? multiFluid[tank].getAmount() : 0;
    }

    public void resetTanks() {
        for (int i = 0; i < getTanks(); i++) {
            multiFluid[i] = FluidStack.EMPTY;
        }
    }

    protected void onContentsChanged() {
        updateCallback.accept(getFluids());
    }

    public void setFluid(FluidStack stack) {
        for (int i = 1; i < tanks; i++) {
            multiFluid[i] = FluidStack.EMPTY;
        }
        setFluid(0, stack);
    }

    public void setFluid(int tank, FluidStack stack) {
        if (tank >= 0 && tank < tanks) {
            multiFluid[tank] = stack.copy();
            updateCallback.accept(getFluids());
        }
    }

    public SmartMultiFluidTank load(CompoundTag nbt) {
        resetTanks();
        for (int i = 0; i < getTanks(); i++) {
            if (!nbt.contains(Integer.toString(i), Tag.TAG_COMPOUND)) {
                continue;
            }
            multiFluid[i] = FluidStack.loadFluidStackFromNBT(nbt.getCompound(Integer.toString(i)));
        }
        return this;
    }

    public CompoundTag save(CompoundTag nbt) {
        for (int i = 0; i < getTanks(); i++) {
            if (multiFluid[i].isEmpty()) {
                continue;
            }
            nbt.put(Integer.toString(i), multiFluid[i].writeToNBT(new CompoundTag()));
        }
        return nbt;
    }

    @Override
    public int getTanks() {
        return tanks;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        return tank >= 0 && tank < getTanks() ? multiFluid[tank] : FluidStack.EMPTY;
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
            if (multiFluid[i].isEmpty() && firstEmpty < 0) {
                firstEmpty = i;
            }
            if (sameFluid(multiFluid[i], resource)) {
                return OptionalInt.of(i);
            }
        }
        return firstEmpty >= 0 ? OptionalInt.of(firstEmpty) : OptionalInt.empty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !isFluidValid(resource) || getSpace() == 0) {
            return 0;
        }

        OptionalInt targetTankOpt = getFirstAvailableTank(resource);
        if (targetTankOpt.isEmpty()) {
            return 0;
        }

        int fillAmount = Math.min(getSpace(), resource.getAmount());
        if (action.simulate()) {
            return fillAmount;
        }

        int targetTank = targetTankOpt.getAsInt();
        if (multiFluid[targetTank].isEmpty()) {
            multiFluid[targetTank] = copyWithAmount(resource, fillAmount);
        } else {
            multiFluid[targetTank].grow(fillAmount);
        }

        if (fillAmount > 0) {
            onContentsChanged();
        }
        return fillAmount;
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }

        for (int i = 0; i < getTanks(); i++) {
            if (!sameFluid(multiFluid[i], resource)) {
                continue;
            }

            int drained = Math.min(multiFluid[i].getAmount(), resource.getAmount());
            FluidStack stack = copyWithAmount(multiFluid[i], drained);
            if (action.execute() && drained > 0) {
                multiFluid[i].shrink(drained);
                if (multiFluid[i].getAmount() <= 0) {
                    multiFluid[i] = FluidStack.EMPTY;
                }
                onContentsChanged();
            }
            return stack;
        }

        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        for (int i = 0; i < getTanks(); i++) {
            if (multiFluid[i].isEmpty()) {
                continue;
            }

            int drained = Math.min(maxDrain, multiFluid[i].getAmount());
            FluidStack stack = copyWithAmount(multiFluid[i], drained);
            if (action.execute() && drained > 0) {
                multiFluid[i].shrink(drained);
                if (multiFluid[i].getAmount() <= 0) {
                    multiFluid[i] = FluidStack.EMPTY;
                }
                onContentsChanged();
            }
            return stack;
        }

        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack getFluid() {
        for (int i = 0; i < getTanks(); i++) {
            if (!multiFluid[i].isEmpty()) {
                return multiFluid[i];
            }
        }
        return FluidStack.EMPTY;
    }

    public @NotNull FluidStack[] getFluids() {
        return multiFluid;
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
            if (!multiFluid[i].isEmpty()) {
                return false;
            }
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
            simulatedFluids[i] = multiFluid[i].copy();
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
                if (sameFluid(simulatedFluids[i], fluid)) {
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

    private static boolean sameFluid(FluidStack first, FluidStack second) {
        return first.isFluidEqual(second) && FluidStack.areFluidStackTagsEqual(first, second);
    }

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }
}
