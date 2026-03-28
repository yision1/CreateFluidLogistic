package com.yision.fluidlogistics.block.FluidTransporter;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class FluidTransporterBlock extends Block implements IWrenchable, IBE<FluidTransporterBlockEntity>,
    ProperWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final EnumMap<Direction, VoxelShape> SHAPES = buildShapes();

    public FluidTransporterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.DOWN)
            .setValue(POWERED, false)
            .setValue(WATERLOGGED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!FeatureToggle.isEnabled(FeatureToggle.FLUID_TRANSPORTER)) {
            return null;
        }

        BlockState state = defaultBlockState();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean waterlogged = level.getFluidState(pos).getType() == Fluids.WATER;
        Direction preferredFacing = null;

        for (Direction face : context.getNearestLookingDirections()) {
            BlockPos adjacentPos = pos.relative(face);
            IFluidHandler sidedHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, adjacentPos,
                face.getOpposite());
            IFluidHandler handler = sidedHandler != null ? sidedHandler
                : level.getCapability(Capabilities.FluidHandler.BLOCK, adjacentPos, null);
            if (handler != null) {
                preferredFacing = face.getOpposite();
                break;
            }
        }

        if (preferredFacing == null) {
            preferredFacing = context.getNearestLookingDirection().getOpposite();
        }

        return state.setValue(FACING, preferredFacing)
            .setValue(POWERED, level.hasNeighborSignal(pos))
            .setValue(WATERLOGGED, waterlogged);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, POWERED, WATERLOGGED));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
        BlockPos currentPos, BlockPos neighborPos) {
        updateWater(level, state, currentPos);
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
        boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        if (!level.getBlockTicks().willTickThisTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public Class<FluidTransporterBlockEntity> getBlockEntityClass() {
        return FluidTransporterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidTransporterBlockEntity> getBlockEntityType() {
        return AllBlockEntities.FLUID_TRANSPORTER.get();
    }

    private static EnumMap<Direction, VoxelShape> buildShapes() {
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            shapes.put(direction, buildShape(direction));
        }
        return shapes;
    }

    private static VoxelShape buildShape(Direction facing) {
        VoxelShape shape = Shapes.empty();
        shape = Shapes.or(shape, rotatedBox(facing, 1, 7, 1, 15, 10, 15));
        shape = Shapes.or(shape, rotatedBox(facing, 0, 10, 0, 16, 16, 16));
        shape = Shapes.or(shape, rotatedBox(facing, 1.925, 4.05, 1.925, 14.075, 8, 14.075));
        shape = Shapes.or(shape, rotatedBox(facing, 2, 0, 2, 14, 3, 14));
        shape = Shapes.or(shape, rotatedBox(facing, 2, 3, 2, 14, 5, 14));
        shape = Shapes.or(shape, rotatedBox(facing, 0.05, 4, 5, 15.95, 10, 11));
        shape = Shapes.or(shape, rotatedBox(facing, 5, 4, 0.05, 11, 10, 15.95));
        return shape;
    }

    private static VoxelShape rotatedBox(Direction facing, double minX, double minY, double minZ, double maxX,
        double maxY, double maxZ) {
        double[] xs = {minX, maxX};
        double[] ys = {minY, maxY};
        double[] zs = {minZ, maxZ};
        double outMinX = 16;
        double outMinY = 16;
        double outMinZ = 16;
        double outMaxX = 0;
        double outMaxY = 0;
        double outMaxZ = 0;

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    double[] point = transformPoint(facing, x, y, z);
                    outMinX = Math.min(outMinX, point[0]);
                    outMinY = Math.min(outMinY, point[1]);
                    outMinZ = Math.min(outMinZ, point[2]);
                    outMaxX = Math.max(outMaxX, point[0]);
                    outMaxY = Math.max(outMaxY, point[1]);
                    outMaxZ = Math.max(outMaxZ, point[2]);
                }
            }
        }

        return Block.box(outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ);
    }

    private static double[] transformPoint(Direction facing, double x, double y, double z) {
        return switch (facing) {
            case DOWN -> new double[] {x, y, z};
            case UP -> new double[] {x, 16 - y, 16 - z};
            case SOUTH -> new double[] {x, z, 16 - y};
            case NORTH -> rotateY180(x, z, 16 - y);
            case EAST -> rotateY270(x, z, 16 - y);
            case WEST -> rotateY90(x, z, 16 - y);
        };
    }

    private static double[] rotateY90(double x, double y, double z) {
        return new double[] {16 - z, y, x};
    }

    private static double[] rotateY180(double x, double y, double z) {
        return new double[] {16 - x, y, 16 - z};
    }

    private static double[] rotateY270(double x, double y, double z) {
        return new double[] {z, y, 16 - x};
    }
}
