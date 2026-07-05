package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.createmod.catnip.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;

public class MultiFluidTankBlock extends AbstractMultiFluidTankBlock<MultiFluidTankBlockEntity> {

    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public static MultiFluidTankBlock regular(Properties properties) {
        return new MultiFluidTankBlock(properties);
    }

    protected MultiFluidTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TOP, true).setValue(BOTTOM, true).setValue(SHAPE, Shape.WINDOW));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(TOP, BOTTOM, SHAPE);
    }

    @Override
    protected String silenceSoundTag() {
        return "SilenceTankSound";
    }

    public static boolean isTank(BlockState state) {
        return state.getBlock() instanceof MultiFluidTankBlock;
    }

    @Override
    protected boolean isOwnBlockEntity(BlockEntity be) {
        return be instanceof MultiFluidTankBlockEntity;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        withBlockEntityDo(context.getLevel(), context.getClickedPos(), MultiFluidTankBlockEntity::cycleWindowStyle);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected float particleHeight(MultiFluidTankBlockEntity controllerBE) {
        return controllerBE.getHeight() - .5f;
    }

    @Override
    public Class<MultiFluidTankBlockEntity> getBlockEntityClass() {
        return MultiFluidTankBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MultiFluidTankBlockEntity> getBlockEntityType() {
        return AllBlockEntities.MULTI_FLUID_TANK.get();
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        boolean x = mirror == Mirror.FRONT_BACK;
        return switch (state.getValue(SHAPE)) {
            case WINDOW_NE -> state.setValue(SHAPE, x ? Shape.WINDOW_NW : Shape.WINDOW_SE);
            case WINDOW_NW -> state.setValue(SHAPE, x ? Shape.WINDOW_NE : Shape.WINDOW_SW);
            case WINDOW_SE -> state.setValue(SHAPE, x ? Shape.WINDOW_SW : Shape.WINDOW_NE);
            case WINDOW_SW -> state.setValue(SHAPE, x ? Shape.WINDOW_SE : Shape.WINDOW_NW);
            default -> state;
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        for (int i = 0; i < rotation.ordinal(); i++) {
            state = rotateOnce(state);
        }
        return state;
    }

    private BlockState rotateOnce(BlockState state) {
        return switch (state.getValue(SHAPE)) {
            case WINDOW_NE -> state.setValue(SHAPE, Shape.WINDOW_SE);
            case WINDOW_NW -> state.setValue(SHAPE, Shape.WINDOW_NE);
            case WINDOW_SE -> state.setValue(SHAPE, Shape.WINDOW_SW);
            case WINDOW_SW -> state.setValue(SHAPE, Shape.WINDOW_NW);
            default -> state;
        };
    }

    public enum Shape implements StringRepresentable {
        PLAIN, WINDOW, WINDOW_NW, WINDOW_SW, WINDOW_NE, WINDOW_SE;

        @Override
        public String getSerializedName() {
            return Lang.asId(name());
        }
    }

    public enum WindowStyle implements StringRepresentable {
        FULL,
        SINGLE,
        NONE;

        private static final java.util.Map<String, WindowStyle> BY_NAME =
                java.util.Arrays.stream(values())
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                ws -> ws.getSerializedName(), ws -> ws));

        public WindowStyle next() {
            return switch (this) {
                case FULL -> SINGLE;
                case SINGLE -> NONE;
                case NONE -> FULL;
            };
        }

        public WindowStyle nextAllowed(int width) {
            WindowStyle next = next();
            if (width <= 1 && next == SINGLE)
                return next.next();
            return next;
        }

        public WindowStyle normalizeForWidth(int width) {
            if (width <= 1 && this == SINGLE)
                return FULL;
            return this;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static WindowStyle safeParse(@org.jetbrains.annotations.Nullable String value) {
            if (value == null)
                return FULL;
            return BY_NAME.getOrDefault(value.toLowerCase(java.util.Locale.ROOT), FULL);
        }

        public static WindowStyle merge(WindowStyle a, WindowStyle b) {
            if (a == FULL || b == FULL)
                return FULL;
            if (a == SINGLE || b == SINGLE)
                return SINGLE;
            return NONE;
        }
    }
}
