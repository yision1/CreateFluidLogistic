package com.yision.fluidlogistics.content.fluids.fluidPump;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
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

	public static final class PropagationContext {

		private final int pumpRange;
		private final boolean extendsCreateRange;
		private final Queue<Pair<Integer, BlockPos>> frontier = new PriorityQueue<>(
			Comparator.comparingInt(pair -> pair.getFirst()));
		private final Map<BlockPos, Integer> bestDistances = new HashMap<>();
		private final Map<BlockPos, Boolean> pressureBeforeCreate = new HashMap<>();

		private PropagationContext(int pumpRange) {
			this.pumpRange = pumpRange;
			extendsCreateRange = pumpRange > FluidPropagator.getPumpRange();
		}

		public void recordCreatePipe(BlockPos pos, int distance, FluidTransportBehaviour pipe) {
			if (!extendsCreateRange)
				return;
			pressureBeforeCreate.put(pos, hasAnyInitializedPressure(pipe));
			bestDistances.merge(pos, distance, Math::min);
		}

		public boolean shouldContinueFromCreateCutoff(int distance) {
			return extendsCreateRange && distance < pumpRange;
		}

		public void addCutoff(BlockPos pos, int distance) {
			frontier.add(Pair.of(distance, pos));
		}

		private boolean hadPressureBeforeCreate(BlockPos pos, FluidTransportBehaviour pipe) {
			Boolean pressure = pressureBeforeCreate.get(pos);
			return pressure != null ? pressure : hasAnyInitializedPressure(pipe);
		}
	}

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

	public static PropagationContext getOrCreateContext(LevelAccessor world, PropagationContext context) {
		if (context != null)
			return context;
		return shouldRun(world) ? new PropagationContext(Config.getFluidPumpRange()) : null;
	}

	public static void finishPropagationForFluidPumps(LevelAccessor world, PropagationContext context) {
		if (context == null)
			return;
		if (context.frontier.isEmpty())
			return;
		Set<Pair<FluidPumpBlockEntity, Direction>> discoveredPumps = new HashSet<>();

		while (!context.frontier.isEmpty()) {
			Pair<Integer, BlockPos> pair = context.frontier.poll();
			int distance = pair.getFirst();
			BlockPos currentPos = pair.getSecond();
			Integer bestDistance = context.bestDistances.get(currentPos);
			if (bestDistance != null && bestDistance <= distance)
				continue;
			context.bestDistances.put(currentPos, distance);
			BlockState currentState = world.getBlockState(currentPos);
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
				FluidTransportBehaviour targetPipe = FluidPropagator.getPipe(world, target);
				if (targetPipe == null)
					continue;
				if (distance >= context.pumpRange && !context.hadPressureBeforeCreate(target, targetPipe))
					continue;
				if (!targetPipe.canHaveFlowToward(targetState, direction.getOpposite()))
					continue;
				int nextDistance = distance + 1;
				Integer targetBestDistance = context.bestDistances.get(target);
				if (targetBestDistance != null && targetBestDistance <= nextDistance)
					continue;
				context.frontier.add(Pair.of(nextDistance, target));
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
