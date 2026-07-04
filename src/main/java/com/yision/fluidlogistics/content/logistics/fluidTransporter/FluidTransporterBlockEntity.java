package com.yision.fluidlogistics.content.logistics.fluidTransporter;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.yision.fluidlogistics.content.fluids.infiniteWater.InfiniteWaterSource;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.registry.AllBlocks;
import com.yision.fluidlogistics.util.SidedCapabilityCache;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public class FluidTransporterBlockEntity extends SmartBlockEntity {

    private static final int INTERNAL_BUFFER_CAPACITY = 1000;
    private static final int TRANSFER_PER_CYCLE = 450;
    private static final int TRANSFER_COOLDOWN_TICKS = 5;

    private final SidedCapabilityCache<IFluidHandler> capCaches =
        new SidedCapabilityCache<>(Capabilities.FluidHandler.BLOCK);
    private final EnumMap<Direction, IFluidHandler> exposedFluidHandlers = new EnumMap<>(Direction.class);
    private final IFluidHandler readOnlyFluidHandler = new TransporterFluidHandler(null);

    private SmartFluidTankBehaviour internalTank;
    private FilteringBehaviour filtering;
    private int transferCooldown;

    public FluidTransporterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AllBlockEntities.FLUID_TRANSPORTER.get(),
            (be, side) -> be.internalTank == null ? null : be.getExposedFluidHandler(side));
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

        IFluidHandler sidedHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, side.getOpposite());
        if (sidedHandler != null) {
            return true;
        }

        return level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, null) != null;
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

            FluidStack request = candidate.copyWithAmount(Math.min(Math.min(space, candidate.getAmount()), TRANSFER_PER_CYCLE));
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

        FluidStack transfer = stored.copyWithAmount(Math.min(stored.getAmount(), TRANSFER_PER_CYCLE));
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
        return stored.isEmpty() || FluidStack.isSameFluidSameComponents(stored, stack);
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

    @Override
    public void invalidate() {
        capCaches.clear();
        super.invalidate();
    }

    private @Nullable IFluidHandler getExposedFluidHandler(@Nullable Direction side) {
        if (side == null) {
            return readOnlyFluidHandler;
        }
        if (!isExternalFluidSide(side)) {
            return null;
        }
        return exposedFluidHandlers.computeIfAbsent(side, TransporterFluidHandler::new);
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
            return internalTank == null ? null : internalTank.getCapability();
        }
    }
}
