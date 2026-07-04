package com.yision.fluidlogistics.content.fluids.fluidPump;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.yision.fluidlogistics.config.Config;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FluidPumpNetworkUpdater {

	private static final Map<ResourceKey<Level>, Integer> LOADED_FLUID_PUMPS = new HashMap<>();

	public static void onFluidPumpLoaded(Level level) {
		if (level.isClientSide)
			return;
		LOADED_FLUID_PUMPS.merge(level.dimension(), 1, Integer::sum);
	}

	public static void onFluidPumpUnloaded(Level level) {
		if (level.isClientSide)
			return;
		LOADED_FLUID_PUMPS.computeIfPresent(level.dimension(), ($, count) -> count <= 1 ? null : count - 1);
	}

	public static void clearLoadedFluidPumpCounts() {
		LOADED_FLUID_PUMPS.clear();
	}

	private static boolean shouldRun(LevelAccessor world) {
		if (!Config.isFluidPumpEnabled())
			return false;
		if (!(world instanceof Level level) || level.isClientSide)
			return false;
		return LOADED_FLUID_PUMPS.getOrDefault(level.dimension(), 0) > 0;
	}

	public static void propagateChangedPipeForFluidPumps(LevelAccessor world, BlockPos pipePos, BlockState pipeState) {
		if (!shouldRun(world))
			return;

		Queue<Pair<Integer, BlockPos>> frontier = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		Set<Pair<FluidPumpBlockEntity, Direction>> discoveredPumps = new HashSet<>();

		frontier.add(Pair.of(0, pipePos));

		while (!frontier.isEmpty()) {
			Pair<Integer, BlockPos> pair = frontier.poll();
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
				if (blockEntity instanceof PumpBlockEntity) {
					if (blockEntity instanceof FluidPumpBlockEntity fluidPump
						&& targetState.getBlock() instanceof FluidPumpBlock
						&& FluidPumpBlock.getFluidAxis(targetState) == direction.getAxis())
						discoveredPumps.add(Pair.of(fluidPump, direction.getOpposite()));
					continue;
				}
				if (visited.contains(target))
					continue;
				FluidTransportBehaviour targetPipe = FluidPropagator.getPipe(world, target);
				if (targetPipe == null)
					continue;
				Integer distance = pair.getFirst();
				if (distance >= Config.getFluidPumpRange() && !hasAnyInitializedPressure(targetPipe))
					continue;
				if (targetPipe.canHaveFlowToward(targetState, direction.getOpposite()))
					frontier.add(Pair.of(distance + 1, target));
			}
		}

		discoveredPumps.forEach(p -> p.getFirst().updatePipesOnSide(p.getSecond()));
	}

	private static boolean hasAnyInitializedPressure(FluidTransportBehaviour pipe) {
		if (pipe.interfaces == null)
			return false;
		for (PipeConnection connection : pipe.interfaces.values())
			if (connection.hasPressure())
				return true;
		return false;
	}
}
