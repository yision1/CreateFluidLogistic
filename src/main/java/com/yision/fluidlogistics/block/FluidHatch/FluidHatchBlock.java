package com.yision.fluidlogistics.block.FluidHatch;

import com.simibubi.create.AllShapes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.capability.IFluidHandler;

@SuppressWarnings("deprecation")
public class FluidHatchBlock extends HorizontalDirectionalBlock
		implements IBE<FluidHatchBlockEntity>, IWrenchable, ProperWaterloggedBlock {

	public FluidHatchBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(FACING, WATERLOGGED));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (!FeatureToggle.isEnabled(FeatureToggle.FLUID_HATCH))
			return null;
		if (context.getClickedFace().getAxis().isVertical())
			return null;

		BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
		return withWater(state, context);
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return fluidState(state);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
		LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		updateWater(level, state, pos);
		return state;
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (!FeatureToggle.isEnabled(FeatureToggle.FLUID_HATCH))
			return InteractionResult.FAIL;
		if (level.isClientSide())
			return InteractionResult.SUCCESS;
		if (player instanceof FakePlayer)
			return InteractionResult.SUCCESS;

		Direction facing = state.getValue(FACING);
		BlockPos targetPos = pos.relative(facing);
		BlockEntity targetBE = level.getBlockEntity(targetPos);
		if (targetBE == null)
			return InteractionResult.FAIL;

		IFluidHandler targetHandler = targetBE.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
		if (targetHandler == null)
			return InteractionResult.FAIL;

		FilteringBehaviour filter = BlockEntityBehaviour.get(level, pos, FilteringBehaviour.TYPE);

		ItemStack heldItem = player.getItemInHand(hand);
		if (heldItem.isEmpty())
			return InteractionResult.PASS;

		boolean success = false;

		if (player.isShiftKeyDown()) {
			success = FluidHatchItemFluidTransfer.tryFillContainerFromTank(
				level, player, hand, targetHandler, targetPos, filter);
			if (!success) {
				success = FluidHatchItemFluidTransfer.tryPourContainerIntoTank(
					level, player, hand, targetHandler, targetPos, filter);
			}
		} else {
			success = FluidHatchItemFluidTransfer.tryPourContainerIntoTank(
				level, player, hand, targetHandler, targetPos, filter);
			if (!success) {
				success = FluidHatchItemFluidTransfer.tryFillContainerFromTank(
					level, player, hand, targetHandler, targetPos, filter);
			}
		}

		if (success) {
			targetBE.setChanged();
			if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				serverLevel.getChunkSource().blockChanged(targetPos);
			}
			AllSoundEvents.ITEM_HATCH.playOnServer(level, pos);
		}

		if (!success && (FluidHatchItemFluidTransfer.canItemBeEmptied(level, heldItem)
			|| FluidHatchItemFluidTransfer.canItemBeFilled(level, heldItem)))
			return InteractionResult.SUCCESS;

		return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return AllShapes.ITEM_HATCH.get(state.getValue(FACING).getOpposite());
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		IBE.onRemove(state, level, pos, newState);
	}

	@Override
	public Class<FluidHatchBlockEntity> getBlockEntityClass() {
		return FluidHatchBlockEntity.class;
	}

	@Override
	public net.minecraft.world.level.block.entity.BlockEntityType<? extends FluidHatchBlockEntity> getBlockEntityType() {
		return AllBlockEntities.FLUID_HATCH.get();
	}

	@Override
	public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
		return false;
	}
}
