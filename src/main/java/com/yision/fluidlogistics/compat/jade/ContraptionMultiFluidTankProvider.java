package com.yision.fluidlogistics.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.storage.MultiFluidTankMountedStorage;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.storage.HorizontalMultiFluidTankMountedStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

public enum ContraptionMultiFluidTankProvider implements IServerExtensionProvider<CompoundTag>, IClientExtensionProvider<CompoundTag, FluidView> {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.parse("fluidlogistics:contraption_multi_fluid_tank");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        return ClientViewGroup.map(groups, FluidView::readDefault, null);
    }

    @Override
    public @Nullable List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof AbstractContraptionEntity entity)) {
            return null;
        }
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return null;
        }
        
        MountedStorageManager storageManager = contraption.getStorage();
        if (storageManager == null) {
            return null;
        }
        
        Map<BlockPos, MountedFluidStorage> storages = storageManager.getFluids().storages;
        
        List<FluidStack> allFluids = new ArrayList<>();
        int totalCapacity = 0;
        
        for (MountedFluidStorage storage : storages.values()) {
            if (storage instanceof MultiFluidTankMountedStorage multiStorage) {
                totalCapacity += multiStorage.getCapacity();
                for (int i = 0; i < multiStorage.getTanks(); i++) {
                    FluidStack fluid = multiStorage.getFluidInTank(i);
                    if (!fluid.isEmpty()) {
                        mergeFluid(allFluids, fluid);
                    }
                }
            } else if (storage instanceof HorizontalMultiFluidTankMountedStorage horizontalStorage) {
                totalCapacity += horizontalStorage.getCapacity();
                for (int i = 0; i < horizontalStorage.getTanks(); i++) {
                    FluidStack fluid = horizontalStorage.getFluidInTank(i);
                    if (!fluid.isEmpty()) {
                        mergeFluid(allFluids, fluid);
                    }
                }
            } else if (storage instanceof IFluidHandler handler) {
                for (int i = 0; i < handler.getTanks(); i++) {
                    FluidStack fluid = handler.getFluidInTank(i);
                    if (!fluid.isEmpty()) {
                        totalCapacity += handler.getTankCapacity(i);
                        mergeFluid(allFluids, fluid);
                    }
                }
            }
        }
        
        if (totalCapacity <= 0) {
            return null;
        }
        
        List<CompoundTag> views = new ArrayList<>();
        if (allFluids.isEmpty()) {
            JadeFluidObject emptyFluid = JadeFluidObject.empty();
            views.add(FluidView.writeDefault(emptyFluid, totalCapacity));
        } else {
            for (FluidStack fluid : allFluids) {
                JadeFluidObject fluidObject = JadeFluidObject.of(
                    fluid.getFluid(),
                    fluid.getAmount(),
                    fluid.getComponentsPatch()
                );
                views.add(FluidView.writeDefault(fluidObject, totalCapacity));
            }
        }

        return List.of(new ViewGroup<>(views));
    }
    
    private void mergeFluid(List<FluidStack> fluids, FluidStack newFluid) {
        for (FluidStack existing : fluids) {
            if (FluidStack.isSameFluidSameComponents(existing, newFluid)) {
                existing.grow(newFluid.getAmount());
                return;
            }
        }
        fluids.add(newFluid.copy());
    }

    @Override
    public boolean shouldRequestData(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof AbstractContraptionEntity entity)) {
            return false;
        }
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return false;
        }
        
        MountedStorageManager storageManager = contraption.getStorage();
        if (storageManager == null) {
            return false;
        }
        
        Map<BlockPos, MountedFluidStorage> storages = storageManager.getFluids().storages;
        for (MountedFluidStorage storage : storages.values()) {
            if (storage instanceof MultiFluidTankMountedStorage || 
                storage instanceof HorizontalMultiFluidTankMountedStorage) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public int getDefaultPriority() {
        return -9999;
    }
}
