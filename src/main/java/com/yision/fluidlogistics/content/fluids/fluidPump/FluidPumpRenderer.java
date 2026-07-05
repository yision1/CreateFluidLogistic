package com.yision.fluidlogistics.content.fluids.fluidPump;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class FluidPumpRenderer extends KineticBlockEntityRenderer<FluidPumpBlockEntity> {

	public FluidPumpRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected BlockState getRenderedBlockState(FluidPumpBlockEntity be) {
		return shaft(FluidPumpBlock.getShaftAxis(be.getBlockState()));
	}
}
