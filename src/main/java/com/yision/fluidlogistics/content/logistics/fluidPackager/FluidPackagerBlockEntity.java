package com.yision.fluidlogistics.content.logistics.fluidPackager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.compat.computercraft.ComputerCraftProxy;
import com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase.InterfaceProvider;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.TankManipulationBehaviour;
import com.yision.fluidlogistics.api.packager.PackageResourceTypes;
import com.yision.fluidlogistics.api.packager.ResourcePackager;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidHandlerHelper;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.util.IPackagerOverrideData;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class FluidPackagerBlockEntity extends PackagerBlockEntity
        implements Clearable, ResourcePackager, IHaveGoggleInformation {

    public TankManipulationBehaviour fluidTarget;

    public FluidPackagerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                AllBlockEntities.FLUID_PACKAGER.get(),
                (be, context) -> be.inventory);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(fluidTarget = new TankManipulationBehaviour(this, InterfaceProvider.oppositeOfBlockFacing())
                .withFilter(this::supportsFluidTarget));
        targetInventory = new InvManipulationBehaviour(this, InterfaceProvider.oppositeOfBlockFacing())
                .withFilter(this::supportsItemTarget);
        behaviours.add(targetInventory);
        behaviours.add(new AdvancementBehaviour(this, AllAdvancements.PACKAGER));
        behaviours.add(computerBehaviour = ComputerCraftProxy.behaviour(this));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        IPackagerOverrideData overrideData = fluidlogistics$overrideData();
        String address = signBasedAddress;
        if (level != null && level.isClientSide) {
            String scannedAddress = findSignAddress();
            if (!scannedAddress.isBlank()) {
                address = scannedAddress;
            } else if (address.isBlank()) {
                address = overrideData.clipboardAddress();
            }
        } else if (address.isBlank()) {
            address = overrideData.clipboardAddress();
        }

        BlockState state = getBlockState();
        boolean linked = state.hasProperty(FluidPackagerBlock.LINKED)
                && state.getValue(FluidPackagerBlock.LINKED);
        PackagerGoggleInfo.addFluidPackagerToTooltip(
                tooltip, address, overrideData.fluidlogistics$isManualOverrideLocked(), linked);
        return true;
    }

    private IPackagerOverrideData fluidlogistics$overrideData() {
        return (IPackagerOverrideData) (Object) this;
    }

    private String findSignAddress() {
        if (level == null) {
            return "";
        }
        for (Direction side : Iterate.directions) {
            String address = getSign(side);
            if (address != null && !address.isBlank()) {
                return address;
            }
        }
        return "";
    }

    private boolean supportsFluidTarget(@Nullable BlockEntity target) {
        return !(target instanceof PortableFluidInterfaceBlockEntity);
    }

    private boolean supportsItemTarget(@Nullable BlockEntity target) {
        return target != null && !(target instanceof PortableFluidInterfaceBlockEntity);
    }

    @Override
    public PackagerBlockEntity owner() {
        return this;
    }

    @Override
    public ResourceLocation resourceTypeId() {
        return PackageResourceTypes.FLUID;
    }

    @Override
    public Snapshot scan() {
        IFluidHandler fluidHandler = fluidTarget.getInventory();
        if (fluidHandler == null) {
            return Snapshot.empty(null);
        }
        InventorySummary summary = new InventorySummary();
        for (Map.Entry<FluidTypeKey, Integer> entry : scanAvailableFluids(fluidHandler).entrySet()) {
            summary.add(PackageResourceTypes.createFluidKey(entry.getKey().template()), entry.getValue());
        }
        return new Snapshot(fluidHandler, summary);
    }

    @Override
    public int extract(ItemStack normalizedKey, int maxAmount, boolean simulate) {
        if (maxAmount <= 0 || !CompressedTankItem.isFluidStack(normalizedKey)) {
            return 0;
        }
        IFluidHandler handler = fluidTarget.getInventory();
        if (handler == null) {
            return 0;
        }
        FluidStack target = CompressedTankItem.getFluid(normalizedKey);
        if (InfiniteFluidHandlerHelper.isInfiniteSource(handler, target)) {
            return maxAmount;
        }
        FluidStack drained = handler.drain(
                target.copyWithAmount(maxAmount),
                simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
        return FluidStack.isSameFluidSameComponents(target, drained) ? drained.getAmount() : 0;
    }

    @Override
    public int insert(ItemStack normalizedKey, int maxAmount, boolean simulate) {
        if (maxAmount <= 0 || !CompressedTankItem.isFluidStack(normalizedKey)) {
            return 0;
        }
        IFluidHandler handler = fluidTarget.getInventory();
        if (handler == null) {
            return 0;
        }
        FluidStack fluid = CompressedTankItem.getFluid(normalizedKey).copyWithAmount(maxAmount);
        if (InfiniteFluidHandlerHelper.canAcceptInfinitely(handler, fluid)) {
            return maxAmount;
        }
        return handler.fill(fluid, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
    }

    private Map<FluidTypeKey, Integer> scanAvailableFluids(IFluidHandler fluidHandler) {
        int tankCount = safeGetTanks(fluidHandler);
        if (tankCount == 0) {
            return Map.of();
        }
        Map<FluidTypeKey, Integer> scanned = new LinkedHashMap<>();
        for (int tank = 0; tank < tankCount; tank++) {
            FluidStack fluid = safeGetFluidInTank(fluidHandler, tank);
            if (fluid.isEmpty()) {
                continue;
            }
            FluidTypeKey key = FluidTypeKey.of(fluid);
            int amount = InfiniteFluidHandlerHelper.getStockAmount(fluidHandler, fluid);
            scanned.merge(key, amount, FluidPackagerBlockEntity::mergeFluidAmounts);
        }
        return scanned.isEmpty() ? Map.of() : scanned;
    }

    private static int mergeFluidAmounts(int existingAmount, int addedAmount) {
        if (existingAmount >= BigItemStack.INF || addedAmount >= BigItemStack.INF) {
            return BigItemStack.INF;
        }
        return (int) Math.min(BigItemStack.INF, (long) existingAmount + addedAmount);
    }

    private record FluidTypeKey(FluidStack template) {
        private static FluidTypeKey of(FluidStack stack) {
            return new FluidTypeKey(stack.copyWithAmount(1));
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof FluidTypeKey other
                    && FluidStack.isSameFluidSameComponents(template, other.template);
        }

        @Override
        public int hashCode() {
            return Objects.hash(template.getFluid(), template.getComponentsPatch());
        }
    }

    private static int safeGetTanks(IFluidHandler handler) {
        try {
            return handler.getTanks();
        } catch (IllegalStateException exception) {
            return 0;
        }
    }

    private static FluidStack safeGetFluidInTank(IFluidHandler handler, int tank) {
        try {
            return handler.getFluidInTank(tank);
        } catch (IllegalStateException exception) {
            return FluidStack.EMPTY;
        }
    }

    @Override
    public void clearContent() {
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        queuedExitingPackages.clear();
    }

}
