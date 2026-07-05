package com.yision.fluidlogistics.content.processing.copperBasin;

import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CopperBasinBlock extends BasinBlock {

	public CopperBasinBlock(Properties p_i48440_1) {
		super(p_i48440_1);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return super.getStateForPlacement(context);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Class<BasinBlockEntity> getBlockEntityClass() {
		return (Class) CopperBasinBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends BasinBlockEntity> getBlockEntityType() {
		return AllBlockEntities.COPPER_BASIN.get();
	}
}
