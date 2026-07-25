package com.yision.fluidlogistics.content.equipment.handPointer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

import com.yision.fluidlogistics.api.handpointer.crafter.HandPointerCrafterAdapter;
import com.yision.fluidlogistics.api.handpointer.crafter.HandPointerCrafterAdapters;
import com.yision.fluidlogistics.api.handpointer.crafter.HandPointerCrafterAdapters.RegisteredAdapter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class MechanicalCrafterConnectionPlanner {

    public static final int MAX_CRAFTERS = 64;
    private static final double MAX_SELECTION_DISTANCE_SQR = 64.0D;

    public enum Failure {
        NONE,
        SAME_CRAFTER,
        DIFFERENT_PLANE,
        TOO_MANY_CRAFTERS,
        NOT_LOADED,
        NOT_CRAFTER,
        CRAFTER_TYPE_MISMATCH,
        FACING_MISMATCH,
        INVALID_CONNECTION,
        NO_OUTPUT
    }

    public enum GroupState {
        CONNECTED,
        DISCONNECTED,
        MIXED
    }

    public enum ApplyResult {
        APPLIED,
        ALREADY_IN_STATE,
        INVALID_SELECTION,
        STATE_CHANGED
    }

    public record Geometry(BlockPos origin, BlockPos terminal, Direction facing, BlockPos min, BlockPos max,
                           List<BlockPos> positions, List<BlockPos> connectionPath, Failure failure) {

        public Geometry {
            positions = List.copyOf(positions);
            connectionPath = List.copyOf(connectionPath);
        }

        public boolean valid() {
            return failure == Failure.NONE;
        }
    }

    public record Routing(Map<BlockPos, Direction> directions, Failure failure) {

        public Routing {
            directions = Map.copyOf(directions);
        }

        public boolean valid() {
            return failure == Failure.NONE;
        }
    }

    public record Plan(Geometry geometry, Routing routing, GroupState groupState,
                       RegisteredAdapter registeredAdapter, Failure failure) {

        public boolean valid() {
            return failure == Failure.NONE && geometry.valid() && routing.valid() && registeredAdapter != null;
        }

        public boolean willConnect() {
            return valid() && groupState != GroupState.CONNECTED;
        }
    }

    private record SelectionBounds(BlockPos min, BlockPos max) {
    }

    private MechanicalCrafterConnectionPlanner() {
    }

    public static boolean isWithinSelectionRange(BlockPos origin, BlockPos target) {
        return origin != null && target != null && origin.distSqr(target) <= MAX_SELECTION_DISTANCE_SQR;
    }

    public static Geometry createGeometry(BlockPos origin, BlockPos terminal, Direction facing) {
        SelectionBounds bounds = selectionBounds(origin, terminal);
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();

        if (origin.equals(terminal)) {
            return invalidGeometry(origin, terminal, facing, min, max, Failure.SAME_CRAFTER);
        }

        Direction.Axis facingAxis = facing.getAxis();
        if (!facingAxis.isHorizontal()
            || facingAxis == Direction.Axis.X && origin.getX() != terminal.getX()
            || facingAxis == Direction.Axis.Z && origin.getZ() != terminal.getZ()) {
            return invalidGeometry(origin, terminal, facing, min, max, Failure.DIFFERENT_PLANE);
        }

        int lateralMin = facingAxis == Direction.Axis.X ? min.getZ() : min.getX();
        int lateralMax = facingAxis == Direction.Axis.X ? max.getZ() : max.getX();
        long lateralSize = (long) lateralMax - lateralMin + 1;
        long verticalSize = (long) max.getY() - min.getY() + 1;
        if (lateralSize * verticalSize > MAX_CRAFTERS) {
            return invalidGeometry(origin, terminal, facing, min, max, Failure.TOO_MANY_CRAFTERS);
        }

        List<BlockPos> positions = new ArrayList<>((int) (lateralSize * verticalSize));
        List<BlockPos> connectionPath = new ArrayList<>(positions.size());
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int lateral = lateralMin; lateral <= lateralMax; lateral++) {
                positions.add(atPlaneCoordinate(facingAxis, origin, lateral, y));
            }

            boolean reverse = (y - min.getY()) % 2 != 0;
            if (reverse) {
                for (int lateral = lateralMax; lateral >= lateralMin; lateral--) {
                    connectionPath.add(atPlaneCoordinate(facingAxis, origin, lateral, y));
                }
            } else {
                for (int lateral = lateralMin; lateral <= lateralMax; lateral++) {
                    connectionPath.add(atPlaneCoordinate(facingAxis, origin, lateral, y));
                }
            }
        }

        return new Geometry(origin.immutable(), terminal.immutable(), facing, min, max,
            positions, connectionPath, Failure.NONE);
    }

    public static Routing createRouting(Geometry geometry, Direction currentTerminalOutput) {
        if (!geometry.valid()) {
            return new Routing(Map.of(), geometry.failure());
        }

        Set<BlockPos> selected = Set.copyOf(geometry.positions());
        Direction output = selectTerminalOutput(geometry, currentTerminalOutput, selected);
        if (output == null) {
            return new Routing(Map.of(), Failure.NO_OUTPUT);
        }

        Map<BlockPos, Direction> directions = new LinkedHashMap<>();
        for (BlockPos pos : geometry.positions()) {
            Direction direction = pos.equals(geometry.terminal())
                ? output
                : directionToward(pos, geometry.terminal(), geometry.facing().getAxis());
            directions.put(pos, direction);
        }
        return new Routing(directions, Failure.NONE);
    }

    public static GroupState classifyConnections(List<BlockPos> positions,
                                                 BiPredicate<BlockPos, BlockPos> connected) {
        boolean anyConnected = false;
        boolean anyDisconnected = false;
        for (int firstIndex = 0; firstIndex < positions.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < positions.size(); secondIndex++) {
                if (connected.test(positions.get(firstIndex), positions.get(secondIndex))) {
                    anyConnected = true;
                } else {
                    anyDisconnected = true;
                }
                if (anyConnected && anyDisconnected) {
                    return GroupState.MIXED;
                }
            }
        }
        return anyConnected ? GroupState.CONNECTED : GroupState.DISCONNECTED;
    }

    public static Plan inspect(Level level, BlockPos origin, BlockPos terminal) {
        if (!level.isLoaded(origin) || !level.isLoaded(terminal)) {
            return invalidPlan(origin, terminal, Direction.NORTH, Failure.NOT_LOADED);
        }

        BlockState originState = level.getBlockState(origin);
        BlockState terminalState = level.getBlockState(terminal);
        Optional<RegisteredAdapter> originCrafter = HandPointerCrafterAdapters.find(level, origin, originState);
        Optional<RegisteredAdapter> terminalCrafter = HandPointerCrafterAdapters.find(level, terminal, terminalState);
        if (originCrafter.isEmpty() || terminalCrafter.isEmpty()) {
            return invalidPlan(origin, terminal, Direction.NORTH, Failure.NOT_CRAFTER);
        }
        RegisteredAdapter registeredAdapter = originCrafter.get();
        if (!registeredAdapter.id().equals(terminalCrafter.get().id())) {
            return invalidPlan(origin, terminal, Direction.NORTH, Failure.CRAFTER_TYPE_MISMATCH);
        }
        HandPointerCrafterAdapter adapter = registeredAdapter.adapter();

        Direction facing = adapter.getFacing(level, origin, originState);
        if (adapter.getFacing(level, terminal, terminalState) != facing) {
            return invalidPlan(origin, terminal, facing, Failure.FACING_MISMATCH);
        }

        Geometry geometry = createGeometry(origin, terminal, facing);
        if (!geometry.valid()) {
            return new Plan(geometry, new Routing(Map.of(), geometry.failure()),
                GroupState.DISCONNECTED, registeredAdapter, geometry.failure());
        }

        for (BlockPos pos : geometry.positions()) {
            if (!level.isLoaded(pos)) {
                return invalidPlan(geometry, Failure.NOT_LOADED);
            }
            BlockState state = level.getBlockState(pos);
            Optional<RegisteredAdapter> selectedCrafter = HandPointerCrafterAdapters.find(level, pos, state);
            if (selectedCrafter.isEmpty()) {
                return invalidPlan(geometry, Failure.NOT_CRAFTER);
            }
            if (!registeredAdapter.id().equals(selectedCrafter.get().id())) {
                return invalidPlan(geometry, Failure.CRAFTER_TYPE_MISMATCH);
            }
            if (adapter.getFacing(level, pos, state) != facing) {
                return invalidPlan(geometry, Failure.FACING_MISMATCH);
            }
        }

        for (int index = 1; index < geometry.connectionPath().size(); index++) {
            BlockPos previous = geometry.connectionPath().get(index - 1);
            BlockPos current = geometry.connectionPath().get(index);
            Direction direction = directionBetween(previous, current);
            if (direction == null || !adapter.canConnect(level, previous, direction)) {
                return invalidPlan(geometry, Failure.INVALID_CONNECTION);
            }
        }

        Routing routing = createRouting(geometry, adapter.getTargetDirection(level, terminal, terminalState));
        if (!routing.valid()) {
            return new Plan(geometry, routing, GroupState.DISCONNECTED, registeredAdapter, routing.failure());
        }

        GroupState groupState = classifyWorldConnections(level, geometry, adapter);
        return new Plan(geometry, routing, groupState, registeredAdapter, Failure.NONE);
    }

    public static ApplyResult apply(Level level, Plan plan, boolean desiredConnected,
                                    Direction terminalOutputDirection) {
        if (plan == null || !plan.valid()) {
            return ApplyResult.INVALID_SELECTION;
        }

        if (desiredConnected) {
            if (plan.groupState() == GroupState.CONNECTED) {
                return ApplyResult.ALREADY_IN_STATE;
            }
            if (!isValidTerminalOutput(plan.geometry(), terminalOutputDirection)) {
                return ApplyResult.STATE_CHANGED;
            }
            HandPointerCrafterAdapter adapter = plan.registeredAdapter().adapter();
            connect(level, plan.geometry().connectionPath(), adapter);
            if (classifyWorldConnections(level, plan.geometry(), adapter) != GroupState.CONNECTED) {
                return ApplyResult.STATE_CHANGED;
            }
            applyRouting(level, plan, terminalOutputDirection, adapter);
            return ApplyResult.APPLIED;
        }

        if (plan.groupState() == GroupState.DISCONNECTED) {
            return ApplyResult.ALREADY_IN_STATE;
        }
        HandPointerCrafterAdapter adapter = plan.registeredAdapter().adapter();
        isolateSelection(level, plan.geometry(), adapter);
        return classifyWorldConnections(level, plan.geometry(), adapter) == GroupState.DISCONNECTED
            ? ApplyResult.APPLIED
            : ApplyResult.STATE_CHANGED;
    }

    public static boolean isValidTerminalOutput(Geometry geometry, Direction output) {
        if (geometry == null || !geometry.valid() || output == null
            || output.getAxis() == geometry.facing().getAxis()) {
            return false;
        }
        BlockPos target = geometry.terminal().relative(output);
        return !geometry.positions().contains(target);
    }

    private static Geometry invalidGeometry(BlockPos origin, BlockPos terminal, Direction facing,
                                            BlockPos min, BlockPos max, Failure failure) {
        return new Geometry(origin.immutable(), terminal.immutable(), facing, min, max,
            List.of(), List.of(), failure);
    }

    private static Plan invalidPlan(BlockPos origin, BlockPos terminal, Direction facing, Failure failure) {
        SelectionBounds bounds = selectionBounds(origin, terminal);
        Geometry geometry = invalidGeometry(
            origin, terminal, facing, bounds.min(), bounds.max(), failure);
        return invalidPlan(geometry, failure);
    }

    private static SelectionBounds selectionBounds(BlockPos origin, BlockPos terminal) {
        return new SelectionBounds(
            new BlockPos(
                Math.min(origin.getX(), terminal.getX()),
                Math.min(origin.getY(), terminal.getY()),
                Math.min(origin.getZ(), terminal.getZ())),
            new BlockPos(
                Math.max(origin.getX(), terminal.getX()),
                Math.max(origin.getY(), terminal.getY()),
                Math.max(origin.getZ(), terminal.getZ())));
    }

    private static Plan invalidPlan(Geometry geometry, Failure failure) {
        return new Plan(geometry, new Routing(Map.of(), failure), GroupState.DISCONNECTED, null, failure);
    }

    private static BlockPos atPlaneCoordinate(Direction.Axis facingAxis, BlockPos plane,
                                              int lateral, int y) {
        return facingAxis == Direction.Axis.X
            ? new BlockPos(plane.getX(), y, lateral)
            : new BlockPos(lateral, y, plane.getZ());
    }

    private static Direction directionToward(BlockPos from, BlockPos terminal, Direction.Axis facingAxis) {
        if (facingAxis == Direction.Axis.X && from.getZ() != terminal.getZ()) {
            return from.getZ() < terminal.getZ() ? Direction.SOUTH : Direction.NORTH;
        }
        if (facingAxis == Direction.Axis.Z && from.getX() != terminal.getX()) {
            return from.getX() < terminal.getX() ? Direction.EAST : Direction.WEST;
        }
        return from.getY() < terminal.getY() ? Direction.UP : Direction.DOWN;
    }

    private static Direction selectTerminalOutput(Geometry geometry, Direction currentOutput,
                                                  Set<BlockPos> selected) {
        LinkedHashSet<Direction> candidates = new LinkedHashSet<>();
        if (currentOutput != null) {
            candidates.add(currentOutput);
            candidates.add(currentOutput.getOpposite());
        }
        candidates.add(Direction.UP);
        candidates.add(Direction.DOWN);
        candidates.add(Direction.NORTH);
        candidates.add(Direction.SOUTH);
        candidates.add(Direction.WEST);
        candidates.add(Direction.EAST);

        for (Direction candidate : candidates) {
            if (candidate.getAxis() == geometry.facing().getAxis()) {
                continue;
            }
            BlockPos target = geometry.terminal().relative(candidate);
            if (selected.contains(target)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        int x = to.getX() - from.getX();
        int y = to.getY() - from.getY();
        int z = to.getZ() - from.getZ();
        if (Math.abs(x) + Math.abs(y) + Math.abs(z) != 1) {
            return null;
        }
        return Direction.fromDelta(x, y, z);
    }

    private static GroupState classifyWorldConnections(Level level, Geometry geometry,
                                                       HandPointerCrafterAdapter adapter) {
        return classifyConnections(
            geometry.positions(), (first, second) -> adapter.areConnected(level, first, second));
    }

    private static void isolateSelection(Level level, Geometry geometry,
                                         HandPointerCrafterAdapter adapter) {
        for (BlockPos pos : geometry.positions()) {
            isolateCrafter(level, pos, adapter);
        }
    }

    private static void isolateCrafter(Level level, BlockPos pos, HandPointerCrafterAdapter adapter) {
        while (true) {
            boolean detached = false;
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = pos.relative(direction);
                if (!adapter.canConnect(level, pos, direction)
                    || !adapter.areConnected(level, pos, neighbour)) {
                    continue;
                }
                adapter.toggleConnection(level, pos, neighbour);
                detached = true;
                break;
            }
            if (!detached) {
                return;
            }
        }
    }

    private static void connect(Level level, List<BlockPos> path, HandPointerCrafterAdapter adapter) {
        for (int index = 1; index < path.size(); index++) {
            BlockPos previous = path.get(index - 1);
            BlockPos current = path.get(index);
            if (!adapter.areConnected(level, previous, current)) {
                adapter.toggleConnection(level, previous, current);
            }
        }
    }

    private static void applyRouting(Level level, Plan plan, Direction terminalOutputDirection,
                                     HandPointerCrafterAdapter adapter) {
        BlockPos terminal = plan.geometry().terminal();
        adapter.setTargetDirection(level, terminal, terminalOutputDirection);

        plan.geometry().positions().stream()
            .filter(pos -> !pos.equals(terminal))
            .sorted(Comparator.comparingInt(pos -> manhattan(pos, terminal)))
            .forEach(pos -> adapter.setTargetDirection(
                level, pos, plan.routing().directions().get(pos)));
    }

    private static int manhattan(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
            + Math.abs(first.getY() - second.getY())
            + Math.abs(first.getZ() - second.getZ());
    }
}
