package com.yision.fluidlogistics.mixin.fluids;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.yision.fluidlogistics.content.fluids.fluidPump.FluidPumpNetworkUpdater;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(value = FluidPropagator.class, remap = false)
public class FluidPropagatorMixin {

	@Inject(method = "propagateChangedPipe", at = @At("TAIL"))
	private static void fluidlogistics$propagateChangedPipeToFluidPumps(LevelAccessor world, BlockPos pipePos,
		BlockState pipeState, CallbackInfo ci) {
		FluidPumpNetworkUpdater.propagateChangedPipeForFluidPumps(world, pipePos, pipeState);
	}
}
