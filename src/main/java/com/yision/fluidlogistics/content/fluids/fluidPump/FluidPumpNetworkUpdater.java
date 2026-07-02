package com.yision.fluidlogistics.content.fluids.fluidPump;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.config.FeatureToggle;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FluidPumpNetworkUpdater {

	public static void propagateChangedPipeForFluidPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState) {
		if (!FeatureToggle.isEnabled(FeatureToggle.FLUID_PUMP)) {
			return;
		}
		List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		Set<Pair<FluidPumpBlockEntity, Direction>> discoveredPumps = new HashSet<>();

		frontier.add(Pair.of(0, pipePos));

		while (!frontier.isEmpty()) {
			Pair<Integer, BlockPos> pair = frontier.remove(0);
			BlockPos currentPos = pair.getSecond();
			if (visited.contains(currentPos))
				continue;
			visited.add(currentPos);
			BlockState currentState = currentPos.equals(pipePos) ? pipeState : world.getBlockState(currentPos);
			FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, currentPos);
			if (pipe == null)
				continue;

			for (Direction direction : FluidPropagator.getPipeConnections(currentState, pipe)) {
				BlockPos target = currentPos.relative(direction);
				if (world instanceof Level l && !l.isLoaded(target))
					continue;

				BlockEntity blockEntity = world.getBlockEntity(target);
				BlockState targetState = world.getBlockState(target);
				if (blockEntity instanceof FluidPumpBlockEntity) {
					if (!(targetState.getBlock() instanceof FluidPumpBlock)
						|| FluidPumpBlock.getFluidAxis(targetState) != direction.getAxis())
						continue;
					discoveredPumps.add(Pair.of((FluidPumpBlockEntity) blockEntity, direction.getOpposite()));
					continue;
				}
				if (visited.contains(target))
					continue;
				FluidTransportBehaviour targetPipe = FluidPropagator.getPipe(world, target);
				if (targetPipe == null)
					continue;
				Integer distance = pair.getFirst();
				if (distance >= Config.getFluidPumpRange() && !targetPipe.hasAnyPressure())
					continue;
				if (targetPipe.canHaveFlowToward(targetState, direction.getOpposite()))
					frontier.add(Pair.of(distance + 1, target));
			}
		}

		discoveredPumps.forEach(pair -> pair.getFirst().updatePipesOnSide(pair.getSecond()));
	}
}
