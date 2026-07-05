package com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.storage;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType;
import com.simibubi.create.foundation.utility.CreateCodecs;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.SmartMultiFluidTank;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.storage.AbstractMultiFluidTankMountedStorage;
import com.yision.fluidlogistics.registry.AllMountedStorageTypes;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

public class HorizontalMultiFluidTankMountedStorage extends AbstractMultiFluidTankMountedStorage {

    public static final Codec<HorizontalMultiFluidTankMountedStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("capacity")
                    .forGetter(HorizontalMultiFluidTankMountedStorage::getCapacity),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("tanks").forGetter(HorizontalMultiFluidTankMountedStorage::getTanks),
            CreateCodecs.FLUID_STACK_CODEC.listOf().fieldOf("fluids")
                    .forGetter(HorizontalMultiFluidTankMountedStorage::getFluidsList))
            .apply(i, HorizontalMultiFluidTankMountedStorage::new));

    public HorizontalMultiFluidTankMountedStorage(MountedFluidStorageType<?> type, int capacity, int tanks,
            List<FluidStack> fluids) {
        super(type, capacity, tanks, fluids);
    }

    public HorizontalMultiFluidTankMountedStorage(int capacity, int tanks, List<FluidStack> fluids) {
        this(AllMountedStorageTypes.HORIZONTAL_MULTI_FLUID_TANK.get(), capacity, tanks, fluids);
    }

    @Override
    protected boolean matchesController(BlockEntity be) {
        return be instanceof HorizontalMultiFluidTankBlockEntity tank && tank.isController();
    }

    @Override
    protected void applyMountedState(BlockEntity controller, int tanks, AbstractMultiFluidTankMountedStorage.Handler wrapped) {
        if (!(controller instanceof HorizontalMultiFluidTankBlockEntity tank)) {
            return;
        }

        SmartMultiFluidTank inventory = tank.getTankInventory();
        for (int i = 0; i < tanks && i < inventory.getTanks(); i++) {
            inventory.setFluid(i, wrapped.getFluidInTank(i).copy());
        }

        tank.setWindows(true);
        tank.initFluidLevel();
        LerpedFloat[] fluidLevel = tank.getFluidLevel();
        if (fluidLevel != null) {
            for (int i = 0; i < wrapped.getTanks() && i < fluidLevel.length; i++) {
                float fillLevel = wrapped.getFluidInTank(i).getAmount() / (float) getCapacity();
                fluidLevel[i].chase(fillLevel, 0.5f, LerpedFloat.Chaser.EXP);
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

    public static HorizontalMultiFluidTankMountedStorage fromLegacy(CompoundTag nbt) {
        return new HorizontalMultiFluidTankMountedStorage(readLegacyCapacity(nbt), readLegacyTanks(nbt), readLegacyFluids(nbt));
    }
}
