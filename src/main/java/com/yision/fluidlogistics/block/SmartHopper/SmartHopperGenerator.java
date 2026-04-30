package com.yision.fluidlogistics.block.SmartHopper;

import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.ModelFile;

public class SmartHopperGenerator extends SpecialBlockStateGen {

	@Override
	protected int getXRotation(BlockState state) {
		return 0;
	}

	@Override
	protected int getYRotation(BlockState state) {
		Direction facing = state.getValue(SmartHopperBlock.FACING);
		if (facing == Direction.DOWN) {
			return 0;
		}
		return horizontalAngle(facing) + 180;
	}

	@Override
	public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov,
		BlockState state) {
		Direction facing = state.getValue(SmartHopperBlock.FACING);
		if (facing == Direction.DOWN) {
			return prov.models().getExistingFile(prov.modLoc("block/smart_hopper/fluid_hopper"));
		}
		return prov.models().getExistingFile(prov.modLoc("block/smart_hopper/fluid_hopper_side"));
	}
}