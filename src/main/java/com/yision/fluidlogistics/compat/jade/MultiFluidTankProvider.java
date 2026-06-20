package com.yision.fluidlogistics.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.HorizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.util.SmartMultiFluidTank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

public enum MultiFluidTankProvider
        implements IServerExtensionProvider<BlockEntity, CompoundTag>, IClientExtensionProvider<CompoundTag, FluidView> {
    INSTANCE;

    public static final ResourceLocation UID = FluidLogistics.asResource("multi_fluid_tank");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        return ClientViewGroup.map(groups, FluidView::readDefault, null);
    }

    @Override
    public @Nullable List<ViewGroup<CompoundTag>> getGroups(ServerPlayer player, ServerLevel world, BlockEntity target,
            boolean showDetails) {
        SmartMultiFluidTank tank = getTank(target);
        if (tank == null) {
            return null;
        }

        int totalCapacity = tank.getCapacity();
        List<CompoundTag> views = new ArrayList<>();
        for (int i = 0; i < tank.getTanks(); i++) {
            FluidStack fluid = tank.getFluidInTank(i);
            if (fluid.isEmpty()) {
                continue;
            }
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

    private @Nullable SmartMultiFluidTank getTank(BlockEntity blockEntity) {
        if (blockEntity instanceof HorizontalMultiFluidTankBlockEntity be) {
            HorizontalMultiFluidTankBlockEntity controller = be.getControllerBE();
            return controller != null ? controller.getTankInventory() : null;
        }
        if (blockEntity instanceof MultiFluidTankBlockEntity be) {
            MultiFluidTankBlockEntity controller = be.getControllerBE();
            return controller != null ? controller.getTankInventory() : null;
        }
        return null;
    }
}
