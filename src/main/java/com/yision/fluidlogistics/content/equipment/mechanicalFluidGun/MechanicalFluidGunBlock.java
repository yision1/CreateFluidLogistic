package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@SuppressWarnings("deprecation")
public class MechanicalFluidGunBlock extends KineticBlock implements IBE<MechanicalFluidGunBlockEntity>, ICogWheel {

	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final DirectionProperty MOUNT_FACE = DirectionProperty.create("mount_face",
		Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

	public static final TagKey<Block> TARGETS = TagKey.create(
		Registries.BLOCK,
		FluidLogistics.asResource("mechanical_fluid_gun_targets")
	);

	public MechanicalFluidGunBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState()
			.setValue(FACING, Direction.NORTH)
			.setValue(MOUNT_FACE, Direction.UP));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FACING, MOUNT_FACE);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return MechanicalFluidGunMount.getMountFace(state).getAxis();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction mountFace = context.getClickedFace();
		Direction playerDir = context.getHorizontalDirection().getOpposite();
		Direction facing = MechanicalFluidGunMount.resolveFacing(mountFace, playerDir);
		return defaultBlockState()
			.setValue(MOUNT_FACE, mountFace)
			.setValue(FACING, facing);
	}

	@Override
	public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
		Direction mountFace = MechanicalFluidGunMount.getMountFace(state);
		Direction facing = MechanicalFluidGunMount.getFacing(state);
		Direction newMountFace = rotation.rotate(mountFace);
		Direction newFacing = rotation.rotate(facing);
		newFacing = MechanicalFluidGunMount.normalizeFacing(newMountFace, newFacing);
		return state.setValue(MOUNT_FACE, newMountFace).setValue(FACING, newFacing);
	}

	@Override
	public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
		Direction mountFace = MechanicalFluidGunMount.getMountFace(state);
		Direction facing = MechanicalFluidGunMount.getFacing(state);
		Direction newMountFace = mirror.mirror(mountFace);
		Direction newFacing = mirror.mirror(facing);
		newFacing = MechanicalFluidGunMount.normalizeFacing(newMountFace, newFacing);
		return state.setValue(MOUNT_FACE, newMountFace).setValue(FACING, newFacing);
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return false;
	}

	@Override
	public boolean isSmallCog() {
		return true;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return MechanicalFluidGunMount.getShapeForMount(MechanicalFluidGunMount.getMountFace(state));
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return MechanicalFluidGunMount.getShapeForMount(MechanicalFluidGunMount.getMountFace(state));
	}

	@Override
	public Class<MechanicalFluidGunBlockEntity> getBlockEntityClass() {
		return MechanicalFluidGunBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends MechanicalFluidGunBlockEntity> getBlockEntityType() {
		return AllBlockEntities.MECHANICAL_FLUID_GUN.get();
	}

	@Override
	public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
		super.onPlace(state, world, pos, oldState, isMoving);
		withBlockEntityDo(world, pos, MechanicalFluidGunBlockEntity::redstoneUpdate);
	}

	@Override
	public void neighborChanged(BlockState state, Level world, BlockPos pos, Block otherBlock,
	                            BlockPos neighborPos, boolean isMoving) {
		withBlockEntityDo(world, pos, MechanicalFluidGunBlockEntity::redstoneUpdate);
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof MechanicalFluidGunBlockEntity be ? be.getComparatorOutput() : 0;
	}

	public static boolean isTargetTagged(Level level, BlockPos targetPos) {
		return level.isLoaded(targetPos) && level.getBlockState(targetPos).is(TARGETS);
	}

	public static boolean isTargetInRange(BlockPos gunPos, BlockPos targetPos) {
		return gunPos.distSqr(targetPos) <= MechanicalFluidGunBlockEntity.RANGE * MechanicalFluidGunBlockEntity.RANGE;
	}

	public static boolean targetsItemOn(BlockState state) {
		return AllBlocks.DEPOT.has(state) || AllBlocks.BELT.has(state);
	}

	public static boolean isSelectableCandidate(Level level, BlockPos gunPos, BlockPos targetPos) {
		return !gunPos.equals(targetPos)
			&& isTargetTagged(level, targetPos);
	}

	public static boolean isSelectableTarget(Level level, BlockPos gunPos, BlockPos targetPos) {
		return isSelectableCandidate(level, gunPos, targetPos)
			&& isTargetInRange(gunPos, targetPos);
	}
}
