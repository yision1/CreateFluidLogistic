package com.yision.fluidlogistics.content.fluids.fluidPump;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

import net.neoforged.neoforge.capabilities.Capabilities;

public class FluidPumpBlock extends PumpBlock {

	private static final double[][] HORIZONTAL_MODEL_BOXES = {
		{2, 2, 2, 14, 14, 14},
		{2, 2, 13, 14, 14, 14},
		{3, 0, 3, 13, 2, 13},
		{3, 14, 3, 13, 16, 13},
		{3, 1, 0, 6, 15, 2},
		{10, 1, 0, 13, 15, 2},
		{4, 4, 14, 12, 12, 15},
	};
	private static final double[][] VERTICAL_MODEL_BOXES = {
		{2, 2, 2, 14, 14, 14},
		{2, 2, 13, 14, 14, 14},
		{14, 3, 3, 16, 13, 13},
		{0, 3, 3, 2, 13, 13},
		{1, 3, 0, 15, 6, 2},
		{1, 10, 0, 15, 13, 2},
		{4, 4, 14, 12, 12, 15},
	};
	private static final double[] SHAFT_BOX = {6, 6, 0, 10, 10, 16};
	private static final VoxelShape[][] SHAPES = makeShapes();

	public FluidPumpBlock(Properties p_i48415_1_) {
		super(p_i48415_1_);
		registerDefaultState(defaultBlockState()
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
	}

	public static Axis getFluidAxis(BlockState state) {
		Axis topAxis = state.getValue(FACING).getAxis();
		Axis shaftAxis = getShaftAxis(state);
		return getRemainingAxis(topAxis, shaftAxis);
	}

	public static Axis getShaftAxis(BlockState state) {
		return getShaftAxis(state.getValue(FACING)
			.getAxis(), state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE));
	}

	private static Axis getShaftAxis(Axis facingAxis, boolean alongFirst) {
		if (facingAxis == Axis.X)
			return alongFirst ? Axis.Y : Axis.Z;
		if (facingAxis == Axis.Y)
			return alongFirst ? Axis.X : Axis.Z;
		if (facingAxis == Axis.Z)
			return alongFirst ? Axis.X : Axis.Y;

		throw new IllegalStateException("Unknown axis.");
	}

	public static Direction getVisualOutputDirection(BlockState state) {
		Direction direction = state.getValue(FACING);
		boolean alongFirst = state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
		boolean vertical = isVerticalModel(direction, alongFirst);
		int xRot = getXRotation(direction);
		int yRot = getYRotation(direction, alongFirst);
		return rotateDirection(vertical ? Direction.EAST : Direction.UP, xRot, yRot);
	}

	public static Direction getFluidDirection(BlockState state, AxisDirection selectedDirection) {
		Direction positive = getVisualOutputDirection(state);
		return selectedDirection == AxisDirection.POSITIVE ? positive : positive.getOpposite();
	}

	public static AxisDirection toSelectedDirection(BlockState state, Direction worldDirection) {
		Direction positive = getVisualOutputDirection(state);
		return worldDirection == positive ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
	}

	public static float getValueBoxZRotation(BlockState state, Direction outputDir) {
		Direction top = getModelTop(state);
		float yRot = AngleHelper.horizontalAngle(top) + 180;
		float xRot = top == Direction.UP ? 90 : top == Direction.DOWN ? 270 : 0;
		Vec3 currentRight = rotateValueBoxAxis(new Vec3(1, 0, 0), xRot, yRot).normalize();
		Vec3 currentBottom = rotateValueBoxAxis(new Vec3(0, -1, 0), xRot, yRot).normalize();
		Vec3 desiredRight = Vec3.atLowerCornerOf(outputDir.getOpposite()
			.getNormal());

		double sin = -desiredRight.dot(currentBottom);
		double cos = desiredRight.dot(currentRight);
		if (Math.abs(sin) < 1e-6 && Math.abs(cos) < 1e-6)
			return 0;
		return (float) Math.toDegrees(Math.atan2(sin, cos));
	}

