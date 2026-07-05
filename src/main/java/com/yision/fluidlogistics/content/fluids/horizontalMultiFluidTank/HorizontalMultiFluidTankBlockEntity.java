package com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank;

import org.jetbrains.annotations.Nullable;

import com.yision.fluidlogistics.content.fluids.multiFluidTank.AbstractMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.SmartMultiFluidTank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

public class HorizontalMultiFluidTankBlockEntity extends AbstractMultiFluidTankBlockEntity<HorizontalMultiFluidTankBlockEntity> {

    protected boolean window;
    protected HorizontalMultiFluidTankBlock.WindowType windowType;

    public HorizontalMultiFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        window = true;
        windowType = HorizontalMultiFluidTankBlock.WindowType.SIDE_WIDE;
    }

    @Override
    protected SmartMultiFluidTank createInventory() {
        return new SmartMultiFluidTank(getCapacityMultiplier(), TANKS, this::onControllerFluidChanged);
    }

    @Override
    public void tick() {
        super.tick();
        chaseFluidLevel();
    }

    public static void toggleWindows(HorizontalMultiFluidTankBlockEntity be) {
        HorizontalMultiFluidTankBlockEntity controllerBE = be.getControllerBE();
        if (controllerBE == null) {
            return;
        }
        if (!controllerBE.window) {
            controllerBE.setWindowType(HorizontalMultiFluidTankBlock.WindowType.SIDE_WIDE);
            controllerBE.setWindows(true);
            return;
        }

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
                    BlockPos pos = worldPosition.offset(
                            axis == Axis.X ? lengthOffset : widthOffset,
                            yOffset,
                            axis == Axis.Z ? lengthOffset : widthOffset);
                    BlockState blockState = level.getBlockState(pos);
                    if (!HorizontalMultiFluidTankBlock.isVessel(blockState)) {
                        continue;
                    }

                    HorizontalMultiFluidTankBlock.Shape shape = HorizontalMultiFluidTankBlock.Shape.PLAIN;
                    if (window) {
                        if (windowType == HorizontalMultiFluidTankBlock.WindowType.SIDE_HORIZONTAL) {
                            if (yOffset == width / 2) {
                                shape = HorizontalMultiFluidTankBlock.Shape.WINDOW;
                            }
                        } else if (windowType == HorizontalMultiFluidTankBlock.WindowType.SIDE_WIDE || height <= 1) {
                            if (widthOffset == 0 || widthOffset == width - 1) {
                                if (width == 1) {
                                    shape = HorizontalMultiFluidTankBlock.Shape.WINDOW;
                                } else if (yOffset == 0) {
                                    shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_TOP;
                                } else if (yOffset == width - 1) {
                                    shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_BOTTOM;
                                } else {
                                    shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_MIDDLE;
                                }
                            }
                        } else {
                            int windowOffset = windowType == HorizontalMultiFluidTankBlock.WindowType.SIDE_NARROW_ENDS ? 0
                                    : Math.max(1, height / 3 - 1);
                            if ((lengthOffset == windowOffset || lengthOffset == height - 1 - windowOffset)
                                    && (widthOffset == 0 || widthOffset == width - 1)) {
                                if (width == 1) {
                                    shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_SINGLE;
                                } else if (yOffset == 0) {
                                    shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_TOP_SINGLE;
                                } else if (yOffset == width - 1) {
                                    shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_BOTTOM_SINGLE;
                                } else {
                                    shape = HorizontalMultiFluidTankBlock.Shape.WINDOW_MIDDLE_SINGLE;
                                }
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

    @Override
    protected void onControllerFluidChanged(FluidStack[] newFluidStack) {
        if (!hasLevel()) {
            return;
        }

        Axis axis = getAxis();
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

        for (int lengthOffset = 0; lengthOffset < height; lengthOffset++) {
            boolean isBright = lengthOffset < maxY;
            int actualLuminosity = isBright ? luminosity : luminosity > 0 ? 1 : 0;

            for (int yOffset = 0; yOffset < width; yOffset++) {
                for (int widthOffset = 0; widthOffset < width; widthOffset++) {
                    BlockPos pos = worldPosition.offset(
                            axis == Axis.X ? lengthOffset : widthOffset,
                            yOffset,
                            axis == Axis.Z ? lengthOffset : widthOffset);
                    HorizontalMultiFluidTankBlockEntity tankAt = partAt(getType(), level, pos);
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

    public Axis getAxis() {
        return getBlockState().getValue(HorizontalMultiFluidTankBlock.AXIS);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public HorizontalMultiFluidTankBlockEntity getControllerBE() {
        if (isController() || !hasLevel()) {
            return this;
        }
        BlockEntity blockEntity = level.getBlockEntity(controller);
        return blockEntity instanceof HorizontalMultiFluidTankBlockEntity tank ? tank : null;
    }

    @Override
    public void notifyMultiUpdated() {
        BlockState state = getBlockState();
        if (HorizontalMultiFluidTankBlock.isVessel(state)) {
            Axis axis = getAxis();
            state = state.setValue(HorizontalMultiFluidTankBlock.NEGATIVE,
                    axis == Axis.X ? getController().getX() == getBlockPos().getX()
                            : getController().getZ() == getBlockPos().getZ());
            state = state.setValue(HorizontalMultiFluidTankBlock.POSITIVE,
                    axis == Axis.X ? getController().getX() + height - 1 == getBlockPos().getX()
                            : getController().getZ() + height - 1 == getBlockPos().getZ());
            level.setBlock(getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }
        if (isController()) {
            setWindows(window);
        }
        onControllerFluidChanged(tankInventory.getFluids());
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
        if (data == null || data instanceof HorizontalMultiFluidTankBlock.WindowType) {
            if (data != null && !window) {
                return data;
            }
            return window ? windowType : null;
        }
        return data;
    }

    @Override
    public Axis getMainConnectionAxis() {
        return getAxis();
    }

    @Override
    public int getMaxLength(Axis longAxis, int width) {
        return longAxis == Axis.Y ? getMaxWidth() : getMaxHeight();
    }

    @Override
    protected Vec3 getRenderExtent() {
        Axis axis = getAxis();
        return new Vec3(
            axis == Axis.X ? height - 1 : width - 1,
            width - 1,
            axis == Axis.Z ? height - 1 : width - 1);
    }

    @Override
    protected void restoreWindowStateOnRead(CompoundTag compound) {
        window = compound.getBoolean("Window");
        windowType = compound.contains("WindowType")
                ? HorizontalMultiFluidTankBlock.WindowType.valueOf(compound.getString("WindowType"))
                : HorizontalMultiFluidTankBlock.WindowType.SIDE_WIDE;
    }

    @Override
    protected void writeWindowState(CompoundTag compound) {
        compound.putBoolean("Window", window);
        compound.putString("WindowType", windowType.name());
    }

    @Override
    protected void writeWindowStateSafe(CompoundTag compound) {
        compound.putBoolean("Window", window);
        compound.putString("WindowType", windowType.name());
    }

    @Override
    protected BlockState applyRemoveControllerShape(BlockState state) {
        return state.setValue(HorizontalMultiFluidTankBlock.POSITIVE, true)
                .setValue(HorizontalMultiFluidTankBlock.NEGATIVE, true)
                .setValue(HorizontalMultiFluidTankBlock.SHAPE,
                        window ? HorizontalMultiFluidTankBlock.Shape.WINDOW : HorizontalMultiFluidTankBlock.Shape.PLAIN);
    }

    @Override
    public boolean isOwnBlockState(BlockState state) {
        return HorizontalMultiFluidTankBlock.isVessel(state);
    }
}
