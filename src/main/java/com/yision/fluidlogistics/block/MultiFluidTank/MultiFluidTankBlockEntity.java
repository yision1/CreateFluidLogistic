package com.yision.fluidlogistics.block.MultiFluidTank;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.util.SmartMultiFluidTank;

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

@SuppressWarnings("unchecked")
public class MultiFluidTankBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid {

    private static final int MAX_SIZE = 3;
    private static final int TANKS = 8;
    private static final int CAPACITY_PER_BLOCK = 16000;
    private static final int SYNC_RATE = 8;
    private static final IFluidHandler EMPTY_HANDLER = new SmartMultiFluidTank(0, TANKS, $ -> {
    });

    protected LazyOptional<IFluidHandler> fluidCapability;
    protected boolean forceFluidLevelUpdate;
    protected SmartMultiFluidTank tankInventory;
    protected BlockPos controller;
    protected BlockPos lastKnownPos;
    protected boolean updateConnectivity;
    protected boolean updateCapability;
    protected MultiFluidTankBlock.WindowStyle windowStyle;
    public int luminosity;
    protected int width;
    protected int height;

    protected int syncCooldown;
    protected boolean queuedSync;

    private LerpedFloat[] fluidLevel;

    public SmartMultiFluidTank getTankInventory() {
        return tankInventory;
    }

