package com.yision.fluidlogistics.block.MultiFluidTank.storage;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.utility.CreateCodecs;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.registry.AllMountedStorageTypes;
import com.yision.fluidlogistics.util.SmartMultiFluidTank;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class MultiFluidTankMountedStorage extends WrapperMountedFluidStorage<MultiFluidTankMountedStorage.Handler>
        implements SyncedMountedStorage {

    public static final Codec<MultiFluidTankMountedStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(MultiFluidTankMountedStorage::getCapacity),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("tanks").forGetter(MultiFluidTankMountedStorage::getTanks),
            CreateCodecs.FLUID_STACK_CODEC.listOf().fieldOf("fluids").forGetter(MultiFluidTankMountedStorage::getFluidsList))
            .apply(i, MultiFluidTankMountedStorage::new));

    private boolean dirty;
    private final int capacity;
    private final int tanks;

    public MultiFluidTankMountedStorage(MountedFluidStorageType<?> type, int capacity, int tanks, List<FluidStack> fluids) {
        super(type, new Handler(capacity, tanks, fluids));
        this.capacity = capacity;
        this.tanks = tanks;
        this.wrapped.onChange = () -> this.dirty = true;
    }

    public MultiFluidTankMountedStorage(int capacity, int tanks, List<FluidStack> fluids) {
        this(AllMountedStorageTypes.MULTI_FLUID_TANK.get(), capacity, tanks, fluids);
    }

    @Override
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof MultiFluidTankBlockEntity tank && tank.isController()) {
            SmartMultiFluidTank inventory = tank.getTankInventory();
            for (int i = 0; i < tanks && i < inventory.getTanks(); i++) {
                inventory.setFluid(i, wrapped.getFluidInTank(i).copy());
            }
        }
    }

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
        if (!(be instanceof MultiFluidTankBlockEntity tank)) {
            return;
        }

        SmartMultiFluidTank inv = tank.getTankInventory();
        for (int i = 0; i < wrapped.getTanks() && i < inv.getTanks(); i++) {
            inv.setFluid(i, wrapped.getFluidInTank(i).copy());
        }

        tank.initFluidLevel();
        LerpedFloat[] fluidLevel = tank.getFluidLevel();
        if (fluidLevel != null) {
            for (int i = 0; i < wrapped.getTanks() && i < fluidLevel.length; i++) {
                float fillLevel = wrapped.getFluidInTank(i).getAmount() / (float) capacity;
                fluidLevel[i].chase(fillLevel, 0.5f, LerpedFloat.Chaser.EXP);
            }
        }
    }

    public static MultiFluidTankMountedStorage fromTank(MultiFluidTankBlockEntity tank) {
        SmartMultiFluidTank inventory = tank.getTankInventory();
        List<FluidStack> fluids = new ArrayList<>();
        for (int i = 0; i < inventory.getTanks(); i++) {
            fluids.add(inventory.getFluidInTank(i).copy());
        }
        return new MultiFluidTankMountedStorage(inventory.getCapacity(), inventory.getTanks(), fluids);
    }

    public static MultiFluidTankMountedStorage fromLegacy(CompoundTag nbt) {
        int capacity = nbt.getInt("Capacity");
        int tanks = nbt.contains("Tanks") ? nbt.getInt("Tanks") : 8;
        List<FluidStack> fluids = new ArrayList<>();
        CompoundTag fluidsNbt = nbt.getCompound("Fluids");
        for (int i = 0; i < tanks; i++) {
            fluids.add(fluidsNbt.contains(Integer.toString(i))
                    ? FluidStack.loadFluidStackFromNBT(fluidsNbt.getCompound(Integer.toString(i)))
                    : FluidStack.EMPTY);
        }
        return new MultiFluidTankMountedStorage(capacity, tanks, fluids);
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
