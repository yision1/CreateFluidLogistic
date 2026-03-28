package com.yision.fluidlogistics.block.FluidTransporter;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.registry.AllBlocks;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
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

    private final EnumMap<Direction, BlockCapabilityCache<IFluidHandler, @Nullable Direction>> capCaches =
        new EnumMap<>(Direction.class);

    private final IFluidHandler exposedFluidHandler = new TransporterFluidHandler();
    private SmartFluidTankBehaviour internalTank;
    private FilteringBehaviour filtering;
    private int transferCooldown;

    public FluidTransporterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AllBlockEntities.FLUID_TRANSPORTER.get(),
            (be, side) -> !FeatureToggle.isEnabled(FeatureToggle.FLUID_TRANSPORTER) || be.internalTank == null
                    ? null
                    : be.exposedFluidHandler);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(internalTank = SmartFluidTankBehaviour.single(this, INTERNAL_BUFFER_CAPACITY));
        behaviours.add(filtering = new FilteringBehaviour(this, new FluidTransporterFilterSlotPositioning()).forFluids()
            .withCallback($ -> notifyUpdate()));
        filtering.setLabel(Component.translatable("block.fluidlogistics.fluid_transporter.filter").copy());
    }

    @Override
    public void tick() {
        super.tick();

        if (!FeatureToggle.isEnabled(FeatureToggle.FLUID_TRANSPORTER) || level == null || level.isClientSide) {
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
        if (!FeatureToggle.isEnabled(FeatureToggle.FLUID_TRANSPORTER) || level == null) {
            return false;
        }

        Direction facing = getBlockState().getValue(FluidTransporterBlock.FACING);
        if (side != facing && side != facing.getOpposite()) {
            return false;
        }

        BlockPos targetPos = worldPosition.relative(side);
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.is(AllBlocks.FLUID_TRANSPORTER.get()) || isInfiniteWaterLeafSource(targetState)) {
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
        if (isInfiniteWaterLeafSource(targetState)) {
            return WaterloggedLeavesFluidHandler.INSTANCE;
        }

        return grabCapability(facing);
    }

    private boolean isInfiniteWaterLeafSource(BlockState state) {
        return state.is(BlockTags.LEAVES)
            && state.hasProperty(BlockStateProperties.WATERLOGGED)
            && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    private @Nullable IFluidHandler grabCapability(Direction facing) {
        if (level == null) {
            return null;
        }

        BlockPos targetPos = worldPosition.relative(facing);
        BlockCapabilityCache<IFluidHandler, @Nullable Direction> cache = capCaches.get(facing);
        if (cache == null && level instanceof ServerLevel serverLevel) {
            cache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, serverLevel, targetPos,
                facing.getOpposite());
            capCaches.put(facing, cache);
        }

        if (cache != null) {
            return cache.getCapability();
        }

        return level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, facing.getOpposite());
    }

    @Override
    public void invalidate() {
        capCaches.clear();
        super.invalidate();
    }

    private class TransporterFluidHandler implements IFluidHandler {

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
            return canAcceptFluid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler handler = getInternalHandler();
            if (handler == null || !canAcceptFluid(resource)) {
                return 0;
            }
            return handler.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler handler = getInternalHandler();
            return handler == null ? FluidStack.EMPTY : handler.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler handler = getInternalHandler();
            return handler == null ? FluidStack.EMPTY : handler.drain(maxDrain, action);
        }

        private @Nullable IFluidHandler getInternalHandler() {
            return internalTank == null ? null : internalTank.getCapability();
        }
    }

    private static class WaterloggedLeavesFluidHandler implements IFluidHandler {
        private static final WaterloggedLeavesFluidHandler INSTANCE = new WaterloggedLeavesFluidHandler();
        private static final FluidStack WATER = new FluidStack(Fluids.WATER, 1000);

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return WATER.copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) {
                return FluidStack.EMPTY;
            }
            return WATER.copyWithAmount(Math.min(resource.getAmount(), WATER.getAmount()));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            return WATER.copyWithAmount(Math.min(maxDrain, WATER.getAmount()));
        }
    }
}
