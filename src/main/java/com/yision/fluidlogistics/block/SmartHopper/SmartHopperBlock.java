package com.yision.fluidlogistics.block.SmartHopper;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.yision.fluidlogistics.registry.AllBlockEntities;
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

public class SmartHopperBlock extends Block implements IWrenchable, IBE<SmartHopperBlockEntity>,
	ProperWaterloggedBlock {

	public static final DirectionProperty FACING = BlockStateProperties.FACING_HOPPER;
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	private static final VoxelShape SHAPE_DOWN = Shapes.or(
		Block.box(0, 10, 0, 16, 16, 16),
		Block.box(4, 4, 4, 12, 10, 12),
		Block.box(6, 0, 6, 10, 4, 10)
	);

	private static final VoxelShape SHAPE_NORTH = Shapes.or(
		Block.box(0, 10, 0, 16, 16, 16),
		Block.box(4, 4, 4, 12, 10, 12),
		Block.box(6, 5, 0, 10, 9, 4)
	);

	private static final VoxelShape SHAPE_SOUTH = Shapes.or(
		Block.box(0, 10, 0, 16, 16, 16),
		Block.box(4, 4, 4, 12, 10, 12),
		Block.box(6, 5, 12, 10, 9, 16)
	);

	private static final VoxelShape SHAPE_WEST = Shapes.or(
		Block.box(0, 10, 0, 16, 16, 16),
		Block.box(4, 4, 4, 12, 10, 12),
		Block.box(0, 5, 6, 4, 9, 10)
	);

	private static final VoxelShape SHAPE_EAST = Shapes.or(
		Block.box(0, 10, 0, 16, 16, 16),
		Block.box(4, 4, 4, 12, 10, 12),
		Block.box(12, 5, 6, 16, 9, 10)
	);

	public SmartHopperBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(FACING, Direction.DOWN)
			.setValue(POWERED, false)
			.setValue(WATERLOGGED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		boolean waterlogged = level.getFluidState(pos).getType() == Fluids.WATER;

		Direction preferredFacing = context.getNearestLookingDirection();
		if (preferredFacing == Direction.UP) {
			preferredFacing = Direction.DOWN;
		}

		return defaultBlockState()
			.setValue(FACING, preferredFacing)
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
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
		LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
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
		return switch (state.getValue(FACING)) {
			case NORTH -> SHAPE_NORTH;
			case SOUTH -> SHAPE_SOUTH;
			case WEST -> SHAPE_WEST;
			case EAST -> SHAPE_EAST;
			default -> SHAPE_DOWN;
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
		CollisionContext context) {
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
	public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
		return originalState.setValue(FACING, getNextFacing(originalState.getValue(FACING)));
	}

	private static Direction getNextFacing(Direction facing) {
		return switch (facing) {
			case DOWN -> Direction.NORTH;
			case NORTH -> Direction.EAST;
			case EAST -> Direction.SOUTH;
			case SOUTH -> Direction.WEST;
			default -> Direction.DOWN;
		};
	}

	@Override
	@SuppressWarnings("deprecation")
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public Class<SmartHopperBlockEntity> getBlockEntityClass() {
		return SmartHopperBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends SmartHopperBlockEntity> getBlockEntityType() {
		return AllBlockEntities.SMART_HOPPER.get();
	}
}
