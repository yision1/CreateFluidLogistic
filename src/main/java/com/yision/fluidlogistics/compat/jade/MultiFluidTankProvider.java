package com.yision.fluidlogistics.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.SmartMultiFluidTank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

public enum MultiFluidTankProvider implements IServerExtensionProvider<CompoundTag>, IClientExtensionProvider<CompoundTag, FluidView> {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.parse("fluidlogistics:multi_fluid_tank");

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
        SmartMultiFluidTank tank = getTank(accessor);
        if (tank == null) {
            return null;
        }

        int totalCapacity = tank.getCapacity();
        List<CompoundTag> views = new ArrayList<>();
        
        int usedCapacity = 0;
        for (int i = 0; i < tank.getTanks(); i++) {
            FluidStack fluid = tank.getFluidInTank(i);
            if (!fluid.isEmpty()) {
                JadeFluidObject fluidObject = JadeFluidObject.of(
                    fluid.getFluid(),
                    fluid.getAmount(),
                    fluid.getComponentsPatch()
                );
                views.add(FluidView.writeDefault(fluidObject, totalCapacity));
                usedCapacity += fluid.getAmount();
            }
        }

        int emptyCapacity = totalCapacity - usedCapacity;
        if (emptyCapacity > 0 && views.isEmpty()) {
            views.add(FluidView.writeDefault(JadeFluidObject.empty(), totalCapacity));
        }

        if (views.isEmpty()) {
            return null;
        }

        return List.of(new ViewGroup<>(views));
    }

    @Override
    public boolean shouldRequestData(Accessor<?> accessor) {
        return getTank(accessor) != null;
    }

    @Override
    public int getDefaultPriority() {
        return 9998;
    }

    private @Nullable SmartMultiFluidTank getTank(Accessor<?> accessor) {
        if (accessor instanceof BlockAccessor blockAccessor) {
            if (blockAccessor.getBlockEntity() instanceof HorizontalMultiFluidTankBlockEntity be) {
                HorizontalMultiFluidTankBlockEntity controller = be.getControllerBE();
                if (controller != null) {
                    return controller.getTankInventory();
                }
            }
            if (blockAccessor.getBlockEntity() instanceof MultiFluidTankBlockEntity be) {
                MultiFluidTankBlockEntity controller = be.getControllerBE();
                if (controller != null) {
                    return controller.getTankInventory();
                }
            }
        }
        return null;
    }
}
