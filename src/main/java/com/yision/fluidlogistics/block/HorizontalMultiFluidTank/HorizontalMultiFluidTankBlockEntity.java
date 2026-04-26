package com.yision.fluidlogistics.block.HorizontalMultiFluidTank;

    
import java.util.List;


import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yision.fluidlogistics.util.SmartMultiFluidTank;
    
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
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
    
import org.jetbrains.annotations.Nullable;
    
    public class HorizontalMultiFluidTankBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IMultiBlockEntityContainer.Fluid {
    
        private static final int MAX_SIZE = 3;
        private static final int TANKS = 8;
        private static final int CAPACITY_PER_BLOCK = 8000;
        private static final int SYNC_RATE = 8;
    
        protected SmartMultiFluidTank tankInventory;
        protected IFluidHandler fluidCapability;
        protected BlockPos controller;
        
        public SmartMultiFluidTank getTankInventory() {
            return tankInventory;
        }
        
        protected BlockPos lastKnownPos;
    protected boolean updateConnectivity;
    protected boolean updateCapability;
    protected boolean window;
    protected HorizontalMultiFluidTankBlock.WindowType windowType;
    public int luminosity;
        protected int width;
        protected int height;
        protected boolean forceFluidLevelUpdate;
        protected int syncCooldown;
        protected boolean queuedSync;
    
        private LerpedFloat[] fluidLevel;
    
        public HorizontalMultiFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
            tankInventory = createInventory();
            window = true;
            windowType = HorizontalMultiFluidTankBlock.WindowType.SIDE_WIDE;
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
                com.yision.fluidlogistics.registry.AllBlockEntities.HORIZONTAL_MULTI_FLUID_TANK.get(),
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
    
        public static void scheduleConnectivityUpdate(HorizontalMultiFluidTankBlockEntity be) {
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
    
        public static void toggleWindows(HorizontalMultiFluidTankBlockEntity be) {
            HorizontalMultiFluidTankBlockEntity controllerBE = be.getControllerBE();
            if (controllerBE == null)
                return;
            if (!controllerBE.window) {
                controllerBE.setWindowType(HorizontalMultiFluidTankBlock.WindowType.SIDE_WIDE);
                controllerBE.setWindows(true);
            } else {
                HorizontalMultiFluidTankBlock.WindowType[] types = HorizontalMultiFluidTankBlock.WindowType.values();
                if (controllerBE.windowType.ordinal() >= types.length - 1) {
                    controllerBE.setWindows(false);
                    return;
                }
                HorizontalMultiFluidTankBlock.WindowType nextType = types[controllerBE.windowType.ordinal() + 1];
                while (!controllerBE.isWindowTypeAllowed(nextType)) {
                    if (nextType.ordinal() >= types.length - 1) {
                        controllerBE.setWindows(false);
                        return;
                    }
                    nextType = types[nextType.ordinal() + 1];
                }
                controllerBE.setWindowType(nextType);
                controllerBE.setWindows(true);
            }
        }

        public boolean isWindowTypeAllowed(HorizontalMultiFluidTankBlock.WindowType type) {
            return switch (type) {
                case SIDE_WIDE -> true;
                case SIDE_NARROW_ENDS -> height >= 2;
                case SIDE_NARROW_THIRDS -> height >= 3;
                case SIDE_HORIZONTAL -> width > 2 && width % 2 == 1;
            };
        }

        public void setWindowType(HorizontalMultiFluidTankBlock.WindowType windowType) {
            this.windowType = windowType;
        }

        public HorizontalMultiFluidTankBlock.WindowType getWindowType() {
            return windowType;
        }
    
        public void setWindows(boolean window) {
            this.window = window;
            Axis axis = getAxis();
            for (int yOffset = 0; yOffset < width; yOffset++) {
                for (int lengthOffset = 0; lengthOffset < height; lengthOffset++) {
                    for (int widthOffset = 0; widthOffset < width; widthOffset++) {
                        BlockPos pos = this.worldPosition.offset(
                                axis == Axis.X ? lengthOffset : widthOffset,
                                yOffset,
                                axis == Axis.Z ? lengthOffset : widthOffset
                        );
                        BlockState blockState = level.getBlockState(pos);
                        if (!HorizontalMultiFluidTankBlock.isVessel(blockState))
                            continue;

                        HorizontalMultiFluidTankBlock.Shape shape = HorizontalMultiFluidTankBlock.Shape.PLAIN;
                        if (window) {
                            if (windowType == HorizontalMultiFluidTankBlock.WindowType.SIDE_HORIZONTAL) {
                                if (yOffset == width / 2) {
                                    shape = HorizontalMultiFluidTankBlock.Shape.WINDOW;
                                }
                            } else if (windowType == HorizontalMultiFluidTankBlock.WindowType.SIDE_WIDE || height <= 1) {
                                if ((widthOffset == 0 || widthOffset == width - 1)) {
                                    if (width == 1)
                                        shape = HorizontalMultiFluidTankBlock.Shape.WINDOW;
                                    else if (yOffset == 0)
                                        shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_TOP;
                                    else if (yOffset == width - 1)
                                        shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_BOTTOM;
                                    else
                                        shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_MIDDLE;
                                }
                            } else if (windowType == HorizontalMultiFluidTankBlock.WindowType.SIDE_NARROW_ENDS || 
                                       windowType == HorizontalMultiFluidTankBlock.WindowType.SIDE_NARROW_THIRDS) {
                                int windowOffset = windowType == HorizontalMultiFluidTankBlock.WindowType.SIDE_NARROW_ENDS ? 0 : Math.max(1, height / 3 - 1);
                                if ((lengthOffset == windowOffset || lengthOffset == height - 1 - windowOffset) && (widthOffset == 0 || widthOffset == width - 1)) {
                                    if (width == 1)
                                        shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_SINGLE;
                                    else if (yOffset == 0)
                                        shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_TOP_SINGLE;
                                    else if (yOffset == width - 1)
                                        shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_BOTTOM_SINGLE;
                                    else
                                        shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_MIDDLE_SINGLE;
                                }
                            }
                        }

                        level.setBlock(pos, blockState.setValue(HorizontalMultiFluidTankBlock.SHAPE, shape),
                                Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE | Block.UPDATE_KNOWN_SHAPE);
                        level.getChunkSource().getLightEngine().checkBlock(pos);
                    }
                }
            }
        }
    
        protected void onFluidStackChanged(FluidStack[] newFluidStack) {
            if (!hasLevel())
                return;

            Axis axis = getAxis();
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
    
            for (int lengthOffset = 0; lengthOffset < height; lengthOffset++) {
                boolean isBright = lengthOffset < maxY;
                int actualLuminosity = isBright ? luminosity : luminosity > 0 ? 1 : 0;
    
                for (int yOffset = 0; yOffset < width; yOffset++) {
                    for (int widthOffset = 0; widthOffset < width; widthOffset++) {
                        BlockPos pos = this.worldPosition.offset(
                                axis == Axis.X ? lengthOffset : widthOffset,
                                yOffset,
                                axis == Axis.Z ? lengthOffset : widthOffset
                        );
                        HorizontalMultiFluidTankBlockEntity tankAt = partAt(getType(), level, pos);
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
    
        public Axis getAxis() {
            return getBlockState().getValue(HorizontalMultiFluidTankBlock.AXIS);
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
        public HorizontalMultiFluidTankBlockEntity getControllerBE() {
            if (isController() || !hasLevel())
                return this;
            BlockEntity blockEntity = level.getBlockEntity(controller);
            if (blockEntity instanceof HorizontalMultiFluidTankBlockEntity)
                return (HorizontalMultiFluidTankBlockEntity) blockEntity;
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
            HorizontalMultiFluidTankBlockEntity controllerBE = getControllerBE();
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
            if (HorizontalMultiFluidTankBlock.isVessel(state)) {
                state = state.setValue(HorizontalMultiFluidTankBlock.POSITIVE, true);
                state = state.setValue(HorizontalMultiFluidTankBlock.NEGATIVE, true);
                state = state.setValue(HorizontalMultiFluidTankBlock.SHAPE, window ? HorizontalMultiFluidTankBlock.Shape.WINDOW : HorizontalMultiFluidTankBlock.Shape.PLAIN);
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
            if (HorizontalMultiFluidTankBlock.isVessel(state)) {
                Axis axis = getAxis();
                state = state.setValue(HorizontalMultiFluidTankBlock.NEGATIVE, axis == Axis.X
                        ? getController().getX() == getBlockPos().getX()
                        : getController().getZ() == getBlockPos().getZ());
                state = state.setValue(HorizontalMultiFluidTankBlock.POSITIVE, axis == Axis.X
                        ? getController().getX() + height - 1 == getBlockPos().getX()
                        : getController().getZ() + height - 1 == getBlockPos().getZ());
                level.setBlock(getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
            }
            if (isController())
                setWindows(window);
            onFluidStackChanged(tankInventory.getFluids());
            setChanged();
        }
    
        @Override
        public void setExtraData(@Nullable Object data) {
            if (data == null) {
                window = false;
                windowType = HorizontalMultiFluidTankBlock.WindowType.SIDE_WIDE;
            } else if (data instanceof HorizontalMultiFluidTankBlock.WindowType type) {
                window = true;
                windowType = type;
            }
        }

        @Override
        @Nullable
        public Object getExtraData() {
            return window ? windowType : null;
        }

        @Override
        public Object modifyExtraData(Object data) {
            if (data == null || (data instanceof HorizontalMultiFluidTankBlock.WindowType)) {
                if (data != null && !window) return data;
                if (window) return windowType;
                return null;
            }
            return data;
        }
    
        @Override
        public Axis getMainConnectionAxis() {
            return getAxis();
        }
    
        @Override
        public int getMaxLength(Axis longAxis, int width) {
            if (longAxis == Axis.Y)
                return getMaxWidth();
            return getMaxHeight();
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
                if (compound.contains("WindowType"))
                    windowType = HorizontalMultiFluidTankBlock.WindowType.valueOf(compound.getString("WindowType"));
                else
                    windowType = HorizontalMultiFluidTankBlock.WindowType.SIDE_WIDE;
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
                compound.putString("WindowType", windowType.name());
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
                compound.putString("WindowType", windowType.name());
                compound.putInt("Size", width);
                compound.putInt("Height", height);
            }
        }
    
        @Override
        protected AABB createRenderBoundingBox() {
            if (isController()) {
                Axis axis = getAxis();
                return super.createRenderBoundingBox().expandTowards(
                        axis == Axis.X ? (height - 1) : (width - 1),
                        width - 1,
                        axis == Axis.Z ? (height - 1) : (width - 1)
                );
            } else
                return super.createRenderBoundingBox();
        }
    
        @Override
        public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
            HorizontalMultiFluidTankBlockEntity controllerBE = getControllerBE();
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
