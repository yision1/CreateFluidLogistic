package com.yision.fluidlogistics.block.FluidPump;

import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;

public class FluidPumpGenerator {

	public void generate(DataGenContext<Block, ?> ctx, RegistrateBlockstateProvider prov) {
		prov.getVariantBuilder(ctx.getEntry())
			.forAllStates(state -> {
				boolean alongFirst = state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
				Direction direction = state.getValue(DirectionalAxisKineticBlock.FACING);
				boolean vertical = direction.getAxis()
					.isHorizontal() && (direction.getAxis() == Axis.X) == alongFirst;
				int xRot = direction == Direction.DOWN ? 270 : direction == Direction.UP ? 90 : 0;
				int yRot = direction.getAxis()
					.isVertical() ? alongFirst ? 180 : 90 : (int) direction.toYRot();

				return ConfiguredModel.builder()
					.modelFile(getModel(prov, vertical))
					.rotationX(xRot)
					.rotationY(yRot)
					.build();
			});
	}

	private ModelFile getModel(RegistrateBlockstateProvider prov, boolean vertical) {
		return prov.models()
			.getExistingFile(prov.modLoc("block/fluid_pump/" + (vertical ? "block_vertical" : "block_horizontal")));
	}
}
