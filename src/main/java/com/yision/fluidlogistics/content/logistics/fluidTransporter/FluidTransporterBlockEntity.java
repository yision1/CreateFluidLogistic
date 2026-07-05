package com.yision.fluidlogistics.content.logistics.fluidTransporter;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.yision.fluidlogistics.content.fluids.infiniteWater.InfiniteWaterSource;
import com.yision.fluidlogistics.registry.AllBlocks;
import com.yision.fluidlogistics.util.SidedCapabilityCache;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FluidTransporterBlockEntity extends SmartBlockEntity {

    private static final int INTERNAL_BUFFER_CAPACITY = 1000;
    private static final int TRANSFER_PER_CYCLE = 450;
    private static final int TRANSFER_COOLDOWN_TICKS = 5;

    private final SidedCapabilityCache<IFluidHandler> capCaches =
        new SidedCapabilityCache<>(ForgeCapabilities.FLUID_HANDLER);
    private final Map<Direction, LazyOptional<IFluidHandler>> exposedFluidCapabilities = new EnumMap<>(Direction.class);
    private LazyOptional<IFluidHandler> readOnlyFluidCapability = LazyOptional.empty();

    private SmartFluidTankBehaviour internalTank;
    private FilteringBehaviour filtering;
    private int transferCooldown;

    public FluidTransporterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(internalTank = SmartFluidTankBehaviour.single(this, INTERNAL_BUFFER_CAPACITY));
        behaviours.add(filtering = new FilteringBehaviour(this, new FluidTransporterFilterSlotPositioning()).forFluids()
            .withCallback($ -> notifyUpdate()));
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        if (transferCooldown > 0) {
            transferCooldown--;
        }

        if (!canActivate() || transferCooldown > 0) {
            return;
        }

        Direction facing = getBlockState().getValue(FluidTransporterBlock.FACING);
        boolean transferred = handleOutput(grabCapability(facing));

        BlockPos inputPos = worldPosition.relative(facing.getOpposite());
        if (!level.getBlockState(inputPos).is(AllBlocks.FLUID_TRANSPORTER.get())) {
            transferred |= handleInput(getInputHandler(facing.getOpposite()));
        }

        if (transferred) {
            transferCooldown = TRANSFER_COOLDOWN_TICKS;
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        exposedFluidCapabilities.values().forEach(LazyOptional::invalidate);
        exposedFluidCapabilities.clear();
        readOnlyFluidCapability.invalidate();
        readOnlyFluidCapability = LazyOptional.empty();
        capCaches.clear();
    }

    public boolean shouldRenderInterface(Direction side) {
        if (level == null) {
            return false;
        }

        Direction facing = getBlockState().getValue(FluidTransporterBlock.FACING);
        if (side != facing && side != facing.getOpposite()) {
            return false;
        }

        BlockPos targetPos = worldPosition.relative(side);
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.is(AllBlocks.FLUID_TRANSPORTER.get()) || InfiniteWaterSource.isWaterSourceBlock(targetState)) {
            return false;
        }

        return getBlockFluidHandler(targetPos, side.getOpposite()) != null;
    }

    private boolean handleInput(@Nullable IFluidHandler source) {
        if (source == null || internalTank == null) {
            return false;
        }

        FluidStack stored = internalTank.getPrimaryHandler().getFluid();
        int space = INTERNAL_BUFFER_CAPACITY - stored.getAmount();
        if (space <= 0) {
            return false;
        }

        for (int tank = 0; tank < source.getTanks(); tank++) {
            FluidStack candidate = source.getFluidInTank(tank);
            if (candidate.isEmpty() || !canAcceptFluid(candidate)) {
                continue;
            }

            int requestedAmount = Math.min(Math.min(space, candidate.getAmount()), TRANSFER_PER_CYCLE);
            FluidStack request = candidate.copy();
            request.setAmount(requestedAmount);
            FluidStack simulated = source.drain(request, FluidAction.SIMULATE);
            if (simulated.isEmpty() || !canAcceptFluid(simulated)) {
                continue;
            }

            FluidStack drained = source.drain(simulated, FluidAction.EXECUTE);
            if (drained.isEmpty()) {
                continue;
            }

            internalTank.getPrimaryHandler().fill(drained, FluidAction.EXECUTE);
            notifyUpdate();
            return true;
        }

        return false;
    }

    private boolean handleOutput(@Nullable IFluidHandler destination) {
        if (destination == null || internalTank == null) {
            return false;
        }

        FluidStack stored = internalTank.getPrimaryHandler().getFluid().copy();
        if (stored.isEmpty()) {
            return false;
        }

        FluidStack transfer = stored.copy();
        transfer.setAmount(Math.min(stored.getAmount(), TRANSFER_PER_CYCLE));
        int accepted = destination.fill(transfer, FluidAction.EXECUTE);
        if (accepted <= 0) {
            return false;
        }

        internalTank.getPrimaryHandler().drain(accepted, FluidAction.EXECUTE);
        notifyUpdate();
        return true;
    }

    private boolean canAcceptFluid(FluidStack stack) {
        if (stack.isEmpty() || filtering != null && !filtering.test(stack)) {
            return false;
        }

        FluidStack stored = internalTank == null ? FluidStack.EMPTY : internalTank.getPrimaryHandler().getFluid();
        return stored.isEmpty()
            || (stored.isFluidEqual(stack) && FluidStack.areFluidStackTagsEqual(stored, stack));
    }

    private boolean canActivate() {
        return !getBlockState().getValue(FluidTransporterBlock.POWERED);
    }

    private @Nullable IFluidHandler getInputHandler(Direction facing) {
        if (level == null) {
            return null;
        }

        BlockPos targetPos = worldPosition.relative(facing);
        BlockState targetState = level.getBlockState(targetPos);
        IFluidHandler infinite = InfiniteWaterSource.getSourceHandler(
            InfiniteWaterSource.Consumer.FLUID_TRANSPORTER, targetState);
        if (infinite != null) {
            return infinite;
        }

        return grabCapability(facing);
    }

    private @Nullable IFluidHandler grabCapability(Direction facing) {
        if (level == null) {
            return null;
        }

        BlockPos targetPos = worldPosition.relative(facing);
        return capCaches.get(level, targetPos, facing);
    }

    private @Nullable IFluidHandler getBlockFluidHandler(BlockPos targetPos, @Nullable Direction side) {
        if (level == null) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(targetPos);
        if (blockEntity == null) {
            return null;
        }

        IFluidHandler sidedHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
        if (sidedHandler != null) {
            return sidedHandler;
        }

        return blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            LazyOptional<IFluidHandler> exposed = getExposedFluidCapability(side);
            return exposed.cast();
        }
        return super.getCapability(cap, side);
    }

    private LazyOptional<IFluidHandler> getExposedFluidCapability(@Nullable Direction side) {
        if (internalTank == null) {
            return LazyOptional.empty();
        }
        if (side == null) {
            if (!readOnlyFluidCapability.isPresent()) {
                readOnlyFluidCapability = LazyOptional.of(() -> new TransporterFluidHandler(null));
            }
            return readOnlyFluidCapability;
        }
        if (!isExternalFluidSide(side)) {
            return LazyOptional.empty();
        }
        return exposedFluidCapabilities.computeIfAbsent(side,
            output -> LazyOptional.of(() -> new TransporterFluidHandler(output)));
    }

    private boolean isExternalFluidSide(Direction side) {
        Direction facing = getBlockState().getValue(FluidTransporterBlock.FACING);
        return side == facing || side == facing.getOpposite();
    }

    private boolean canFillFrom(@Nullable Direction side) {
        return side == getBlockState().getValue(FluidTransporterBlock.FACING).getOpposite();
    }

    private boolean canDrainFrom(@Nullable Direction side) {
        return side == getBlockState().getValue(FluidTransporterBlock.FACING);
    }

    private class TransporterFluidHandler implements IFluidHandler {
        private final @Nullable Direction side;

        private TransporterFluidHandler(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int getTanks() {
            IFluidHandler handler = getInternalHandler();
            return handler == null ? 0 : handler.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler handler = getInternalHandler();
            return handler == null ? FluidStack.EMPTY : handler.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler handler = getInternalHandler();
            return handler == null ? 0 : handler.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return canFillFrom(side) && canAcceptFluid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler handler = getInternalHandler();
            if (handler == null || !canFillFrom(side) || !canAcceptFluid(resource)) {
                return 0;
            }
            return handler.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler handler = getInternalHandler();
            return handler == null || !canDrainFrom(side) ? FluidStack.EMPTY : handler.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler handler = getInternalHandler();
            return handler == null || !canDrainFrom(side) ? FluidStack.EMPTY : handler.drain(maxDrain, action);
        }

        private @Nullable IFluidHandler getInternalHandler() {
            return internalTank == null ? null : internalTank.getCapability().orElse(null);
        }
    }
}
