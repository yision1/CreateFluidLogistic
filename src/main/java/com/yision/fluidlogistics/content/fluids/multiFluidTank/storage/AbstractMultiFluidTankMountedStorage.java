package com.yision.fluidlogistics.content.fluids.multiFluidTank.storage;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public abstract class AbstractMultiFluidTankMountedStorage
        extends WrapperMountedFluidStorage<AbstractMultiFluidTankMountedStorage.Handler>
        implements SyncedMountedStorage {

    private boolean dirty;
    private final int capacity;
    private final int tanks;

    public AbstractMultiFluidTankMountedStorage(MountedFluidStorageType<?> type, int capacity, int tanks,
            List<FluidStack> fluids) {
        super(type, new Handler(capacity, tanks, fluids));
        this.capacity = capacity;
        this.tanks = tanks;
        this.wrapped.onChange = () -> this.dirty = true;
    }

    protected abstract boolean matchesController(BlockEntity be);

    protected abstract void applyMountedState(BlockEntity controller, int tanks, Handler wrapped);

    public List<FluidStack> getFluidsList() {
        List<FluidStack> fluids = new ArrayList<>();
        for (int i = 0; i < wrapped.getTanks(); i++) {
            fluids.add(wrapped.getFluidInTank(i).copy());
        }
        return fluids;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTanks() {
        return tanks;
    }

    @Override
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (matchesController(be)) {
            applyMountedState(be, tanks, wrapped);
        }
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markClean() {
        dirty = false;
    }

    @Override
    public void afterSync(Contraption contraption, BlockPos localPos) {
        BlockEntity be = contraption.getBlockEntityClientSide(localPos);
        if (!matchesController(be)) {
            return;
        }
        applyMountedState(be, wrapped.getTanks(), wrapped);
    }

    public static List<FluidStack> readLegacyFluids(CompoundTag nbt) {
        int tanks = nbt.contains("Tanks") ? nbt.getInt("Tanks") : 8;
        List<FluidStack> fluids = new ArrayList<>();
        CompoundTag fluidsNbt = nbt.getCompound("Fluids");
        for (int i = 0; i < tanks; i++) {
            fluids.add(fluidsNbt.contains(Integer.toString(i))
                    ? FluidStack.loadFluidStackFromNBT(fluidsNbt.getCompound(Integer.toString(i)))
                    : FluidStack.EMPTY);
        }
        return fluids;
    }

    public static int readLegacyCapacity(CompoundTag nbt) {
        return nbt.getInt("Capacity");
    }

    public static int readLegacyTanks(CompoundTag nbt) {
        return nbt.contains("Tanks") ? nbt.getInt("Tanks") : 8;
    }

    public static final class Handler implements IFluidHandler {
        private final List<FluidStack> tankFluids;
        private final int capacity;
        private final int tanks;
        private Runnable onChange = () -> {
        };

        public Handler(int capacity, int tanks, List<FluidStack> fluids) {
            this.capacity = capacity;
            this.tanks = tanks;
            this.tankFluids = new ArrayList<>();
            for (int i = 0; i < tanks; i++) {
                this.tankFluids.add(i < fluids.size() ? fluids.get(i).copy() : FluidStack.EMPTY);
            }
        }

        @Override
        public int getTanks() {
            return tanks;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank >= 0 && tank < tanks ? tankFluids.get(tank) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return capacity;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }

            int totalFilled = 0;
            int remaining = resource.getAmount();
            int totalSpace = capacity - getTotalFluidAmount();
            if (totalSpace <= 0) {
                return 0;
            }

            for (int i = 0; i < tanks; i++) {
                FluidStack existing = tankFluids.get(i);
                if (!sameFluid(existing, resource)) {
                    continue;
                }

                int toFill = Math.min(remaining, totalSpace);
                if (action.execute()) {
                    existing.grow(toFill);
                    onChange.run();
                }
                totalFilled += toFill;
                remaining -= toFill;
                totalSpace -= toFill;
                if (remaining <= 0 || totalSpace <= 0) {
                    break;
                }
            }

            if (totalFilled == 0 && remaining > 0 && totalSpace > 0) {
                for (int i = 0; i < tanks; i++) {
                    if (!tankFluids.get(i).isEmpty()) {
                        continue;
                    }
                    int toFill = Math.min(remaining, totalSpace);
                    if (action.execute()) {
                        tankFluids.set(i, copyWithAmount(resource, toFill));
                        onChange.run();
                    }
                    totalFilled += toFill;
                    break;
                }
            }

            return totalFilled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }

            for (int i = 0; i < tanks; i++) {
                FluidStack existing = tankFluids.get(i);
                if (!sameFluid(existing, resource)) {
                    continue;
                }

                int drained = Math.min(existing.getAmount(), resource.getAmount());
                FluidStack stack = copyWithAmount(existing, drained);
                if (action.execute()) {
                    existing.shrink(drained);
                    if (existing.getAmount() <= 0) {
                        tankFluids.set(i, FluidStack.EMPTY);
                    }
                    onChange.run();
                }
                return stack;
            }

            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < tanks; i++) {
                FluidStack existing = tankFluids.get(i);
                if (existing.isEmpty()) {
                    continue;
                }

                int drained = Math.min(maxDrain, existing.getAmount());
                FluidStack stack = copyWithAmount(existing, drained);
                if (action.execute()) {
                    existing.shrink(drained);
                    if (existing.getAmount() <= 0) {
                        tankFluids.set(i, FluidStack.EMPTY);
                    }
                    onChange.run();
                }
                return stack;
            }

            return FluidStack.EMPTY;
        }

        public int getCapacity() {
            return capacity;
        }

        private int getTotalFluidAmount() {
            int total = 0;
            for (FluidStack fluid : tankFluids) {
                total += fluid.getAmount();
            }
            return total;
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
}