	private static Vec3 rotateValueBoxAxis(Vec3 vec, float xRot, float yRot) {
		return VecHelper.rotate(VecHelper.rotate(vec, xRot, Direction.Axis.X), yRot, Direction.Axis.Y);
	}

	private static boolean isVerticalModel(Direction direction, boolean alongFirst) {
		return direction.getAxis()
			.isHorizontal() && (direction.getAxis() == Axis.X) == alongFirst;
	}

	private static int getXRotation(Direction direction) {
		return direction == Direction.DOWN ? 270 : direction == Direction.UP ? 90 : 0;
	}

	private static int getYRotation(Direction direction, boolean alongFirst) {
		return direction.getAxis()
			.isVertical() ? alongFirst ? 180 : 90 : (int) direction.toYRot();
	}

	private static Direction rotateDirection(Direction direction, int xRot, int yRot) {
		Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
		normal = rotateVectorX(normal, xRot);
		normal = rotateVectorY(normal, yRot);
		return Direction.getNearest(normal.x, normal.y, normal.z);
	}

	private static Vec3 rotateVectorX(Vec3 point, int degrees) {
		int normalized = Math.floorMod(degrees, 360);
		return switch (normalized) {
			case 90 -> new Vec3(point.x, point.z, -point.y);
			case 180 -> new Vec3(point.x, -point.y, -point.z);
			case 270 -> new Vec3(point.x, -point.z, point.y);
			default -> point;
		};
	}

	private static Vec3 rotateVectorY(Vec3 point, int degrees) {
		int normalized = Math.floorMod(degrees, 360);
		return switch (normalized) {
			case 90 -> new Vec3(-point.z, point.y, point.x);
			case 180 -> new Vec3(-point.x, point.y, -point.z);
			case 270 -> new Vec3(point.z, point.y, -point.x);
			default -> point;
		};
	}

	public static Direction getModelTop(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return getShaftAxis(state);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == getShaftAxis(state);
	}

	@Override
	public boolean isSmallCog() {
		return false;
	}

	@Override
	public boolean isLargeCog() {
		return false;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return getModelShape(state);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return getModelShape(state);
	}

	public static boolean isOpenAt(BlockState state, Direction direction) {
		return direction.getAxis() == getFluidAxis(state);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState toPlace = ProperWaterloggedBlock.withWater(context.getLevel(), defaultBlockState(),
			context.getClickedPos());
		Direction targetOutputDirection = getTargetFluidDirection(context);
		Direction topDirection = chooseModelTop(context, targetOutputDirection.getAxis());

		return getPlacementStateForOutput(toPlace, targetOutputDirection, topDirection);
	}

	private Direction getTargetFluidDirection(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		boolean isShiftKeyDown = context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown();

		Axis preferredAxis = getPreferredFluidConnectionAxis(level, pos, null);
		Axis placementAxis = preferredAxis != null ? preferredAxis : getPlacedFluidAxis(context);
		Direction nearestLookingDirection = getNearestLookingDirectionOnAxis(context, placementAxis);
		Direction targetDirection = isShiftKeyDown ? nearestLookingDirection : nearestLookingDirection.getOpposite();

		if (preferredAxis != null && !isShiftKeyDown)
			return getBestConnectedDirectionOnAxis(context, preferredAxis, targetDirection);

		return targetDirection;
	}

	private Axis getPlacedFluidAxis(BlockPlaceContext context) {
		if (context.getClickedFace()
			.getAxis()
			.isHorizontal())
			return Axis.Y;
		return getNearestHorizontalLookingDirection(context).getAxis();
	}

	private Direction getNearestHorizontalLookingDirection(BlockPlaceContext context) {
		for (Direction direction : context.getNearestLookingDirections())
			if (direction.getAxis()
				.isHorizontal())
				return direction;
		return context.getHorizontalDirection();
	}