    public MultiFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tankInventory = createInventory();
        fluidCapability = LazyOptional.empty();
        forceFluidLevelUpdate = true;
        updateConnectivity = false;
        updateCapability = false;
        windowStyle = MultiFluidTankBlock.WindowStyle.FULL;
        width = 1;
        height = 1;
        refreshCapability();
    }

    protected SmartMultiFluidTank createInventory() {
        return new SmartMultiFluidTank(getCapacityMultiplier(), TANKS, this::onFluidStackChanged);
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
        if (!FeatureToggle.isEnabled(FeatureToggle.MULTI_FLUID_TANK)) {
            return;
        }

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
        chaseFluidLevel();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    private void chaseFluidLevel() {
        if (fluidLevel == null) {
            return;
        }

        for (LerpedFloat level : fluidLevel) {
            if (level != null) {
                level.tickChaser();
            }
        }
    }

    private void chaseExpFluidLevel() {
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

    private void onPositionChanged() {
        removeController(true);
        lastKnownPos = worldPosition;
    }

    public static void scheduleConnectivityUpdate(MultiFluidTankBlockEntity be) {
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

    public MultiFluidTankBlock.WindowStyle getWindowStyle() {
        return windowStyle;
    }

    public static void cycleWindowStyle(MultiFluidTankBlockEntity be) {
        MultiFluidTankBlockEntity controllerBE = be.getControllerBE();
        if (controllerBE == null) {
            return;
        }
        controllerBE.setWindowStyle(controllerBE.windowStyle.nextAllowed(controllerBE.width));
    }

    public void setWindowStyle(MultiFluidTankBlock.WindowStyle style) {
        style = style.normalizeForWidth(width);
        this.windowStyle = style;
        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = worldPosition.offset(xOffset, yOffset, zOffset);
                    BlockState blockState = level.getBlockState(pos);
                    if (!MultiFluidTankBlock.isTank(blockState)) {
                        continue;
                    }

                    MultiFluidTankBlock.Shape shape = getShapeForWindowStyle(style, xOffset, zOffset);
                    level.setBlock(pos, blockState.setValue(MultiFluidTankBlock.SHAPE, shape),
                            Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
                    level.getChunkSource().getLightEngine().checkBlock(pos);
                }
            }
        }
        setChanged();
        sendData();
    }

    private MultiFluidTankBlock.Shape getShapeForWindowStyle(MultiFluidTankBlock.WindowStyle style,
                                                             int xOffset, int zOffset) {
        return switch (style) {
            case FULL -> MultiFluidTankBlock.Shape.WINDOW;
            case NONE -> MultiFluidTankBlock.Shape.PLAIN;
            case SINGLE -> switch (width) {
                case 1 -> MultiFluidTankBlock.Shape.WINDOW;
                case 2 -> xOffset == 0
                        ? zOffset == 0 ? MultiFluidTankBlock.Shape.WINDOW_NW : MultiFluidTankBlock.Shape.WINDOW_SW
                        : zOffset == 0 ? MultiFluidTankBlock.Shape.WINDOW_NE : MultiFluidTankBlock.Shape.WINDOW_SE;
                default -> Math.abs(xOffset - zOffset) == 1
                        ? MultiFluidTankBlock.Shape.WINDOW
                        : MultiFluidTankBlock.Shape.PLAIN;
            };
        };
    }

    protected void onFluidStackChanged(FluidStack[] newFluidStack) {
        if (!hasLevel()) {
            return;
        }

        float luminosityTotal = 0.0f;
        int tankTotal = 0;
        for (FluidStack stack : newFluidStack) {
            if (stack.isEmpty()) {
                continue;
            }
            tankTotal++;
            luminosityTotal += stack.getFluid().getFluidType().getLightLevel(stack) / 1.2f;
        }

        int luminosity = tankTotal > 0 ? (int) luminosityTotal / tankTotal : 0;
        int maxY = (int) (getFillState() * height) + 1;

        for (int yOffset = 0; yOffset < height; yOffset++) {
            boolean isBright = yOffset < maxY;
            int actualLuminosity = isBright ? luminosity : luminosity > 0 ? 1 : 0;

            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = worldPosition.offset(xOffset, yOffset, zOffset);
                    MultiFluidTankBlockEntity tankAt = partAt(getType(), level, pos);
                    if (tankAt == null) {
                        continue;
                    }
                    level.updateNeighbourForOutputSignal(pos, tankAt.getBlockState().getBlock());
                    if (tankAt.luminosity != actualLuminosity) {
                        tankAt.setLuminosity(actualLuminosity);
                    }
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
        if (level.isClientSide || this.luminosity == luminosity) {
            return;
        }
        this.luminosity = luminosity;
        sendData();
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

    public IFluidHandler getTankHandler() {
        return handlerForCapability();
    }

    @Override
    public BlockPos getController() {
        return isController() ? worldPosition : controller;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MultiFluidTankBlockEntity getControllerBE() {
        if (isController() || !hasLevel()) {
            return this;
        }
        BlockEntity blockEntity = level.getBlockEntity(controller);
        return blockEntity instanceof MultiFluidTankBlockEntity tank ? tank : null;
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
        windowStyle = windowStyle.normalizeForWidth(width);
        onFluidStackChanged(tankInventory.getFluids());

        BlockState state = getBlockState();
        if (MultiFluidTankBlock.isTank(state)) {
            MultiFluidTankBlock.Shape shape = getShapeForWindowStyle(windowStyle, 0, 0);
            state = state.setValue(MultiFluidTankBlock.BOTTOM, true)
                    .setValue(MultiFluidTankBlock.TOP, true)
                    .setValue(MultiFluidTankBlock.SHAPE, shape);
            level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
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
        BlockState state = getBlockState();
        if (MultiFluidTankBlock.isTank(state)) {
            state = state.setValue(MultiFluidTankBlock.BOTTOM, getController().getY() == getBlockPos().getY());
            state = state.setValue(MultiFluidTankBlock.TOP,
                    getController().getY() + height - 1 == getBlockPos().getY());
            level.setBlock(getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }
        if (isController()) {
            setWindowStyle(windowStyle);
        }
        onFluidStackChanged(tankInventory.getFluids());
        setChanged();
    }

    @Override
    public void setExtraData(@Nullable Object data) {
        if (data instanceof MultiFluidTankBlock.WindowStyle style) {
            windowStyle = style;
        }
    }

    @Override
    @Nullable
    public Object getExtraData() {
        return windowStyle;
    }

    @Override
    public Object modifyExtraData(Object data) {
        if (data instanceof MultiFluidTankBlock.WindowStyle existing) {
            return MultiFluidTankBlock.WindowStyle.merge(existing, windowStyle);
        }
        return data;
    }

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public int getMaxLength(Direction.Axis longAxis, int width) {
        return longAxis == Direction.Axis.Y ? getMaxHeight() : getMaxWidth();
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
            windowStyle = MultiFluidTankBlock.WindowStyle.safeParse(compound.getString("WindowStyle"));
            width = compound.getInt("Size");
            height = compound.getInt("Height");
            windowStyle = windowStyle.normalizeForWidth(width);
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
            compound.putString("WindowStyle", windowStyle.getSerializedName());
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
            compound.putString("WindowStyle", windowStyle.getSerializedName());
            compound.putInt("Size", width);
            compound.putInt("Height", height);
        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return isController() ? super.createRenderBoundingBox().expandTowards(width - 1, height - 1, width - 1)
                : super.createRenderBoundingBox();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        MultiFluidTankBlockEntity controllerBE = getControllerBE();
        return controllerBE != null && containedFluidTooltip(tooltip, isPlayerSneaking,
                controllerBE.getCapability(ForgeCapabilities.FLUID_HANDLER));
    }

    public int getComparatorOutput() {
        if (tankInventory.isEmpty()) {
            return 0;
        }
        return (int) (getFillState() * 14) + 1;
    }

    private void refreshCapability() {
        LazyOptional<IFluidHandler> oldCap = fluidCapability;
        fluidCapability = LazyOptional.of(this::handlerForCapability);
        oldCap.invalidate();
    }

    private IFluidHandler handlerForCapability() {
        if (isController()) {
            return tankInventory;
        }
        MultiFluidTankBlockEntity controllerBE = getControllerBE();
        return controllerBE != null ? controllerBE.handlerForCapability() : EMPTY_HANDLER;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (!FeatureToggle.isEnabled(FeatureToggle.MULTI_FLUID_TANK)) {
            return LazyOptional.empty();
        }
        if (!fluidCapability.isPresent()) {
            refreshCapability();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(cap, side);
    }
}
