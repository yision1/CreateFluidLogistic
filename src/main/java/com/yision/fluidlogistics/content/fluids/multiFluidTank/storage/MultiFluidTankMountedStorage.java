package com.yision.fluidlogistics.content.fluids.multiFluidTank.storage;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.api.contraption.storage.fluid.WrapperMountedFluidStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.registry.AllMountedStorageTypes;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.SmartMultiFluidTank;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MultiFluidTankMountedStorage extends WrapperMountedFluidStorage<MultiFluidTankMountedStorage.Handler> implements SyncedMountedStorage {
    public static final MapCodec<MultiFluidTankMountedStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(MultiFluidTankMountedStorage::getCapacity),
        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("tanks").forGetter(MultiFluidTankMountedStorage::getTanks),
        FluidStack.OPTIONAL_CODEC.listOf().fieldOf("fluids").forGetter(MultiFluidTankMountedStorage::getFluidsList)
    ).apply(i, MultiFluidTankMountedStorage::new));

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
                inventory.setFluid(i, this.wrapped.getFluidInTank(i).copy());
            }
        }
    }

    public List<FluidStack> getFluidsList() {
        List<FluidStack> fluids = new ArrayList<>();
        for (int i = 0; i < this.wrapped.getTanks(); i++) {
            fluids.add(this.wrapped.getFluidInTank(i).copy());
        }
        return fluids;
    }

    public int getCapacity() {
        return this.capacity;
    }

    public int getTanks() {
        return this.tanks;
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public void markClean() {
        this.dirty = false;
    }

    @Override
    public void afterSync(Contraption contraption, BlockPos localPos) {
        BlockEntity be = contraption.getBlockEntityClientSide(localPos);
        if (!(be instanceof MultiFluidTankBlockEntity tank))
            return;

        SmartMultiFluidTank inv = tank.getTankInventory();
        for (int i = 0; i < this.wrapped.getTanks() && i < inv.getTanks(); i++) {
            inv.setFluid(i, this.wrapped.getFluidInTank(i).copy());
        }

        tank.initFluidLevel();
        LerpedFloat[] fluidLevel = tank.getFluidLevel();
        if (fluidLevel != null) {
            for (int i = 0; i < this.wrapped.getTanks() && i < fluidLevel.length; i++) {
                float fillLevel = this.wrapped.getFluidInTank(i).getAmount() / (float) this.capacity;
                fluidLevel[i].chase(fillLevel, 0.5, LerpedFloat.Chaser.EXP);
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

    public static MultiFluidTankMountedStorage fromLegacy(HolderLookup.Provider registries, CompoundTag nbt) {
        int capacity = nbt.getInt("Capacity");
        int tanks = nbt.contains("Tanks") ? nbt.getInt("Tanks") : 8;
        List<FluidStack> fluids = new ArrayList<>();
        CompoundTag fluidsNbt = nbt.getCompound("Fluids");
        for (int i = 0; i < tanks; i++) {
            if (fluidsNbt.contains(Integer.toString(i))) {
                FluidStack fluid = FluidStack.parseOptional(registries, fluidsNbt.getCompound(Integer.toString(i)));
                fluids.add(fluid);
            } else {
                fluids.add(FluidStack.EMPTY);
            }
        }
        return new MultiFluidTankMountedStorage(capacity, tanks, fluids);
    }

    public static final class Handler implements IFluidHandler {
        private final List<FluidStack> tankFluids;
        private final int capacity;
        private final int tanks;
        private Runnable onChange = () -> {};

        public Handler(int capacity, int tanks, List<FluidStack> fluids) {
            this.capacity = capacity;
            this.tanks = tanks;
            this.tankFluids = new ArrayList<>();
            for (int i = 0; i < tanks; i++) {
                if (i < fluids.size()) {
                    this.tankFluids.add(fluids.get(i).copy());
                } else {
                    this.tankFluids.add(FluidStack.EMPTY);
                }
            }
        }

        @Override
        public int getTanks() {
            return this.tanks;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank >= 0 && tank < this.tanks) {
                return this.tankFluids.get(tank);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.capacity;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            
            int totalFilled = 0;
            int remaining = resource.getAmount();
            
            int currentTotal = getTotalFluidAmount();
            int totalSpace = this.capacity - currentTotal;
            if (totalSpace <= 0) return 0;
            
            for (int i = 0; i < this.tanks; i++) {
                FluidStack existing = this.tankFluids.get(i);
                if (!existing.isEmpty() && FluidStack.isSameFluidSameComponents(existing, resource)) {
                    int toFill = Math.min(remaining, totalSpace);
                    if (action.execute()) {
                        this.tankFluids.set(i, existing.copyWithAmount(existing.getAmount() + toFill));
                        this.onChange.run();
                    }
                    totalFilled += toFill;
                    remaining -= toFill;
                    totalSpace -= toFill;
                    if (remaining <= 0 || totalSpace <= 0) break;
                }
            }
            
            if (totalFilled == 0 && remaining > 0 && totalSpace > 0) {
                for (int i = 0; i < this.tanks; i++) {
                    if (this.tankFluids.get(i).isEmpty()) {
                        int toFill = Math.min(remaining, totalSpace);
                        if (action.execute()) {
                            this.tankFluids.set(i, resource.copyWithAmount(toFill));
                            this.onChange.run();
                        }
                        totalFilled += toFill;
                        break;
                    }
                }
            }
            
            return totalFilled;
        }
        
        private int getTotalFluidAmount() {
            int total = 0;
            for (FluidStack fluid : this.tankFluids) {
                total += fluid.getAmount();
            }
            return total;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            for (int i = 0; i < this.tanks; i++) {
                FluidStack existing = this.tankFluids.get(i);
                if (!existing.isEmpty() && FluidStack.isSameFluidSameComponents(existing, resource)) {
                    int drained = Math.min(existing.getAmount(), resource.getAmount());
                    if (action.execute()) {
                        this.tankFluids.set(i, existing.copyWithAmount(existing.getAmount() - drained));
                        this.onChange.run();
                    }
                    return existing.copyWithAmount(drained);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < this.tanks; i++) {
                FluidStack existing = this.tankFluids.get(i);
                if (!existing.isEmpty()) {
                    int drained = Math.min(maxDrain, existing.getAmount());
                    if (action.execute()) {
                        this.tankFluids.set(i, existing.copyWithAmount(existing.getAmount() - drained));
                        this.onChange.run();
                    }
                    return existing.copyWithAmount(drained);
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
