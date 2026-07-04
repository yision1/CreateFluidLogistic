package com.yision.fluidlogistics.content.logistics.fluidPackager.repackager;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.util.IPackagerOverrideData;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.phys.BlockHitResult;

public class FluidRepackagerBlock extends PackagerBlock {

	public FluidRepackagerBlock(Properties properties) {
		super(properties);
		BlockState defaultBlockState = defaultBlockState();
		registerDefaultState(defaultBlockState.setValue(POWERED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return super.getStateForPlacement(context);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, net.minecraft.world.InteractionHand hand,
											  BlockHitResult hitResult) {
		if (level.getBlockEntity(pos) instanceof FluidRepackagerBlockEntity repackager
			&& repackager.hasStalledPackageReady()) {
			if (!level.isClientSide()) {
				player.getInventory()
					.placeItemBackInInventory(repackager.extractStalledPackage(false));
				AllSoundEvents.playItemPickup(player);
			}
			return ItemInteractionResult.SUCCESS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}

	@Override
	public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
		super.onNeighborChange(state, level, pos, neighbor);
	}

	@Override
	public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos,
								boolean isMoving) {
		if (worldIn.isClientSide)
			return;

		BlockEntity blockEntity = worldIn.getBlockEntity(pos);
		if (blockEntity instanceof IPackagerOverrideData data && data.fluidlogistics$isManualOverrideLocked()) {
			InvManipulationBehaviour behaviour = BlockEntityBehaviour.get(worldIn, pos, InvManipulationBehaviour.TYPE);
			if (behaviour != null)
				behaviour.onNeighborChanged(fromPos);
			return;
		}

		super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
	}

	@Override
	public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
		return AllBlockEntities.FLUID_REPACKAGER.get();
	}
}
