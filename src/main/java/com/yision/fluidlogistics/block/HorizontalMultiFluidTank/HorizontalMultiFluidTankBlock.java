package com.yision.fluidlogistics.block.HorizontalMultiFluidTank;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.fluid.FluidHelper.FluidExchange;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.createmod.catnip.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

@SuppressWarnings("deprecation")
public class HorizontalMultiFluidTankBlock extends Block
        implements IWrenchable, IBE<HorizontalMultiFluidTankBlockEntity> {

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
        if (!FeatureToggle.isEnabled(FeatureToggle.HORIZONTAL_MULTI_FLUID_TANK)) {
            return null;
        }
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
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean moved) {
        if (oldState.getBlock() == state.getBlock() || moved) {
            return;
        }
        withBlockEntityDo(world, pos, HorizontalMultiFluidTankBlockEntity::scheduleConnectivityUpdate);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter world, BlockPos pos) {
        return 0;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        withBlockEntityDo(context.getLevel(), context.getClickedPos(), HorizontalMultiFluidTankBlockEntity::toggleWindows);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult ray) {
        ItemStack heldItem = player.getItemInHand(hand);
        boolean onClient = world.isClientSide;

        if (heldItem.isEmpty()) {
            return InteractionResult.PASS;
        }

        FluidExchange exchange = null;
        HorizontalMultiFluidTankBlockEntity be = ConnectivityHandler.partAt(getBlockEntityType(), world, pos);
        if (be == null) {
            return InteractionResult.FAIL;
        }

        LazyOptional<IFluidHandler> tankCapability = be.getCapability(ForgeCapabilities.FLUID_HANDLER);
        if (!tankCapability.isPresent()) {
            return InteractionResult.PASS;
        }

        IFluidHandler fluidTank = tankCapability.orElse(null);
        FluidStack prevFluidInTank = fluidTank.getFluidInTank(0).copy();

        if (FluidHelper.tryEmptyItemIntoBE(world, player, hand, heldItem, be)) {
            exchange = FluidExchange.ITEM_TO_TANK;
        } else if (FluidHelper.tryFillItemFromBE(world, player, hand, heldItem, be)) {
            exchange = FluidExchange.TANK_TO_ITEM;
        }

        if (exchange == null) {
            if (GenericItemEmptying.canItemBeEmptied(world, heldItem)
                    || GenericItemFilling.canItemBeFilled(world, heldItem)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        SoundEvent soundevent = null;
        BlockState fluidState = null;
        FluidStack fluidInTank = fluidTank.getFluidInTank(0);

        if (exchange == FluidExchange.ITEM_TO_TANK) {
            Fluid fluid = fluidInTank.getFluid();
            fluidState = fluid.defaultFluidState().createLegacyBlock();
            soundevent = FluidHelper.getEmptySound(fluidInTank);
        }

        if (exchange == FluidExchange.TANK_TO_ITEM) {
            Fluid fluid = prevFluidInTank.getFluid();
            fluidState = fluid.defaultFluidState().createLegacyBlock();
            soundevent = FluidHelper.getFillSound(prevFluidInTank);
        }

        if (soundevent != null && !onClient) {
            float pitch = Mth.clamp(
                    1 - (1f * fluidInTank.getAmount() / (HorizontalMultiFluidTankBlockEntity.getCapacityMultiplier() * 16)),
                    0, 1);
            pitch = pitch / 1.5f + .5f + (world.random.nextFloat() - .5f) / 4f;
            world.playSound(null, pos, soundevent, SoundSource.BLOCKS, .5f, pitch);
        }

        if (!sameFluid(fluidInTank, prevFluidInTank)) {
            HorizontalMultiFluidTankBlockEntity controllerBE = be.getControllerBE();
            if (controllerBE != null) {
                if (fluidState != null && onClient) {
                    BlockParticleOption blockParticleData = new BlockParticleOption(ParticleTypes.BLOCK, fluidState);
                    float fluidLevel = (float) fluidInTank.getAmount() / fluidTank.getTankCapacity(0);

                    boolean reversed = fluidInTank.getFluid().getFluidType().isLighterThanAir();
                    if (reversed) {
                        fluidLevel = 1 - fluidLevel;
                    }

                    Vec3 vec = ray.getLocation();
                    vec = new Vec3(vec.x, controllerBE.getBlockPos().getY() + fluidLevel * (controllerBE.getWidth() - .5f) + .25f,
                            vec.z);
                    Vec3 motion = player.position().subtract(vec).scale(1 / 20f);
                    vec = vec.add(motion);
                    world.addParticle(blockParticleData, vec.x, vec.y, vec.z, motion.x, motion.y, motion.z);
                    return InteractionResult.SUCCESS;
                }

                controllerBE.sendDataImmediately();
                controllerBE.setChanged();
            }
        }

        return InteractionResult.SUCCESS;
    }

    static final VoxelShape CAMPFIRE_SMOKE_CLIP = Block.box(0, 4, 0, 16, 16, 16);

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context == CollisionContext.empty() ? CAMPFIRE_SMOKE_CLIP : state.getShape(level, pos);
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter reader, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos currentPos, BlockPos neighborPos) {
        return state;
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.hasBlockEntity() && (state.getBlock() != newState.getBlock() || !newState.hasBlockEntity())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof HorizontalMultiFluidTankBlockEntity tankBE) {
                world.removeBlockEntity(pos);
                ConnectivityHandler.splitMulti(tankBE);
            }
        }
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

    public static final SoundType SILENCED_METAL =
            new ForgeSoundType(0.1F, 1.5F, () -> SoundEvents.METAL_BREAK, () -> SoundEvents.METAL_STEP,
                    () -> SoundEvents.METAL_PLACE, () -> SoundEvents.METAL_HIT, () -> SoundEvents.METAL_FALL);

    @Override
    public SoundType getSoundType(BlockState state, LevelReader world, BlockPos pos, Entity entity) {
        SoundType soundType = super.getSoundType(state, world, pos, entity);
        if (entity != null && entity.getPersistentData().contains("SilenceVesselSound")) {
            return SILENCED_METAL;
        }
        return soundType;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos) {
        return getBlockEntityOptional(worldIn, pos).map(HorizontalMultiFluidTankBlockEntity::getControllerBE)
                .map(HorizontalMultiFluidTankBlockEntity::getComparatorOutput).orElse(0);
    }

    private static boolean sameFluid(FluidStack first, FluidStack second) {
        return first.isFluidEqual(second) && FluidStack.areFluidStackTagsEqual(first, second);
    }
}
