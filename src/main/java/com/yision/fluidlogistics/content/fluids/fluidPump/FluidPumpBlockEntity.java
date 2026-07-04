package com.yision.fluidlogistics.content.fluids.fluidPump;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter.ScrollOptionSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.yision.fluidlogistics.config.Config;

import com.google.common.collect.ImmutableList;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;

public class FluidPumpBlockEntity extends PumpBlockEntity {

	public static final int RANGE = 24;
	public static final float PRESSURE_MULTIPLIER = 2.0f;

	private boolean directionManuallyConfigured;
	private boolean defaultDirectionInitialized;
	private boolean fluidPumpPressureUpdate;
	private boolean directionReady;
	private AxisDirection selectedFluidDirection = AxisDirection.POSITIVE;
	private ScrollOptionBehaviour<FluidTransferDirection> directionSelector;

	public FluidPumpBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
		super(typeIn, pos, state);
	}

	public void setPressureUpdate(boolean value) {
		this.fluidPumpPressureUpdate = value;
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		super.addBehaviours(behaviours);
		behaviours.removeIf(b -> b instanceof FluidTransportBehaviour
			&& !(b instanceof FluidPumpFluidTransferBehaviour));
		behaviours.add(new FluidPumpFluidTransferBehaviour(this));

		directionSelector = new ScrollOptionBehaviour<>(
			FluidTransferDirection.class,
			Component.translatable("fluidlogistics.fluid_pump.transfer_direction"),
			this, new FluidPumpDirectionSlot()) {
			@Override
			public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
				return new ValueSettingsBoard(label, max, 1, ImmutableList.of(Component.literal("Select")),
					new ScrollOptionSettingsFormatter(FluidTransferDirection.guiOptions()));
			}
		};
		directionSelector.withCallback(this::onDirectionSelected);
		directionSelector.onlyActiveWhen(() -> directionReady);
		syncDirectionSelectorWithBlockState();
		behaviours.add(directionSelector);

		registerAwardables(behaviours, AllAdvancements.PUMP);
	}

	@Override
	public void initialize() {
		super.initialize();
		initializeDefaultDirection();
	}

	private void initializeDefaultDirection() {
		if (level == null || level.isClientSide || defaultDirectionInitialized || directionManuallyConfigured)
			return;

		defaultDirectionInitialized = true;
		AxisDirection target = chooseDefaultOutputDirection();
		selectedFluidDirection = target;
		directionReady = true;
		syncDirectionSelectorWithBlockState();
		updatePressureChange();
		FluidPumpNetworkUpdater.propagateChangedPipeForFluidPumps(level, worldPosition, getBlockState());
		notifyUpdate();
	}

	private AxisDirection chooseDefaultOutputDirection() {
		return FluidPumpBlock.toSelectedDirection(getBlockState(),
			FluidPumpBlock.getVisualOutputDirection(getBlockState()));
	}

	private void syncDirectionSelectorWithBlockState() {
		if (directionSelector == null)
			return;
		FluidTransferDirection target = FluidTransferDirection.fromAxisDirection(getSelectedFluidDirection());
		if (directionSelector.get() != target) {
			directionSelector.value = target.ordinal();
		}
	}

	private void onDirectionSelected(int newOrdinal) {
		if (level == null || level.isClientSide)
			return;

		FluidTransferDirection newDir = FluidTransferDirection.values()[newOrdinal];

		if (newDir.getAxisDirection() == getSelectedFluidDirection())
			return;

		boolean wasManuallyConfigured = directionManuallyConfigured;
		selectedFluidDirection = newDir.getAxisDirection();
		directionManuallyConfigured = true;

		updatePressureChange();
		FluidPumpNetworkUpdater.propagateChangedPipeForFluidPumps(level, worldPosition, getBlockState());

		if (!wasManuallyConfigured && hasSource()) {
			detachKinetics();
			removeSource();
			attachKinetics();
		}

		setChanged();
		sendData();
	}

	private AxisDirection getSelectedFluidDirection() {
		return selectedFluidDirection;
	}

	public void setInitialOutputDirection(Direction outputDirection) {
		if (level == null || level.isClientSide || directionManuallyConfigured)
			return;
		if (outputDirection.getAxis() != FluidPumpBlock.getFluidAxis(getBlockState()))
			return;

		selectedFluidDirection = FluidPumpBlock.toSelectedDirection(getBlockState(), outputDirection);
		defaultDirectionInitialized = true;
		directionReady = true;
		syncDirectionSelectorWithBlockState();
		updatePressureChange();
		FluidPumpNetworkUpdater.propagateChangedPipeForFluidPumps(level, worldPosition, getBlockState());
		notifyUpdate();
	}

	public Direction getEffectiveFront() {
		return FluidPumpBlock.getFluidDirection(getBlockState(), getSelectedFluidDirection());
	}

	@Override
	protected Direction getFront() {
		return getEffectiveFront();
	}

	@Override
	protected boolean isFront(Direction side) {
		return side == getEffectiveFront();
	}

	@Override
	public boolean isPullingOnSide(boolean front) {
		return !front;
	}

	@Override
	public boolean isSideAccessible(Direction side) {
		BlockState blockState = getBlockState();
		if (!(blockState.getBlock() instanceof FluidPumpBlock))
			return false;
		return side.getAxis() == FluidPumpBlock.getFluidAxis(blockState);
	}

	@Override
	public void tick() {
		super.tick();

		if (level.isClientSide && !isVirtual())
			return;

		if (fluidPumpPressureUpdate) {
			fluidPumpPressureUpdate = false;
			updatePressureChange();
		}
	}

	@Override
	protected void distributePressureTo(Direction side) {
		if (getSpeed() == 0)
			return;

		BlockFace start = new BlockFace(worldPosition, side);
		boolean pull = isPullingOnSide(isFront(side));
		Set<BlockFace> targets = new HashSet<>();
		Map<BlockPos, Pair<Integer, Map<Direction, Boolean>>> pipeGraph = new HashMap<>();

		if (!pull)
			FluidPropagator.resetAffectedFluidNetworks(level, worldPosition, side.getOpposite());

		if (!hasReachedValidEndpoint(level, start, pull)) {

			pipeGraph.computeIfAbsent(worldPosition, $ -> Pair.of(0, new IdentityHashMap<>()))
				.getSecond()
				.put(side, pull);
			pipeGraph.computeIfAbsent(start.getConnectedPos(), $ -> Pair.of(1, new IdentityHashMap<>()))
				.getSecond()
				.put(side.getOpposite(), !pull);

			List<Pair<Integer, BlockPos>> frontier = new ArrayList<>();
			Set<BlockPos> visited = new HashSet<>();
			int maxDistance = Config.getFluidPumpRange();
			frontier.add(Pair.of(1, start.getConnectedPos()));

			while (!frontier.isEmpty()) {
				Pair<Integer, BlockPos> entry = frontier.remove(0);
				int distance = entry.getFirst();
				BlockPos currentPos = entry.getSecond();

				if (!level.isLoaded(currentPos))
					continue;
				if (visited.contains(currentPos))
					continue;
				visited.add(currentPos);
				BlockState currentState = level.getBlockState(currentPos);
				FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, currentPos);
				if (pipe == null)
					continue;

				for (Direction face : FluidPropagator.getPipeConnections(currentState, pipe)) {
					BlockFace blockFace = new BlockFace(currentPos, face);
					BlockPos connectedPos = blockFace.getConnectedPos();

					if (!level.isLoaded(connectedPos))
						continue;
					if (blockFace.isEquivalent(start))
						continue;
					if (hasReachedValidEndpoint(level, blockFace, pull)) {
						pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
							.getSecond()
							.put(face, pull);
						targets.add(blockFace);
						continue;
					}

					FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, connectedPos);
					if (pipeBehaviour == null)
						continue;
					if (isPumpTransferBehaviour(pipeBehaviour))
						continue;
					if (visited.contains(connectedPos))
						continue;
					if (distance + 1 >= maxDistance) {
						pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
							.getSecond()
							.put(face, pull);
						targets.add(blockFace);
						continue;
					}

					pipeGraph.computeIfAbsent(currentPos, $ -> Pair.of(distance, new IdentityHashMap<>()))
						.getSecond()
						.put(face, pull);
					pipeGraph.computeIfAbsent(connectedPos, $ -> Pair.of(distance + 1, new IdentityHashMap<>()))
						.getSecond()
						.put(face.getOpposite(), !pull);
					frontier.add(Pair.of(distance + 1, connectedPos));
				}
			}
		}

		Map<Integer, Set<BlockFace>> validFaces = new HashMap<>();
		searchForEndpointRecursively(pipeGraph, targets, validFaces,
			new BlockFace(start.getPos(), start.getOppositeFace()), pull);

		float pressure = Math.abs(getSpeed()) * PRESSURE_MULTIPLIER;
		for (Set<BlockFace> set : validFaces.values()) {
			int parallelBranches = Math.max(1, set.size() - 1);
			for (BlockFace face : set) {
				BlockPos pipePos = face.getPos();
				Direction pipeSide = face.getFace();

				if (pipePos.equals(worldPosition))
					continue;

				boolean inbound = pipeGraph.get(pipePos)
					.getSecond()
					.get(pipeSide);
				FluidTransportBehaviour pipeBehaviour = FluidPropagator.getPipe(level, pipePos);
				if (pipeBehaviour == null)
					continue;

				pipeBehaviour.addPressure(pipeSide, inbound, pressure / parallelBranches);
			}
		}
	}

	private boolean isPumpTransferBehaviour(FluidTransportBehaviour behaviour) {
		if (behaviour instanceof FluidPumpFluidTransferBehaviour)
			return true;
		try {
			Class<?> pumpBehaviourClass = Class.forName("com.simibubi.create.content.fluids.pump.PumpBlockEntity$PumpFluidTransferBehaviour");
			return pumpBehaviourClass.isInstance(behaviour);
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private boolean hasReachedValidEndpoint(LevelAccessor world, BlockFace blockFace, boolean pull) {
		BlockPos connectedPos = blockFace.getConnectedPos();
		BlockState connectedState = world.getBlockState(connectedPos);
		BlockEntity blockEntity = world.getBlockEntity(connectedPos);
		Direction face = blockFace.getFace();

		if (PumpBlock.isPump(connectedState) && getPumpFluidAxis(connectedState) == face.getAxis()
			&& blockEntity instanceof PumpBlockEntity pumpBE) {
			Direction pumpFront = blockEntity instanceof FluidPumpBlockEntity fluidPump
				? fluidPump.getEffectiveFront()
				: connectedState.getValue(PumpBlock.FACING);
			boolean pumpFrontSide = blockFace.getOppositeFace() == pumpFront;
			return pumpBE.isPullingOnSide(pumpFrontSide) != pull;
		}

		FluidTransportBehaviour pipe = FluidPropagator.getPipe(world, connectedPos);
		if (pipe != null && pipe.canHaveFlowToward(connectedState, blockFace.getOppositeFace()))
			return false;

		if (blockEntity != null) {
			if (blockEntity.getLevel()
				.getCapability(Capabilities.FluidHandler.BLOCK, blockEntity.getBlockPos(), face.getOpposite()) != null)
				return true;
		}

		return FluidPropagator.isOpenEnd(world, blockFace.getPos(), face);
	}

	private Axis getPumpFluidAxis(BlockState state) {
		if (state.getBlock() instanceof FluidPumpBlock)
			return FluidPumpBlock.getFluidAxis(state);
		return state.getValue(PumpBlock.FACING).getAxis();
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);
		directionManuallyConfigured = compound.getBoolean("DirectionManuallyConfigured");
		defaultDirectionInitialized = compound.getBoolean("DefaultDirectionInitialized");
		selectedFluidDirection = compound.getBoolean("SelectedFluidDirectionPositive")
			? AxisDirection.POSITIVE
			: AxisDirection.NEGATIVE;
		if (defaultDirectionInitialized || directionManuallyConfigured)
			directionReady = true;
		syncDirectionSelectorWithBlockState();
	}

	@Override
	protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(compound, registries, clientPacket);
		compound.putBoolean("DirectionManuallyConfigured", directionManuallyConfigured);
		compound.putBoolean("DefaultDirectionInitialized", defaultDirectionInitialized);
		compound.putBoolean("SelectedFluidDirectionPositive", selectedFluidDirection == AxisDirection.POSITIVE);
	}

	class FluidPumpFluidTransferBehaviour extends FluidTransportBehaviour {

		public FluidPumpFluidTransferBehaviour(SmartBlockEntity be) {
			super(be);
		}

		@Override
		public void tick() {
			super.tick();
			for (Entry<Direction, PipeConnection> entry : interfaces.entrySet()) {
				boolean pull = isPullingOnSide(isFront(entry.getKey()));
				Couple<Float> pressure = entry.getValue().getPressure();
				pressure.set(pull, Math.abs(getSpeed()) * PRESSURE_MULTIPLIER);
				pressure.set(!pull, 0f);
			}
		}

		@Override
		public boolean canHaveFlowToward(BlockState state, Direction direction) {
			return isSideAccessible(direction);
		}

		@Override
		public AttachmentTypes getRenderedRimAttachment(BlockAndTintGetter world, BlockPos pos, BlockState state,
			Direction direction) {
			AttachmentTypes attachment = super.getRenderedRimAttachment(world, pos, state, direction);
			if (attachment == AttachmentTypes.RIM)
				return AttachmentTypes.NONE;
			return attachment;
		}
	}
}
