package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

public class MultiFluidTankBlockEntity extends AbstractMultiFluidTankBlockEntity<MultiFluidTankBlockEntity> {

    protected MultiFluidTankBlock.WindowStyle windowStyle;

    public MultiFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        windowStyle = MultiFluidTankBlock.WindowStyle.FULL;
    }

    @Override
    protected SmartMultiFluidTank createInventory() {
        return new SmartMultiFluidTank(getCapacityMultiplier(), TANKS, this::onControllerFluidChanged);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            AllBlockEntities.MULTI_FLUID_TANK.get(),
            (be, side) -> {
                if (be.fluidCapability == null)
                    be.refreshCapability();
                return be.fluidCapability;
            }
        );
    }

    @Override
    public void tick() {
        super.tick();
        chaseFluidLevel();
    }

    public MultiFluidTankBlock.WindowStyle getWindowStyle() {
        return windowStyle;
    }

    public static void cycleWindowStyle(MultiFluidTankBlockEntity be) {
        MultiFluidTankBlockEntity controllerBE = be.getControllerBE();
        if (controllerBE == null)
            return;
        controllerBE.setWindowStyle(controllerBE.windowStyle.nextAllowed(controllerBE.width));
    }

    public void setWindowStyle(MultiFluidTankBlock.WindowStyle style) {
        style = style.normalizeForWidth(width);
        this.windowStyle = style;
        for (int yOffset = 0; yOffset < height; yOffset++) {
            for (int xOffset = 0; xOffset < width; xOffset++) {
                for (int zOffset = 0; zOffset < width; zOffset++) {
                    BlockPos pos = this.worldPosition.offset(xOffset, yOffset, zOffset);
                    BlockState blockState = level.getBlockState(pos);
                    if (!MultiFluidTankBlock.isTank(blockState))
                        continue;

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

    @Override
    protected void onWidthRestored() {
        windowStyle = windowStyle.normalizeForWidth(width);
    }

    @Override
    protected void onControllerFluidChanged(FluidStack[] newFluidStack) {
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

    @Nullable
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
    public void notifyMultiUpdated() {
        BlockState state = this.getBlockState();
        if (MultiFluidTankBlock.isTank(state)) {
            state = state.setValue(MultiFluidTankBlock.BOTTOM, getController().getY() == getBlockPos().getY());
            state = state.setValue(MultiFluidTankBlock.TOP, getController().getY() + height - 1 == getBlockPos().getY());
            level.setBlock(getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }
        if (isController())
            setWindowStyle(windowStyle);
        onControllerFluidChanged(tankInventory.getFluids());
        setChanged();
    }

    @Override
    public void setExtraData(@Nullable Object data) {
        if (data instanceof MultiFluidTankBlock.WindowStyle style)
            windowStyle = style;
    }

    @Override
    @Nullable
    public Object getExtraData() {
        return windowStyle;
    }

    @Override
    public Object modifyExtraData(Object data) {
        if (data instanceof MultiFluidTankBlock.WindowStyle existing)
            return MultiFluidTankBlock.WindowStyle.merge(existing, windowStyle);
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
    protected Vec3 getRenderExtent() {
        return new Vec3(width - 1, height - 1, width - 1);
    }

    @Override
    protected void restoreWindowStateOnRead(CompoundTag compound) {
        windowStyle = MultiFluidTankBlock.WindowStyle.safeParse(compound.getString("WindowStyle"));
    }

    @Override
    protected void writeWindowState(CompoundTag compound) {
        compound.putString("WindowStyle", windowStyle.getSerializedName());
    }

    @Override
    protected void writeWindowStateSafe(CompoundTag compound) {
        compound.putString("WindowStyle", windowStyle.getSerializedName());
    }

    @Override
    protected BlockState applyRemoveControllerShape(BlockState state) {
        MultiFluidTankBlock.Shape shape = getShapeForWindowStyle(windowStyle, 0, 0);
        return state.setValue(MultiFluidTankBlock.BOTTOM, true)
            .setValue(MultiFluidTankBlock.TOP, true)
            .setValue(MultiFluidTankBlock.SHAPE, shape);
    }

    @Override
    public boolean isOwnBlockState(BlockState state) {
        return MultiFluidTankBlock.isTank(state);
    }
}
