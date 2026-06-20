package com.yision.fluidlogistics.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.storage.HorizontalMultiFluidTankMountedStorage;
import com.yision.fluidlogistics.block.MultiFluidTank.storage.MultiFluidTankMountedStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

public enum ContraptionMultiFluidTankProvider implements IServerExtensionProvider<AbstractContraptionEntity, CompoundTag>,
        IClientExtensionProvider<CompoundTag, FluidView> {
    INSTANCE;

    public static final ResourceLocation UID = FluidLogistics.asResource("contraption_multi_fluid_tank");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        return ClientViewGroup.map(groups, FluidView::readDefault, null);
    }

    @Override
    public @Nullable List<ViewGroup<CompoundTag>> getGroups(ServerPlayer player, ServerLevel world,
            AbstractContraptionEntity entity, boolean showDetails) {
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return null;
        }

        MountedStorageManager storageManager = contraption.getStorage();
        if (storageManager == null || storageManager.getFluids() == null) {
            return null;
        }

        Map<BlockPos, MountedFluidStorage> storages = storageManager.getFluids().storages;
        List<FluidStack> allFluids = new ArrayList<>();
        int totalCapacity = 0;

        for (MountedFluidStorage storage : storages.values()) {
            if (storage instanceof MultiFluidTankMountedStorage multiStorage) {
                totalCapacity += multiStorage.getCapacity();
                addFluids(allFluids, multiStorage);
            } else if (storage instanceof HorizontalMultiFluidTankMountedStorage horizontalStorage) {
                totalCapacity += horizontalStorage.getCapacity();
                addFluids(allFluids, horizontalStorage);
            } else {
                IFluidHandler handler = storage;
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
        for (FluidStack fluid : allFluids) {
            views.add(FluidView.writeDefault(JadeFluidObject.of(fluid.getFluid(), fluid.getAmount(), fluid.getTag()),
                    totalCapacity));
        }

        if (views.isEmpty()) {
            views.add(FluidView.writeDefault(JadeFluidObject.empty(), totalCapacity));
        }

        return List.of(new ViewGroup<>(views));
    }

    @Override
    public int getDefaultPriority() {
        return -9999;
    }

    private static void addFluids(List<FluidStack> allFluids, IFluidHandler handler) {
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fluid = handler.getFluidInTank(i);
            if (!fluid.isEmpty()) {
                mergeFluid(allFluids, fluid);
            }
        }
    }

    private static void mergeFluid(List<FluidStack> fluids, FluidStack newFluid) {
        for (FluidStack existing : fluids) {
            if (existing.isFluidEqual(newFluid) && FluidStack.areFluidStackTagsEqual(existing, newFluid)) {
                existing.grow(newFluid.getAmount());
                return;
            }
        }
        fluids.add(newFluid.copy());
    }
}
