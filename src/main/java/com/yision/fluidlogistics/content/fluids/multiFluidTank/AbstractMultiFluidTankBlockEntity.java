package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.IFluidTank;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public abstract class AbstractMultiFluidTankBlockEntity<T extends AbstractMultiFluidTankBlockEntity<T>>
		extends SmartBlockEntity implements IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid {

	protected static final int MAX_SIZE = 3;
	protected static final int TANKS = 8;
	protected static final int CAPACITY_PER_BLOCK = 16000;
	protected static final int SYNC_RATE = 8;

	protected SmartMultiFluidTank tankInventory;
	protected IFluidHandler fluidCapability;
	protected BlockPos controller;
	protected BlockPos lastKnownPos;
	protected boolean updateConnectivity;
	protected boolean updateCapability;
	public int luminosity;
	protected int width;
	protected int height;
	protected boolean forceFluidLevelUpdate;
	protected int syncCooldown;
	protected boolean queuedSync;

	private LerpedFloat[] fluidLevel;

	protected AbstractMultiFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		tankInventory = createInventory();
		updateConnectivity = false;
		updateCapability = false;
		forceFluidLevelUpdate = true;
		height = 1;
		width = 1;
		refreshCapability();
	}

	protected abstract SmartMultiFluidTank createInventory();

	protected abstract void onControllerFluidChanged(FluidStack[] newFluidStack);

	protected abstract void restoreWindowStateOnRead(CompoundTag compound);

	protected abstract void writeWindowState(CompoundTag compound);

	protected abstract void writeWindowStateSafe(CompoundTag compound);

	@Override
	public void removeController(boolean keepContents) {
		if (level.isClientSide)
			return;
		updateConnectivity = true;
		if (!keepContents)
			applyFluidTankSize(1);
		controller = null;
		width = 1;
		height = 1;
		onWidthRestored();
		onControllerFluidChanged(tankInventory.getFluids());

		BlockState state = getBlockState();
		if (isOwnBlockState(state)) {
			state = applyRemoveControllerShape(state);
			getLevel().setBlock(worldPosition, state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
		}

		refreshCapability();
		setChanged();
		sendData();
	}

	protected abstract BlockState applyRemoveControllerShape(BlockState state);

	public abstract boolean isOwnBlockState(BlockState state);

	@Override
	public abstract T getControllerBE();

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
	}

	@Override
	public void initialize() {
		super.initialize();
		sendData();
		if (level.isClientSide)
			invalidateRenderBoundingBox();
	}

	@Override
	public void tick() {
		super.tick();

		if (syncCooldown > 0) {
			syncCooldown--;
			if (syncCooldown == 0 && queuedSync)
				sendData();
		}

		if (lastKnownPos == null)
			lastKnownPos = getBlockPos();
		else if (!lastKnownPos.equals(worldPosition) && worldPosition != null) {
			onPositionChanged();
			return;
		}

		if (updateCapability) {
			updateCapability = false;
			refreshCapability();
		}

		if (updateConnectivity)
			updateConnectivity();
	}

	private void onPositionChanged() {
		removeController(true);
		lastKnownPos = worldPosition;
	}

	public static void scheduleConnectivityUpdate(AbstractMultiFluidTankBlockEntity<?> be) {
		if (be.level == null || be.level.isClientSide)
			return;
		be.updateConnectivity = true;
	}

	protected void updateConnectivity() {
		updateConnectivity = false;
		if (level.isClientSide)
			return;
		if (!isController())
			return;
		ConnectivityHandler.formMulti(this);
	}

	public SmartMultiFluidTank getTankInventory() {
		return tankInventory;
	}

	public IFluidHandler getTankHandler() {
		if (fluidCapability == null)
			refreshCapability();
		return fluidCapability;
	}

	protected void chaseFluidLevel() {
		if (fluidLevel == null)
			return;
		for (int i = 0; i < fluidLevel.length; i++) {
			if (fluidLevel[i] != null)
				fluidLevel[i].tickChaser();
		}
	}

	protected void chaseExpFluidLevel() {
		if (fluidLevel == null)
			return;
		for (int i = 0; i < TANKS; i++) {
			fluidLevel[i].chase(getFillState(i), .5f, LerpedFloat.Chaser.EXP);
		}
	}

	public void initFluidLevel() {
		fluidLevel = new LerpedFloat[TANKS];
		for (int i = 0; i < TANKS; i++) {
			fluidLevel[i] = LerpedFloat.linear()
				.startWithValue(getFillState(i));
		}
	}

	public void initFluidLevel(boolean force) {
		if (force)
			initFluidLevel();
		else if (fluidLevel == null)
			initFluidLevel();
	}

	@SuppressWarnings("unchecked")
	public static <T extends BlockEntity & IMultiBlockEntityContainer> T partAt(BlockEntityType<?> type,
			net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be != null && be.getType() == type && !be.isRemoved())
			return (T) be;
		return null;
	}

	public void applyFluidTankSize(int blocks) {
		tankInventory.setCapacity(blocks * getCapacityMultiplier());
		int overflow = tankInventory.getFluidAmount() - tankInventory.getCapacity();
		if (overflow > 0) {
			for (int i = 0; i < TANKS; i++) {
				int drained = tankInventory.drain(overflow, IFluidHandler.FluidAction.EXECUTE).getAmount();
				overflow -= drained;
				if (overflow <= 0)
					break;
			}
		}
		forceFluidLevelUpdate = true;
	}

	@Override
	public BlockPos getController() {
		return isController() ? worldPosition : controller;
	}

	@Override
	public boolean isController() {
		return controller == null || worldPosition.getX() == controller.getX()
			&& worldPosition.getY() == controller.getY() && worldPosition.getZ() == controller.getZ();
	}

	@Override
	public void setController(BlockPos controller) {
		if (level.isClientSide && !isVirtual())
			return;
		if (controller.equals(this.controller))
			return;
		this.controller = controller;
		refreshCapability();
		setChanged();
		sendData();
	}

	protected void refreshCapability() {
		fluidCapability = handlerForCapability();
		invalidateCapabilities();
	}

	@SuppressWarnings("unchecked")
	protected IFluidHandler handlerForCapability() {
		if (isController())
			return tankInventory;
		T controllerBE = getControllerBE();
		return controllerBE != null ? controllerBE.handlerForCapability() : null;
	}

	@Override
	public BlockPos getLastKnownPos() {
		return lastKnownPos;
	}

	@Override
	public void preventConnectivityUpdate() {
		updateConnectivity = false;
	}

	@Override
	public int getMaxWidth() {
		return MAX_SIZE;
	}

	public static int getMaxHeight() {
		return MAX_SIZE * 10;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void setHeight(int height) {
		this.height = height;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public void setWidth(int width) {
		this.width = width;
	}

	@Override
	public boolean hasTank() {
		return true;
	}

	@Override
	public int getTankSize(int tank) {
		return getCapacityMultiplier();
	}

	@Override
	public void setTankSize(int tank, int blocks) {
		applyFluidTankSize(blocks);
	}

	@Override
	public IFluidTank getTank(int tank) {
		return tankInventory;
	}

	@Override
	public FluidStack getFluid(int tank) {
		return tankInventory.getFluid().copy();
	}

	public float getFillState() {
		return (float) tankInventory.getFluidAmount() / tankInventory.getCapacity();
	}

	public float getFillState(int i) {
		return (float) tankInventory.getFluidAmount(i) / tankInventory.getCapacity();
	}

	public LerpedFloat[] getFluidLevel() {
		return fluidLevel;
	}

	public int getTotalTankSize() {
		return width * width * height;
	}

	public static int getCapacityMultiplier() {
		return CAPACITY_PER_BLOCK;
	}

	public void sendDataImmediately() {
		syncCooldown = 0;
		queuedSync = false;
		sendData();
	}

	@Override
	public void sendData() {
		if (syncCooldown > 0) {
			queuedSync = true;
			return;
		}
		super.sendData();
		queuedSync = false;
		syncCooldown = SYNC_RATE;
	}

	@Override
	protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(compound, registries, clientPacket);

		BlockPos controllerBefore = controller;
		int prevSize = width;
		int prevHeight = height;
		int prevLum = luminosity;

		updateConnectivity = compound.contains("Uninitialized");

		luminosity = compound.getInt("Luminosity");
		lastKnownPos = null;
		controller = null;

		if (compound.contains("LastKnownPos"))
			lastKnownPos = NBTHelper.readBlockPos(compound, "LastKnownPos");
		if (compound.contains("Controller"))
			controller = NBTHelper.readBlockPos(compound, "Controller");

		if (isController()) {
			restoreWindowStateOnRead(compound);
			width = compound.getInt("Size");
			height = compound.getInt("Height");
			onWidthRestored();
			tankInventory.setCapacity(getTotalTankSize() * getCapacityMultiplier());
			tankInventory.load(registries, compound.getCompound("TankContent"));
			if (tankInventory.getSpace() < 0)
				tankInventory.drain(-tankInventory.getSpace(), IFluidHandler.FluidAction.EXECUTE);
		}

		updateCapability = true;

		if (compound.contains("ForceFluidLevel") || fluidLevel == null)
			initFluidLevel();

		if (!clientPacket)
			return;

		boolean changeOfController =
			controllerBefore == null ? controller != null : !controllerBefore.equals(controller);
		if (changeOfController || prevSize != width || prevHeight != height) {
			if (hasLevel())
				level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
			if (isController())
				tankInventory.setCapacity(getCapacityMultiplier() * getTotalTankSize());
			invalidateRenderBoundingBox();
		}
		if (isController()) {
			if (compound.contains("ForceFluidLevel") || fluidLevel == null)
				initFluidLevel(true);
			chaseExpFluidLevel();
		}
		if (luminosity != prevLum && hasLevel())
			level.getChunkSource().getLightEngine().checkBlock(worldPosition);

		if (compound.contains("LazySync") && fluidLevel != null)
			for (int i = 0; i < TANKS; i++) {
				fluidLevel[i].chase(fluidLevel[i].getChaseTarget(), 0.125f, LerpedFloat.Chaser.EXP);
			}
	}

	protected void onWidthRestored() {
	}

	@Override
	public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
		if (updateConnectivity)
			compound.putBoolean("Uninitialized", true);

		if (lastKnownPos != null)
			compound.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
		if (!isController())
			compound.put("Controller", NbtUtils.writeBlockPos(controller));
		if (isController()) {
			writeWindowState(compound);
			compound.put("TankContent", tankInventory.save(registries, new CompoundTag()));
			compound.putInt("Size", width);
			compound.putInt("Height", height);
		}
		compound.putInt("Luminosity", luminosity);
		super.write(compound, registries, clientPacket);

		if (!clientPacket)
			return;
		if (forceFluidLevelUpdate)
			compound.putBoolean("ForceFluidLevel", true);
		if (queuedSync)
			compound.putBoolean("LazySync", true);
		forceFluidLevelUpdate = false;
	}

	@Override
	public void writeSafe(CompoundTag compound, HolderLookup.Provider registries) {
		if (isController()) {
			writeWindowStateSafe(compound);
			compound.putInt("Size", width);
			compound.putInt("Height", height);
		}
	}

	@Override
	protected AABB createRenderBoundingBox() {
		if (isController())
			return super.createRenderBoundingBox().expandTowards(getRenderExtent());
		else
			return super.createRenderBoundingBox();
	}

	protected abstract net.minecraft.world.phys.Vec3 getRenderExtent();

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		T controllerBE = getControllerBE();
		if (controllerBE == null)
			return false;
		return containedFluidTooltip(tooltip, isPlayerSneaking, controllerBE.getTankHandler());
	}

	public int getComparatorOutput() {
		if (tankInventory.isEmpty())
			return 0;
		float fillState = getFillState();
		return (int) (fillState * 14) + 1;
	}

	protected void setLuminosity(int luminosity) {
		if (level.isClientSide)
			return;
		if (this.luminosity == luminosity)
			return;
		this.luminosity = luminosity;
		sendData();
	}

	protected void registerCapability(RegisterCapabilitiesEvent event,
			BlockEntityType<? extends AbstractMultiFluidTankBlockEntity<?>> type) {
		event.registerBlockEntity(
			Capabilities.FluidHandler.BLOCK,
			type,
			(be, side) -> {
				AbstractMultiFluidTankBlockEntity<?> tank = (AbstractMultiFluidTankBlockEntity<?>) be;
				if (tank.fluidCapability == null)
					tank.refreshCapability();
				return tank.fluidCapability;
			}
		);
	}
}
