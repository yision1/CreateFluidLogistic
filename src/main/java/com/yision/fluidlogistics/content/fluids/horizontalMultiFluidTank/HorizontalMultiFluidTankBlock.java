package com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.AbstractMultiFluidTankBlock;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.createmod.catnip.lang.Lang;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;

public class HorizontalMultiFluidTankBlock extends AbstractMultiFluidTankBlock<HorizontalMultiFluidTankBlockEntity> {

    public static final BooleanProperty POSITIVE = BooleanProperty.create("positive");
    public static final BooleanProperty NEGATIVE = BooleanProperty.create("negative");
    public static final EnumProperty<Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public static HorizontalMultiFluidTankBlock regular(Properties properties) {
        return new HorizontalMultiFluidTankBlock(properties);
    }

    protected HorizontalMultiFluidTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POSITIVE, true).setValue(NEGATIVE, true).setValue(AXIS, Axis.X)
                .setValue(SHAPE, Shape.WINDOW));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(POSITIVE, NEGATIVE, AXIS, SHAPE);
    }

    public static boolean isVessel(BlockState state) {
        return state.getBlock() instanceof HorizontalMultiFluidTankBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (ctx.getPlayer() == null || !ctx.getPlayer().isShiftKeyDown()) {
            BlockState placedOn = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(ctx.getClickedFace().getOpposite()));
            Axis preferredAxis = placedOn.getOptionalValue(AXIS).orElse(null);
            if (preferredAxis != null) {
                return defaultBlockState().setValue(AXIS, preferredAxis);
            }
        }
        return defaultBlockState().setValue(AXIS, ctx.getHorizontalDirection().getAxis());
    }

    @Override
    protected String silenceSoundTag() {
        return "SilenceVesselSound";
    }

    @Override
    protected boolean isOwnBlockEntity(BlockEntity be) {
        return be instanceof HorizontalMultiFluidTankBlockEntity;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        withBlockEntityDo(context.getLevel(), context.getClickedPos(), HorizontalMultiFluidTankBlockEntity::toggleWindows);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected float particleHeight(HorizontalMultiFluidTankBlockEntity controllerBE) {
        return controllerBE.getWidth() - .5f;
    }

    @Override
    public Class<HorizontalMultiFluidTankBlockEntity> getBlockEntityClass() {
        return HorizontalMultiFluidTankBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HorizontalMultiFluidTankBlockEntity> getBlockEntityType() {
        return AllBlockEntities.HORIZONTAL_MULTI_FLUID_TANK.get();
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }

        Axis mirrorAxis = mirror == Mirror.FRONT_BACK ? Axis.X : Axis.Z;
        Axis axis = state.getValue(AXIS);
        if (axis == mirrorAxis) {
            return state.setValue(POSITIVE, state.getValue(NEGATIVE)).setValue(NEGATIVE, state.getValue(POSITIVE));
        }
        return state;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        for (int i = 0; i < rotation.ordinal(); i++) {
            state = rotateOnce(state);
        }
        return state;
    }

    private BlockState rotateOnce(BlockState state) {
        Axis axis = state.getValue(AXIS);
        if (axis == Axis.X) {
            return state.setValue(AXIS, Axis.Z);
        }
        if (axis == Axis.Z) {
            return state.setValue(AXIS, Axis.X).setValue(POSITIVE, state.getValue(NEGATIVE))
                    .setValue(NEGATIVE, state.getValue(POSITIVE));
        }
        return state;
    }

    public enum Shape implements StringRepresentable {
        PLAIN, WINDOW, WINDOW_TOP, WINDOW_MIDDLE, WINDOW_BOTTOM, WINDOW_SINGLE, WINDOW_TOP_SINGLE, WINDOW_MIDDLE_SINGLE,
        WINDOW_BOTTOM_SINGLE;

        @Override
        public String getSerializedName() {
            return Lang.asId(name());
        }

        public Shape nonSingleVariant() {
            return switch (this) {
                case WINDOW_SINGLE -> WINDOW;
                case WINDOW_TOP_SINGLE -> WINDOW_TOP;
                case WINDOW_MIDDLE_SINGLE -> WINDOW_MIDDLE;
                case WINDOW_BOTTOM_SINGLE -> WINDOW_BOTTOM;
                default -> this;
            };
        }
    }

    public enum WindowType implements StringRepresentable {
        SIDE_WIDE, SIDE_NARROW_ENDS, SIDE_NARROW_THIRDS, SIDE_HORIZONTAL;

        @Override
        public String getSerializedName() {
            return Lang.asId(name());
        }
    }
}
