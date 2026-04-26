package com.yision.fluidlogistics.block.MultiFluidTank;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yision.fluidlogistics.util.SmartMultiFluidTank;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class MultiFluidTankBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid {

    private static final int MAX_SIZE = 3;
    private static final int TANKS = 8;
    private static final int CAPACITY_PER_BLOCK = 8000;
    private static final int SYNC_RATE = 8;

    protected SmartMultiFluidTank tankInventory;
    protected IFluidHandler fluidCapability;
    protected BlockPos controller;
    
    public SmartMultiFluidTank getTankInventory() {
        return tankInventory;
    };
    protected BlockPos lastKnownPos;
    protected boolean updateConnectivity;
    protected boolean updateCapability;
    protected boolean window;
    public int luminosity;
    protected int width;
    protected int height;
    protected boolean forceFluidLevelUpdate;
    protected int syncCooldown;
    protected boolean queuedSync;

    private LerpedFloat[] fluidLevel;

    public MultiFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tankInventory = createInventory();
        window = true;
        updateConnectivity = false;
        updateCapability = false;
        forceFluidLevelUpdate = true;
        height = 1;
        width = 1;
        refreshCapability();
    }

    protected SmartMultiFluidTank createInventory() {
        return new SmartMultiFluidTank(getCapacityMultiplier(), TANKS, this::onFluidStackChanged);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            com.yision.fluidlogistics.registry.AllBlockEntities.MULTI_FLUID_TANK.get(),
            (be, side) -> {
                if (be.fluidCapability == null)
                    be.refreshCapability();
                return be.fluidCapability;
            }
        );
    }

    public IFluidHandler getTankHandler() {
        if (fluidCapability == null)
            refreshCapability();
        return fluidCapability;
    }

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

        chaseFluidLevel();
    }

    private void chaseFluidLevel() {
        if (fluidLevel == null)
            return;

        for (int i = 0; i < fluidLevel.length; i++) {
            if (fluidLevel[i] != null)
                fluidLevel[i].tickChaser();
        }
    }

    private void chaseExpFluidLevel() {
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

    private void onPositionChanged() {
        removeController(true);
        lastKnownPos = worldPosition;
    }

    public static void scheduleConnectivityUpdate(MultiFluidTankBlockEntity be) {
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

    public static void toggleWindows(MultiFluidTankBlockEntity be) {
        MultiFluidTankBlockEntity controllerBE = be.getControllerBE();
        if (controllerBE == null)
            return;
        controllerBE.setWindows(!controllerBE.window);
    }

    public void setWindows(boolean window) {
        this.window = window;
        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = this.worldPosition.offset(xOffset, yOffset, zOffset);
                    BlockState blockState = level.getBlockState(pos);
                    if (!MultiFluidTankBlock.isTank(blockState))
                        continue;

                    MultiFluidTankBlock.Shape shape = MultiFluidTankBlock.Shape.PLAIN;
                    if (window) {
                        shape = MultiFluidTankBlock.Shape.WINDOW;
                    }

                    level.setBlock(pos, blockState.setValue(MultiFluidTankBlock.SHAPE, shape),
                            Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
                    level.getChunkSource().getLightEngine().checkBlock(pos);
                }
            }
        }
    }

    protected void onFluidStackChanged(FluidStack[] newFluidStack) {
        if (!hasLevel())
            return;

        float luminosityTotal = 0.0f;
        int tankTotal = 0;
        for (int i = 0; i < newFluidStack.length; i++) {
            if (newFluidStack[i].isEmpty()) continue;
            tankTotal++;
            int lightLevel = newFluidStack[i].getFluid().getFluidType().getLightLevel(newFluidStack[i]);
            luminosityTotal += (lightLevel / 1.2f);
        }
        int luminosity = tankTotal > 0 ? (int) luminosityTotal / tankTotal : 0;
        int maxY = (int) ((getFillState() * height) + 1);

        for (int yOffset = 0; yOffset < height; yOffset++) {
            boolean isBright = yOffset < maxY;
            int actualLuminosity = isBright ? luminosity : luminosity > 0 ? 1 : 0;

            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = this.worldPosition.offset(xOffset, yOffset, zOffset);
                    MultiFluidTankBlockEntity tankAt = partAt(getType(), level, pos);
                    if (tankAt == null)
                        continue;
                    level.updateNeighbourForOutputSignal(pos, tankAt.getBlockState().getBlock());
                    if (tankAt.luminosity == actualLuminosity)
                        continue;
                    tankAt.setLuminosity(actualLuminosity);
                }
            }
        }

        if (!level.isClientSide) {
            setChanged();
            sendData();
        }

        if (isVirtual()) {
            initFluidLevel(false);
            chaseExpFluidLevel();
        }
    }

    protected void setLuminosity(int luminosity) {
        if (level.isClientSide)
            return;
        if (this.luminosity == luminosity)
            return;
        this.luminosity = luminosity;
        sendData();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity & IMultiBlockEntityContainer> T partAt(BlockEntityType<?> type, net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
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
                if (overflow <= 0) break;
            }
        }
        forceFluidLevelUpdate = true;
    }

    @Override
    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Override
    public MultiFluidTankBlockEntity getControllerBE() {
        if (isController() || !hasLevel())
            return this;
        BlockEntity blockEntity = level.getBlockEntity(controller);
        if (blockEntity instanceof MultiFluidTankBlockEntity)
            return (MultiFluidTankBlockEntity) blockEntity;
        return null;
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

    protected IFluidHandler handlerForCapability() {
        if (isController())
            return tankInventory;
        MultiFluidTankBlockEntity controllerBE = getControllerBE();
        return controllerBE != null ? controllerBE.handlerForCapability() : null;
    }

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
        onFluidStackChanged(tankInventory.getFluids());

        BlockState state = getBlockState();
        if (MultiFluidTankBlock.isTank(state)) {
            state = state.setValue(MultiFluidTankBlock.BOTTOM, true);
            state = state.setValue(MultiFluidTankBlock.TOP, true);
            state = state.setValue(MultiFluidTankBlock.SHAPE, window ? MultiFluidTankBlock.Shape.WINDOW : MultiFluidTankBlock.Shape.PLAIN);
            getLevel().setBlock(worldPosition, state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
        }

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
    public void notifyMultiUpdated() {
        BlockState state = this.getBlockState();
        if (MultiFluidTankBlock.isTank(state)) {
            state = state.setValue(MultiFluidTankBlock.BOTTOM, getController().getY() == getBlockPos().getY());
            state = state.setValue(MultiFluidTankBlock.TOP, getController().getY() + height - 1 == getBlockPos().getY());
            level.setBlock(getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }
        if (isController())
            setWindows(window);
        onFluidStackChanged(tankInventory.getFluids());
        setChanged();
    }

    @Override
    public void setExtraData(@Nullable Object data) {
        if (data instanceof Boolean)
            window = (boolean) data;
    }

    @Override
    @Nullable
    public Object getExtraData() {
        return window;
    }

    @Override
    public Object modifyExtraData(Object data) {
        if (data instanceof Boolean windows) {
            windows |= window;
            return windows;
        }
        return data;
    }

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public int getMaxLength(Direction.Axis longAxis, int width) {
        if (longAxis == Direction.Axis.Y)
            return getMaxHeight();
        return getMaxWidth();
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
            window = compound.getBoolean("Window");
            width = compound.getInt("Size");
            height = compound.getInt("Height");
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

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (updateConnectivity)
            compound.putBoolean("Uninitialized", true);

        if (lastKnownPos != null)
            compound.put("LastKnownPos", NbtUtils.writeBlockPos(lastKnownPos));
        if (!isController())
            compound.put("Controller", NbtUtils.writeBlockPos(controller));
        if (isController()) {
            compound.putBoolean("Window", window);
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
            compound.putBoolean("Window", window);
            compound.putInt("Size", width);
            compound.putInt("Height", height);
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        if (isController())
            return super.createRenderBoundingBox().expandTowards(width - 1, height - 1, width - 1);
        else
            return super.createRenderBoundingBox();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        MultiFluidTankBlockEntity controllerBE = getControllerBE();
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
}
