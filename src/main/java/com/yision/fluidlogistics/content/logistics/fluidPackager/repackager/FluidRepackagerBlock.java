package com.yision.fluidlogistics.content.logistics.fluidPackager.repackager;

import static com.yision.fluidlogistics.registry.AllBlocks.COPPER_FROGPORT;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.phys.BlockHitResult;

public class FluidRepackagerBlock extends PackagerBlock {

	public FluidRepackagerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
											  Player player, net.minecraft.world.InteractionHand hand,
											  BlockHitResult hitResult) {
		if (COPPER_FROGPORT.isIn(stack))
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
	public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
		return AllBlockEntities.FLUID_REPACKAGER.get();
	}
}
