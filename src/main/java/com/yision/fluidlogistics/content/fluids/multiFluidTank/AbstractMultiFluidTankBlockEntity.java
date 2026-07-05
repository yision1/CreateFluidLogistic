package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;

public abstract class AbstractMultiFluidTankBlockEntity<T extends AbstractMultiFluidTankBlockEntity<T>>
        extends SmartBlockEntity implements IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid {

    protected static final int MAX_SIZE = 3;
    protected static final int TANKS = 8;
    protected static final int CAPACITY_PER_BLOCK = 16000;
    protected static final int SYNC_RATE = 8;
    protected static final IFluidHandler EMPTY_HANDLER = new SmartMultiFluidTank(0, TANKS, $ -> {
    });

    protected SmartMultiFluidTank tankInventory;
    protected LazyOptional<IFluidHandler> fluidCapability;
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
        fluidCapability = LazyOptional.empty();
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

    protected abstract BlockState applyRemoveControllerShape(BlockState state);

    public abstract boolean isOwnBlockState(BlockState state);

    @Override
    public abstract T getControllerBE();

    protected void onWidthRestored() {
    }

    @Override
    public void removeController(boolean keepContents) {
        if (level.isClientSide) {
            return;
        }
        updateConnectivity = true;
        if (!keepContents) {
            applyFluidTankSize(1);
        }
        controller = null;
        width = 1;
        height = 1;
        onWidthRestored();
        onControllerFluidChanged(tankInventory.getFluids());

        BlockState state = getBlockState();
        if (isOwnBlockState(state)) {
            state = applyRemoveControllerShape(state);
            level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
        }

        refreshCapability();
        setChanged();
        sendData();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        sendData();
        if (level.isClientSide) {
            invalidateRenderBoundingBox();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (syncCooldown > 0) {
            syncCooldown--;
            if (syncCooldown == 0 && queuedSync) {
                sendData();
            }
        }

        if (lastKnownPos == null) {
            lastKnownPos = getBlockPos();
        } else if (!lastKnownPos.equals(worldPosition) && worldPosition != null) {
            onPositionChanged();
            return;
        }

        if (updateCapability) {
            updateCapability = false;
            refreshCapability();
        }

        if (updateConnectivity) {
            updateConnectivity();
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    private void onPositionChanged() {
        removeController(true);
        lastKnownPos = worldPosition;
    }

    public static void scheduleConnectivityUpdate(AbstractMultiFluidTankBlockEntity<?> be) {
        if (be.level == null || be.level.isClientSide) {
            return;
        }
        be.updateConnectivity = true;
    }

    protected void updateConnectivity() {
        updateConnectivity = false;
        if (level.isClientSide || !isController()) {
            return;
        }
        ConnectivityHandler.formMulti(this);
    }

    public SmartMultiFluidTank getTankInventory() {
        return tankInventory;
    }

    public IFluidHandler getTankHandler() {
        if (!fluidCapability.isPresent()) {
            refreshCapability();
        }
        return handlerForCapability();
    }

    protected void chaseFluidLevel() {
        if (fluidLevel == null) {
            return;
        }
        for (LerpedFloat level : fluidLevel) {
            if (level != null) {
                level.tickChaser();
            }
        }
    }

    protected void chaseExpFluidLevel() {
        if (fluidLevel == null) {
            return;
        }
        for (int i = 0; i < TANKS; i++) {
            fluidLevel[i].chase(getFillState(i), 0.5f, LerpedFloat.Chaser.EXP);
        }
    }

    public void initFluidLevel() {
        fluidLevel = new LerpedFloat[TANKS];
        for (int i = 0; i < TANKS; i++) {
            fluidLevel[i] = LerpedFloat.linear().startWithValue(getFillState(i));
        }
    }

    public void initFluidLevel(boolean force) {
        if (force || fluidLevel == null) {
            initFluidLevel();
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity & IMultiBlockEntityContainer> T partAt(BlockEntityType<?> type,
            net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && be.getType() == type && !be.isRemoved()) {
            return (T) be;
        }
        return null;
    }

    public void applyFluidTankSize(int blocks) {
        tankInventory.setCapacity(blocks * getCapacityMultiplier());
        int overflow = tankInventory.getFluidAmount() - tankInventory.getCapacity();
        while (overflow > 0) {
            FluidStack drained = tankInventory.drain(overflow, IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) {
                break;
            }
            overflow -= drained.getAmount();
        }
        forceFluidLevelUpdate = true;
    }

    @Override
    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Override
    public boolean isController() {
        return controller == null || worldPosition.equals(controller);
    }

    @Override
    public void setController(BlockPos controller) {
        if (level.isClientSide && !isVirtual()) {
            return;
        }
        if (Objects.equals(controller, this.controller)) {
            return;
        }
        this.controller = controller;
        refreshCapability();
        setChanged();
        sendData();
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
        return tankInventory.getCapacity() == 0 ? 0 : (float) tankInventory.getFluidAmount() / tankInventory.getCapacity();
    }

    public float getFillState(int tank) {
        return tankInventory.getCapacity() == 0 ? 0 : (float) tankInventory.getFluidAmount(tank) / tankInventory.getCapacity();
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
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);

        BlockPos controllerBefore = controller;
        int prevSize = width;
        int prevHeight = height;
        int prevLum = luminosity;

        updateConnectivity = compound.contains("Uninitialized");
        luminosity = compound.getInt("Luminosity");
        lastKnownPos = null;
        controller = null;

        if (compound.contains("LastKnownPos")) {
            lastKnownPos = NbtUtils.readBlockPos(compound.getCompound("LastKnownPos"));
        }
        if (compound.contains("Controller")) {
            controller = NbtUtils.readBlockPos(compound.getCompound("Controller"));
        }

        if (isController()) {
            restoreWindowStateOnRead(compound);
            width = compound.getInt("Size");
            height = compound.getInt("Height");
            onWidthRestored();
            tankInventory.setCapacity(getTotalTankSize() * getCapacityMultiplier());
            tankInventory.load(compound.getCompound("TankContent"));
            if (tankInventory.getSpace() < 0) {
                tankInventory.drain(-tankInventory.getSpace(), IFluidHandler.FluidAction.EXECUTE);
            }
        }

        if (compound.contains("ForceFluidLevel") || fluidLevel == null) {
            initFluidLevel();
        }

        updateCapability = true;

        if (!clientPacket) {
            return;
        }

        boolean changeOfController = !Objects.equals(controllerBefore, controller);
        if (changeOfController || prevSize != width || prevHeight != height) {
            if (hasLevel()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
            }
            if (isController()) {
                tankInventory.setCapacity(getCapacityMultiplier() * getTotalTankSize());
            }
            invalidateRenderBoundingBox();
        }

        if (isController()) {
            if (compound.contains("ForceFluidLevel") || fluidLevel == null) {
                initFluidLevel(true);
            }
            chaseExpFluidLevel();
        }

        if (luminosity != prevLum && hasLevel()) {
            level.getChunkSource().getLightEngine().checkBlock(worldPosition);
        }

        if (compound.contains("LazySync") && fluidLevel != null) {
            for (LerpedFloat level : fluidLevel) {
                level.chase(level.getChaseTarget(), 0.125f, LerpedFloat.Chaser.EXP);
            }
        }
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        if (updateConnectivity) {
            compound.putBoolean("Uninitialized", true);
        }

        if (lastKnownPos != null) {
            compound.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
        }
        if (!isController()) {
            compound.put("Controller", NbtUtils.writeBlockPos(controller));
        }
        if (isController()) {
            writeWindowState(compound);
            compound.put("TankContent", tankInventory.save(new CompoundTag()));
            compound.putInt("Size", width);
            compound.putInt("Height", height);
        }
        compound.putInt("Luminosity", luminosity);
        super.write(compound, clientPacket);

        if (!clientPacket) {
            return;
        }
        if (forceFluidLevelUpdate) {
            compound.putBoolean("ForceFluidLevel", true);
        }
        if (queuedSync) {
            compound.putBoolean("LazySync", true);
        }
        forceFluidLevelUpdate = false;
    }

    @Override
    public void writeSafe(CompoundTag compound) {
        if (isController()) {
            writeWindowStateSafe(compound);
            compound.putInt("Size", width);
            compound.putInt("Height", height);
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        if (isController()) {
            return super.createRenderBoundingBox().expandTowards(getRenderExtent());
        }
        return super.createRenderBoundingBox();
    }

    protected abstract net.minecraft.world.phys.Vec3 getRenderExtent();

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        T controllerBE = getControllerBE();
        return controllerBE != null && containedFluidTooltip(tooltip, isPlayerSneaking,
                controllerBE.getCapability(ForgeCapabilities.FLUID_HANDLER));
    }

    public int getComparatorOutput() {
        if (tankInventory.isEmpty()) {
            return 0;
        }
        return (int) (getFillState() * 14) + 1;
    }

    protected void setLuminosity(int luminosity) {
        if (level.isClientSide || this.luminosity == luminosity) {
            return;
        }
        this.luminosity = luminosity;
        sendData();
    }

    private void refreshCapability() {
        LazyOptional<IFluidHandler> oldCap = fluidCapability;
        fluidCapability = LazyOptional.of(this::handlerForCapability);
        oldCap.invalidate();
    }

    @SuppressWarnings("unchecked")
    protected IFluidHandler handlerForCapability() {
        if (isController()) {
            return tankInventory;
        }
        T controllerBE = getControllerBE();
        return controllerBE != null ? controllerBE.handlerForCapability() : EMPTY_HANDLER;
    }

    @Nonnull
    @Override
    public <C> LazyOptional<C> getCapability(@Nonnull Capability<C> cap, @Nullable Direction side) {
        if (!fluidCapability.isPresent()) {
            refreshCapability();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }
}
