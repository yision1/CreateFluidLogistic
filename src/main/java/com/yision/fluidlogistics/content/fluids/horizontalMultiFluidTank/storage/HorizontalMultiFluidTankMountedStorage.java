package com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.storage;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.content.contraptions.Contraption;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.SmartMultiFluidTank;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.storage.AbstractMultiFluidTankMountedStorage;
import com.yision.fluidlogistics.registry.AllMountedStorageTypes;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

public class HorizontalMultiFluidTankMountedStorage extends AbstractMultiFluidTankMountedStorage<AbstractMultiFluidTankMountedStorage.Handler> {
    public static final MapCodec<HorizontalMultiFluidTankMountedStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity").forGetter(HorizontalMultiFluidTankMountedStorage::getCapacity),
        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("tanks").forGetter(HorizontalMultiFluidTankMountedStorage::getTanks),
        FluidStack.OPTIONAL_CODEC.listOf().fieldOf("fluids").forGetter(HorizontalMultiFluidTankMountedStorage::getFluidsList)
    ).apply(i, HorizontalMultiFluidTankMountedStorage::new));

    public HorizontalMultiFluidTankMountedStorage(MountedFluidStorageType<?> type, int capacity, int tanks, List<FluidStack> fluids) {
        super(type, new Handler(capacity, tanks, fluids), capacity, tanks);
        this.wrapped.onChange = () -> this.dirty = true;
    }

    public HorizontalMultiFluidTankMountedStorage(int capacity, int tanks, List<FluidStack> fluids) {
        this(AllMountedStorageTypes.HORIZONTAL_MULTI_FLUID_TANK.get(), capacity, tanks, fluids);
    }

    @Override
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof HorizontalMultiFluidTankBlockEntity tank && tank.isController()) {
            SmartMultiFluidTank inventory = tank.getTankInventory();
            for (int i = 0; i < tanks && i < inventory.getTanks(); i++) {
                inventory.setFluid(i, this.wrapped.getFluidInTank(i).copy());
            }
        }
    }

    @Override
    public void afterSync(Contraption contraption, BlockPos localPos) {
        BlockEntity be = contraption.getBlockEntityClientSide(localPos);
        if (!(be instanceof HorizontalMultiFluidTankBlockEntity tank))
            return;

        SmartMultiFluidTank inv = tank.getTankInventory();
        for (int i = 0; i < this.wrapped.getTanks() && i < inv.getTanks(); i++) {
            inv.setFluid(i, this.wrapped.getFluidInTank(i).copy());
        }

        tank.setWindows(true);
        tank.initFluidLevel();
        LerpedFloat[] fluidLevel = tank.getFluidLevel();
        if (fluidLevel != null) {
            for (int i = 0; i < this.wrapped.getTanks() && i < fluidLevel.length; i++) {
                float fillLevel = this.wrapped.getFluidInTank(i).getAmount() / (float) this.capacity;
                fluidLevel[i].chase(fillLevel, 0.5, LerpedFloat.Chaser.EXP);
            }
        }
    }

    public static HorizontalMultiFluidTankMountedStorage fromTank(HorizontalMultiFluidTankBlockEntity tank) {
        SmartMultiFluidTank inventory = tank.getTankInventory();
        List<FluidStack> fluids = new ArrayList<>();
        for (int i = 0; i < inventory.getTanks(); i++) {
            fluids.add(inventory.getFluidInTank(i).copy());
        }
        return new HorizontalMultiFluidTankMountedStorage(inventory.getCapacity(), inventory.getTanks(), fluids);
    }

    public static HorizontalMultiFluidTankMountedStorage fromLegacy(HolderLookup.Provider registries, CompoundTag nbt) {
        int capacity = nbt.getInt("Capacity");
        int tanks = readLegacyTanks(nbt);
        List<FluidStack> fluids = readLegacyFluids(registries, nbt, tanks);
        return new HorizontalMultiFluidTankMountedStorage(capacity, tanks, fluids);
    }
}