	private Direction getNearestLookingDirectionOnAxis(BlockPlaceContext context, Axis axis) {
		for (Direction direction : context.getNearestLookingDirections())
			if (direction.getAxis() == axis)
				return direction;
		return Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE);
	}

	private Direction getBestConnectedDirectionOnAxis(BlockPlaceContext context, Axis preferredAxis,
													 Direction targetDirection) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Direction bestConnectedDirection = null;
		double bestDistance = Double.MAX_VALUE;

		for (Direction d : Iterate.directions) {
			if (d.getAxis() != preferredAxis)
				continue;
			BlockPos adjPos = pos.relative(d);
			BlockState adjState = level.getBlockState(adjPos);
			if (!canConnectFluidPortTo(level, adjPos, adjState, d))
				continue;
			double distance = Vec3.atLowerCornerOf(d.getNormal())
				.distanceTo(Vec3.atLowerCornerOf(targetDirection.getNormal()));
			if (distance > bestDistance)
				continue;
			bestDistance = distance;
			bestConnectedDirection = d;
		}

		if (bestConnectedDirection != null)
			return bestConnectedDirection;

		for (Direction direction : context.getNearestLookingDirections())
			if (direction.getAxis() == preferredAxis)
				return direction;

		return Direction.fromAxisAndDirection(preferredAxis, targetDirection.getAxisDirection());
	}

	private boolean canConnectFluidPortTo(Level level, BlockPos pos, BlockState state, Direction direction) {
		if (FluidPipeBlock.canConnectTo(level, pos, state, direction))
			return true;
		return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null;
	}

	private Direction chooseModelTop(BlockPlaceContext context, Axis fluidAxis) {
		if (fluidAxis == Axis.Y)
			return chooseVerticalModelTop(context);
		return context.getClickedFace() == Direction.DOWN ? Direction.DOWN : Direction.UP;
	}

	private Direction chooseVerticalModelTop(BlockPlaceContext context) {
		if (context.getClickedFace()
			.getAxis()
			.isHorizontal())
			return context.getClickedFace();
		return getNearestHorizontalLookingDirection(context).getOpposite();
	}

	private static BlockState getPlacementStateForOutput(BlockState state, Direction targetOutputDirection,
													  Direction preferredTopDirection) {
		Axis shaftAxis = getShaftAxisForPlacement(targetOutputDirection.getAxis(), preferredTopDirection);
		return withTopAndShaft(state, preferredTopDirection, shaftAxis);
	}

	private static BlockState withTopAndShaft(BlockState state, Direction topDirection, Axis shaftAxis) {
		Axis topAxis = topDirection.getAxis();
		boolean alongFirst = computeAlongFirst(topAxis, shaftAxis);

		return state.setValue(FACING, topDirection)
			.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, alongFirst);
	}

	private static Axis getShaftAxisForPlacement(Axis fluidAxis, Direction topDirection) {
		return getRemainingAxis(fluidAxis, topDirection.getAxis());
	}

	private static Axis getRemainingAxis(Axis first, Axis second) {
		for (Axis axis : Iterate.axes)
			if (axis != first && axis != second)
				return axis;
		throw new IllegalStateException("Impossible axis.");
	}

	private static boolean computeAlongFirst(Axis facingAxis, Axis shaftAxis) {
		if (facingAxis == Axis.X)
			return shaftAxis == Axis.Y;
		if (facingAxis == Axis.Y)
			return shaftAxis == Axis.X;
		if (facingAxis == Axis.Z)
			return shaftAxis == Axis.X;
		throw new IllegalStateException("Unknown axis.");
	}

	private static VoxelShape getModelShape(BlockState state) {
		Direction direction = state.getValue(FACING);
		boolean alongFirst = state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
		return SHAPES[alongFirst ? 1 : 0][direction.ordinal()];
	}

	private static VoxelShape[][] makeShapes() {
		VoxelShape[][] shapes = new VoxelShape[2][Direction.values().length];
		for (boolean alongFirst : Iterate.trueAndFalse) {
			for (Direction direction : Iterate.directions) {
				boolean vertical = isVerticalModel(direction, alongFirst);
				int xRot = getXRotation(direction);
				int yRot = getYRotation(direction, alongFirst);
				shapes[alongFirst ? 1 : 0][direction.ordinal()] =
					Shapes.or(makeShape(vertical ? VERTICAL_MODEL_BOXES : HORIZONTAL_MODEL_BOXES, xRot, yRot),
						makeShaftShape(getShaftAxis(direction.getAxis(), alongFirst)))
						.optimize();
			}
		}
		return shapes;
	}

	private static VoxelShape makeShape(double[][] boxes, int xRot, int yRot) {
		VoxelShape shape = Shapes.empty();
		for (double[] box : boxes)
			shape = Shapes.or(shape, rotateBox(box, xRot, yRot));
		return shape.optimize();
	}

	private static VoxelShape makeShaftShape(Axis axis) {
		return switch (axis) {
			case X -> rotateBox(SHAFT_BOX, 0, 90);
			case Y -> rotateBox(SHAFT_BOX, 90, 0);
			case Z -> Block.box(SHAFT_BOX[0], SHAFT_BOX[1], SHAFT_BOX[2], SHAFT_BOX[3], SHAFT_BOX[4], SHAFT_BOX[5]);
		};
	}

	private static VoxelShape rotateBox(double[] box, int xRot, int yRot) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;

		for (int x = 0; x < 2; x++) {
			for (int y = 0; y < 2; y++) {
				for (int z = 0; z < 2; z++) {
					Vec3 rotated = rotatePoint(new Vec3(box[x == 0 ? 0 : 3], box[y == 0 ? 1 : 4], box[z == 0 ? 2 : 5]),
						xRot, yRot);
					minX = Math.min(minX, rotated.x);
					minY = Math.min(minY, rotated.y);
					minZ = Math.min(minZ, rotated.z);
					maxX = Math.max(maxX, rotated.x);
					maxY = Math.max(maxY, rotated.y);
					maxZ = Math.max(maxZ, rotated.z);
				}
			}
		}

		return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static Vec3 rotatePoint(Vec3 point, int xRot, int yRot) {
		Vec3 rotated = point;
		rotated = rotateX(rotated, xRot);
		rotated = rotateY(rotated, yRot);
		return rotated;
	}

	private static Vec3 rotateX(Vec3 point, int degrees) {
		int normalized = Math.floorMod(degrees, 360);
		double x = point.x - 8;
		double y = point.y - 8;
		double z = point.z - 8;
		return switch (normalized) {
			case 90 -> new Vec3(x + 8, z + 8, -y + 8);
			case 180 -> new Vec3(x + 8, -y + 8, -z + 8);
			case 270 -> new Vec3(x + 8, -z + 8, y + 8);
			default -> point;
		};
	}

	private static Vec3 rotateY(Vec3 point, int degrees) {
		int normalized = Math.floorMod(degrees, 360);
		double x = point.x - 8;
		double y = point.y - 8;
		double z = point.z - 8;
		return switch (normalized) {
			case 90 -> new Vec3(-z + 8, y + 8, x + 8);
			case 180 -> new Vec3(-x + 8, y + 8, -z + 8);
			case 270 -> new Vec3(z + 8, y + 8, -x + 8);
			default -> point;
		};
	}

	@Override
	public void neighborChanged(BlockState state, Level world, BlockPos pos, Block otherBlock, BlockPos neighborPos,
								boolean isMoving) {
		DebugPackets.sendNeighborsUpdatePacket(world, pos);
		Direction d = FluidPropagator.validateNeighbourChange(state, world, pos, otherBlock, neighborPos, isMoving);
		if (d == null)
			return;
		if (!isOpenAt(state, d))
			return;
		world.scheduleTick(pos, this, 1, TickPriority.HIGH);
	}

	@Override
	public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, world, pos, oldState, isMoving);
		if (world.isClientSide)
			return;
		if (state != oldState)
			world.scheduleTick(pos, this, 1, TickPriority.HIGH);

		if (state.getBlock() instanceof FluidPumpBlock && oldState.getBlock() instanceof FluidPumpBlock
			&& getFluidAxis(state) == getFluidAxis(oldState)
			&& getVisualOutputDirection(state) == getVisualOutputDirection(oldState).getOpposite()) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof FluidPumpBlockEntity pump) {
				pump.setPressureUpdate(true);
			}
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide)
			return;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof FluidPumpBlockEntity pump))
			return;

		Direction targetOutputDirection = getInitialOutputDirection(level, pos, state, placer);
		pump.setInitialOutputDirection(targetOutputDirection);
	}

	private Direction getInitialOutputDirection(Level level, BlockPos pos, BlockState state, LivingEntity placer) {
		Axis fluidAxis = getFluidAxis(state);
		boolean isShiftKeyDown = placer instanceof Player player && player.isShiftKeyDown();
		Direction targetDirection = getNearestLookingDirectionOnAxis(placer, fluidAxis);
		targetDirection = isShiftKeyDown ? targetDirection : targetDirection.getOpposite();

		Axis preferredAxis = getPreferredFluidConnectionAxis(level, pos, fluidAxis);
		if (preferredAxis != null && preferredAxis != targetDirection.getAxis() && !isShiftKeyDown)
			return getBestConnectedDirectionOnAxis(level, pos, preferredAxis, targetDirection);

		return targetDirection;
	}

	private Axis getPreferredFluidConnectionAxis(Level level, BlockPos pos, Axis allowedAxis) {
		Axis preferredAxis = null;
		for (Direction d : Iterate.directions) {
			if (allowedAxis != null && d.getAxis() != allowedAxis)
				continue;
			BlockPos adjPos = pos.relative(d);
			BlockState adjState = level.getBlockState(adjPos);
			if (!canConnectFluidPortTo(level, adjPos, adjState, d))
				continue;
			if (preferredAxis != null && preferredAxis != d.getAxis())
				return null;
			preferredAxis = d.getAxis();
		}
		return preferredAxis;
	}

	private Direction getNearestLookingDirectionOnAxis(LivingEntity placer, Axis axis) {
		if (placer != null)
			for (Direction direction : Direction.orderedByNearest(placer))
				if (direction.getAxis() == axis)
					return direction;
		return Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE);
	}

	private Direction getBestConnectedDirectionOnAxis(Level level, BlockPos pos, Axis preferredAxis,
													 Direction targetDirection) {
		Direction bestConnectedDirection = null;
		double bestDistance = Double.MAX_VALUE;

		for (Direction d : Iterate.directions) {
			if (d.getAxis() != preferredAxis)
				continue;
			BlockPos adjPos = pos.relative(d);
			BlockState adjState = level.getBlockState(adjPos);
			if (!canConnectFluidPortTo(level, adjPos, adjState, d))
				continue;
			double distance = Vec3.atLowerCornerOf(d.getNormal())
				.distanceTo(Vec3.atLowerCornerOf(targetDirection.getNormal()));
			if (distance > bestDistance)
				continue;
			bestDistance = distance;
			bestConnectedDirection = d;
		}

		return bestConnectedDirection != null ? bestConnectedDirection : targetDirection;
	}

	@Override
	public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource r) {
		FluidPropagator.propagateChangedPipe(world, pos, state);
		FluidPumpNetworkUpdater.propagateChangedPipeForFluidPumps(world, pos, state);
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
		boolean blockTypeChanged = !state.is(newState.getBlock());
		if (blockTypeChanged && !world.isClientSide) {
			FluidPropagator.propagateChangedPipe(world, pos, state);
			FluidPumpNetworkUpdater.propagateChangedPipeForFluidPumps(world, pos, state);
		}
		super.onRemove(state, world, pos, newState, isMoving);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public Class getBlockEntityClass() {
		return FluidPumpBlockEntity.class;
	}

	@Override
	public net.minecraft.world.level.block.entity.BlockEntityType getBlockEntityType() {
		return AllBlockEntities.FLUID_PUMP.get();
	}

	@Override
	public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
		return originalState.setValue(FACING, originalState.getValue(FACING).getOpposite());
	}
}
