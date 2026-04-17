package com.yision.fluidlogistics.block.MultiFluidAccessPort;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.DirectedDirectionalBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.util.SharedCapacityFluidHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.yision.fluidlogistics.block.MultiFluidAccessPort.MultiFluidAccessPortBlock.ATTACHED;

public class MultiFluidAccessPortBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private final Map<Direction, IFluidHandler> sideCapabilities;
    private Map<Direction, FilteringBehaviour> filters;
    private boolean powered;

    public MultiFluidAccessPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        sideCapabilities = new EnumMap<>(Direction.class);
    }

    @Override
    public void initialize() {
        super.initialize();
        updateConnectedStorage();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        if (filters == null) {
            filters = new EnumMap<>(Direction.class);
        }
        filters.clear();
        addFilterBehaviour(behaviours, OutputSlot.LEFT, getLeftOutputDirection());
        addFilterBehaviour(behaviours, OutputSlot.RIGHT, getRightOutputDirection());
        addFilterBehaviour(behaviours, OutputSlot.BACK, getBackOutputDirection());
    }

    private void addFilterBehaviour(List<BlockEntityBehaviour> behaviours, OutputSlot slot, Direction side) {
        FilteringBehaviour behaviour = new PortFilteringBehaviour(this, new OutputFilterSlot(slot), typeFor(slot));
        createFilter(slot, behaviour);
        filters.put(side, behaviour);
        behaviours.add(behaviour);
    }

    private FilteringBehaviour createFilter(OutputSlot slot, FilteringBehaviour behaviour) {
        behaviour.forFluids();
        behaviour.setLabel(Component.translatable(getFilterLabelKey(slot)).copy());
        behaviour.withCallback($ -> onFilterChanged());
        return behaviour;
    }

    private void onFilterChanged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        notifyUpdate();
        for (Direction side : Direction.values()) {
            BlockEntity be = level.getBlockEntity(worldPosition.relative(side));
            if (be instanceof com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlockEntity packager) {
                packager.triggerStockCheck();
            }
        }
    }

    private String getFilterLabelKey(OutputSlot slot) {
        return switch (slot) {
            case LEFT -> "block.fluidlogistics.multi_fluid_access_port.filter_left";
            case RIGHT -> "block.fluidlogistics.multi_fluid_access_port.filter_right";
            case BACK -> "block.fluidlogistics.multi_fluid_access_port.filter_back";
        };
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AllBlockEntities.MULTI_FLUID_ACCESS_PORT.get(),
            (be, side) -> be.getFluidCapability(side));
    }

    public IFluidHandler getFluidCapability(Direction side) {
        if (side == null || !isOutputSide(side)) {
            return null;
        }
        return sideCapabilities.computeIfAbsent(side, output -> new PortFluidHandler(this, output));
    }

    public void updateConnectedStorage() {
        boolean previouslyPowered = powered;
        Level level = getLevel();
        if (level == null) {
            return;
        }

        powered = level.hasNeighborSignal(worldPosition);
        boolean attached = !powered && getConnectedFluidHandler() != null;
        if (previouslyPowered != powered || getBlockState().getValue(ATTACHED) != attached) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(ATTACHED, attached));
            notifyUpdate();
        }
    }

    private IFluidHandler getConnectedFluidHandler() {
        if (level == null || powered) {
            return null;
        }
        Direction targetDirection = DirectedDirectionalBlock.getTargetDirection(getBlockState());
        BlockPos targetPos = worldPosition.relative(targetDirection);
        IFluidHandler handler =
            level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, targetDirection.getOpposite());
        if (handler instanceof WrappedPortFluidHandler) {
            return null;
        }
        return handler;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (powered) {
            return false;
        }

        IFluidHandler handler = getConnectedFluidHandler();
        if (handler == null) {
            return false;
        }

        if (!hasAnyFluid(handler)) {
            return false;
        }

        if (hasAnyFilter()) {
            return addFilteredTooltip(tooltip, handler);
        }
        return containedFluidTooltip(tooltip, isPlayerSneaking, handler);
    }

    private boolean hasAnyFilter() {
        return hasFilter(getLeftOutputDirection()) || hasFilter(getRightOutputDirection()) || hasFilter(getBackOutputDirection());
    }

    private boolean hasFilter(Direction side) {
        return !getFilterItem(side).isEmpty();
    }

    private boolean addFilteredTooltip(List<Component> tooltip, IFluidHandler handler) {
        List<DisplayedFluid> merged = new ArrayList<>();
        mergeFilteredFluid(merged, handler, getLeftOutputDirection());
        mergeFilteredFluid(merged, handler, getRightOutputDirection());
        mergeFilteredFluid(merged, handler, getBackOutputDirection());
        if (merged.isEmpty()) {
            return false;
        }

        CreateLang.builder()
            .translate("gui.goggles.fluid_container")
            .forGoggles(tooltip);

        boolean added = false;
        for (DisplayedFluid entry : merged) {
            CreateLang.fluidName(entry.stack)
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
            CreateLang.builder()
                .add(CreateLang.number(entry.stack.getAmount())
                    .text("mB")
                    .style(ChatFormatting.GOLD))
                .text(ChatFormatting.GRAY, " / ")
                .add(CreateLang.number(entry.capacity)
                    .text("mB")
                    .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
            added = true;
        }
        return added;
    }

    private void mergeFilteredFluid(List<DisplayedFluid> merged, IFluidHandler handler, Direction side) {
        if (!hasFilter(side)) {
            return;
        }

        DisplayedFluid match = getMatchingDisplayedFluid(side, handler);
        if (match == null) {
            return;
        }

        for (DisplayedFluid existing : merged) {
            if (FluidStack.isSameFluidSameComponents(existing.stack, match.stack)) {
                return;
            }
        }

        merged.add(match);
    }

    private DisplayedFluid getMatchingDisplayedFluid(Direction side, IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = getVisibleFluid(side, handler, tank);
            if (!fluid.isEmpty()) {
                return new DisplayedFluid(fluid.copy(), handler.getTankCapacity(tank));
            }
        }
        return null;
    }

    private boolean hasAnyFluid(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (!handler.getFluidInTank(tank).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private record DisplayedFluid(FluidStack stack, int capacity) {
    }

    private boolean isOutputSide(Direction side) {
        return side == getLeftOutputDirection() || side == getRightOutputDirection() || side == getBackOutputDirection();
    }

    Direction getBackOutputDirection() {
        return DirectedDirectionalBlock.getTargetDirection(getBlockState()).getOpposite();
    }

    Direction getLeftOutputDirection() {
        Direction facing = getBlockState().getValue(MultiFluidAccessPortBlock.FACING);
        return isVerticalTarget() ? facing.getClockWise() : facing.getCounterClockWise();
    }

    Direction getRightOutputDirection() {
        Direction facing = getBlockState().getValue(MultiFluidAccessPortBlock.FACING);
        return isVerticalTarget() ? facing.getCounterClockWise() : facing.getClockWise();
    }

    private boolean isVerticalTarget() {
        return getBlockState().getValue(MultiFluidAccessPortBlock.TARGET) != AttachFace.WALL;
    }

    ItemStack getFilterItem(Direction side) {
        if (filters == null) {
            return ItemStack.EMPTY;
        }
        FilteringBehaviour behaviour = filters.get(side);
        return behaviour == null ? ItemStack.EMPTY : behaviour.getFilter();
    }

    private boolean testFilter(Direction side, FluidStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        FilteringBehaviour behaviour = filters.get(normalizeOutputSide(side));
        return behaviour == null || behaviour.getFilter().isEmpty() || behaviour.test(stack);
    }

    private Direction normalizeOutputSide(Direction side) {
        if (side == null) {
            return null;
        }
        Direction left = getLeftOutputDirection();
        Direction right = getRightOutputDirection();
        Direction back = getBackOutputDirection();
        if (side == left) {
            return left;
        }
        if (side == right) {
            return right;
        }
        if (side == back) {
            return back;
        }
        return null;
    }

    private BehaviourType<?> typeFor(OutputSlot slot) {
        return switch (slot) {
            case LEFT -> PortFilteringBehaviour.LEFT_TYPE;
            case RIGHT -> PortFilteringBehaviour.RIGHT_TYPE;
            case BACK -> PortFilteringBehaviour.BACK_TYPE;
        };
    }

    private FluidStack getVisibleFluid(Direction side, IFluidHandler handler, int tank) {
        if (tank < 0 || tank >= handler.getTanks()) {
            return FluidStack.EMPTY;
        }

        FluidStack fluid = handler.getFluidInTank(tank);
        if (fluid.isEmpty() || !testFilter(side, fluid)) {
            return FluidStack.EMPTY;
        }
        return fluid.copy();
    }

    private FluidStack getFirstVisibleFluid(Direction side, IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = getVisibleFluid(side, handler, tank);
            if (!fluid.isEmpty()) {
                return fluid;
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        powered = tag.getBoolean("Powered");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Powered", powered);
    }

    private interface WrappedPortFluidHandler {
    }

    private static class PortFluidHandler implements IFluidHandler, WrappedPortFluidHandler, SharedCapacityFluidHandler {
        private final MultiFluidAccessPortBlockEntity blockEntity;
        private final Direction side;
        private final ThreadLocal<Boolean> recursionGuard = ThreadLocal.withInitial(() -> false);

        private PortFluidHandler(MultiFluidAccessPortBlockEntity blockEntity, Direction side) {
            this.blockEntity = blockEntity;
            this.side = side;
        }

        private <T> T preventRecursion(Supplier<T> value, T fallback) {
            if (recursionGuard.get()) {
                return fallback;
            }
            recursionGuard.set(true);
            try {
                return value.get();
            } finally {
                recursionGuard.set(false);
            }
        }

        @Override
        public int getTanks() {
            return preventRecursion(() -> {
                IFluidHandler handler = blockEntity.getConnectedFluidHandler();
                return handler == null ? 0 : handler.getTanks();
            }, 0);
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return preventRecursion(() -> {
                IFluidHandler handler = blockEntity.getConnectedFluidHandler();
                return handler == null ? FluidStack.EMPTY : blockEntity.getVisibleFluid(side, handler, tank);
            }, FluidStack.EMPTY);
        }

        @Override
        public int getTankCapacity(int tank) {
            return preventRecursion(() -> {
                IFluidHandler handler = blockEntity.getConnectedFluidHandler();
                if (handler == null || tank < 0 || tank >= handler.getTanks()) {
                    return 0;
                }
                return handler.getTankCapacity(tank);
            }, 0);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return preventRecursion(() -> {
                IFluidHandler handler = blockEntity.getConnectedFluidHandler();
                if (handler == null || tank < 0 || tank >= handler.getTanks() || stack.isEmpty()) {
                    return false;
                }
                if (!blockEntity.testFilter(side, stack)) {
                    return false;
                }

                FluidStack existing = handler.getFluidInTank(tank);
                return existing.isEmpty() || FluidStack.isSameFluidSameComponents(existing, stack);
            }, false);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return preventRecursion(() -> {
                IFluidHandler handler = blockEntity.getConnectedFluidHandler();
                if (handler == null || resource.isEmpty() || !blockEntity.testFilter(side, resource)) {
                    return 0;
                }
                return handler.fill(resource, action);
            }, 0);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return preventRecursion(() -> {
                IFluidHandler handler = blockEntity.getConnectedFluidHandler();
                if (handler == null || resource.isEmpty() || !blockEntity.testFilter(side, resource)) {
                    return FluidStack.EMPTY;
                }
                return handler.drain(resource, action);
            }, FluidStack.EMPTY);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return preventRecursion(() -> {
                IFluidHandler handler = blockEntity.getConnectedFluidHandler();
                if (handler == null || maxDrain <= 0) {
                    return FluidStack.EMPTY;
                }
                FluidStack matching = blockEntity.getFirstVisibleFluid(side, handler);
                if (matching.isEmpty()) {
                    return FluidStack.EMPTY;
                }
                return handler.drain(matching.copyWithAmount(maxDrain), action);
            }, FluidStack.EMPTY);
        }

        @Override
        public boolean canFillAll(List<FluidStack> fluids) {
            return preventRecursion(() -> {
                IFluidHandler handler = blockEntity.getConnectedFluidHandler();
                if (!(handler instanceof SharedCapacityFluidHandler sharedCapacityFluidHandler)) {
                    return false;
                }

                for (FluidStack fluid : fluids) {
                    if (fluid.isEmpty() || !blockEntity.testFilter(side, fluid)) {
                        return false;
                    }
                }

                return sharedCapacityFluidHandler.canFillAll(fluids);
            }, false);
        }
    }

    private static class OutputFilterSlot extends ValueBoxTransform {
        private final OutputSlot slot;

        private OutputFilterSlot(OutputSlot slot) {
            this.slot = slot;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction facing = state.getValue(MultiFluidAccessPortBlock.FACING);
            AttachFace target = state.getValue(MultiFluidAccessPortBlock.TARGET);
            if (target == AttachFace.WALL) {
                Vec3 base = baseTopLocation(facing);
                return base == null ? null : VecHelper.rotateCentered(base, wallRotation(facing), Axis.Y);
            }

            Vec3 base = baseVerticalLocation(facing, target);
            return base == null ? null : VecHelper.rotateCentered(base, verticalRotation(facing), Axis.Y);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            Direction facing = state.getValue(MultiFluidAccessPortBlock.FACING);
            AttachFace target = state.getValue(MultiFluidAccessPortBlock.TARGET);
            if (target == AttachFace.WALL) {
                TransformStack transform = TransformStack.of(ms)
                    .rotateYDegrees(wallRotation(facing))
                    .rotateXDegrees(90);
                if (slot == OutputSlot.LEFT) {
                    transform.rotateZDegrees(90);
                } else if (slot == OutputSlot.RIGHT) {
                    transform.rotateZDegrees(-90);
                }
                return;
            }

            TransformStack.of(ms).rotateYDegrees(verticalRotation(facing) + 180);
        }

        @Override
        public float getScale() {
            return super.getScale() * 0.95f;
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            Vec3 offset = getLocalOffset(level, pos, state);
            if (offset == null) {
                return false;
            }

            AttachFace target = state.getValue(MultiFluidAccessPortBlock.TARGET);
            if (target == AttachFace.WALL) {
                return Math.abs(localHit.y - offset.y) < 3 / 16f
                    && Math.abs(localHit.x - offset.x) < 3 / 16f
                    && Math.abs(localHit.z - offset.z) < 3 / 16f;
            }

            Direction face = state.getValue(MultiFluidAccessPortBlock.FACING);
            if (Math.abs(distanceToFace(localHit, offset, face)) >= 3 / 16f) {
                return false;
            }
            return Math.abs(localHit.x - offset.x) < 3 / 16f
                && Math.abs(localHit.y - offset.y) < 3 / 16f
                && Math.abs(localHit.z - offset.z) < 3 / 16f;
        }

        private Vec3 baseTopLocation(Direction facing) {
            if (slot == OutputSlot.LEFT) {
                return VecHelper.voxelSpace(13, 15.75, 8);
            }
            if (slot == OutputSlot.RIGHT) {
                return VecHelper.voxelSpace(3, 15.75, 8);
            }
            if (slot == OutputSlot.BACK) {
                return VecHelper.voxelSpace(8, 15.75, 3);
            }
            return null;
        }

        private Vec3 baseVerticalLocation(Direction facing, AttachFace target) {
            if (slot == OutputSlot.LEFT) {
                return VecHelper.voxelSpace(3, 8, 15.75);
            }
            if (slot == OutputSlot.RIGHT) {
                return VecHelper.voxelSpace(13, 8, 15.75);
            }
            if (slot == OutputSlot.BACK) {
                return VecHelper.voxelSpace(8, target == AttachFace.FLOOR ? 13 : 3, 15.75);
            }
            return null;
        }

        private float rotationFromFacing(Direction facing) {
            return switch (facing) {
                case NORTH -> 0;
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
        }

        private float wallRotation(Direction facing) {
            float rotation = rotationFromFacing(facing);
            if (facing.getAxis() == Axis.Z) {
                rotation += 180;
            }
            return rotation;
        }

        private float verticalRotation(Direction facing) {
            float rotation = rotationFromFacing(facing);
            if (facing.getAxis() == Axis.Z) {
                rotation += 180;
            }
            return rotation;
        }

        private double distanceToFace(Vec3 hit, Vec3 offset, Direction face) {
            return switch (face) {
                case NORTH, SOUTH -> hit.z - offset.z;
                case EAST, WEST -> hit.x - offset.x;
                default -> 0;
            };
        }

    }

    static ValueBoxTransform createSlotTransform(OutputSlot slot) {
        return new OutputFilterSlot(slot);
    }

    private enum OutputSlot {
        LEFT,
        RIGHT,
        BACK
    }

    private static class PortFilteringBehaviour extends FilteringBehaviour {
        private static final BehaviourType<PortFilteringBehaviour> LEFT_TYPE = new BehaviourType<>();
        private static final BehaviourType<PortFilteringBehaviour> RIGHT_TYPE = new BehaviourType<>();
        private static final BehaviourType<PortFilteringBehaviour> BACK_TYPE = new BehaviourType<>();

        private final BehaviourType<?> type;
        private final String key;
        private final int netId;

        private PortFilteringBehaviour(SmartBlockEntity be, ValueBoxTransform slot, BehaviourType<?> type) {
            super(be, slot);
            this.type = type;
            if (type == LEFT_TYPE) {
                this.key = "FilteringLeft";
                this.netId = 21;
            } else if (type == RIGHT_TYPE) {
                this.key = "FilteringRight";
                this.netId = 22;
            } else {
                this.key = "FilteringBack";
                this.netId = 23;
            }
        }

        @Override
        public BehaviourType<?> getType() {
            return type;
        }

        @Override
        public String getClipboardKey() {
            return key;
        }

        @Override
        public int netId() {
            return netId;
        }

        @Override
        public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            CompoundTag data = new CompoundTag();
            super.write(data, registries, clientPacket);
            nbt.put(key, data);
        }

        @Override
        public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
            if (nbt.contains(key)) {
                super.read(nbt.getCompound(key), registries, clientPacket);
            }
        }

        @Override
        public void writeSafe(CompoundTag nbt, HolderLookup.Provider registries) {
            CompoundTag data = new CompoundTag();
            super.writeSafe(data, registries);
            nbt.put(key, data);
        }
    }
}
