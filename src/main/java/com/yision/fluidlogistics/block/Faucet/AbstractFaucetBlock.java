package com.yision.fluidlogistics.block.Faucet;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import java.util.EnumMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;

@SuppressWarnings("deprecation")
public abstract class AbstractFaucetBlock<T extends AbstractFaucetBlockEntity> extends HorizontalDirectionalBlock implements IBE<T>, IWrenchable {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final EnumMap<Direction, VoxelShape> SHAPES = buildShapes();

    protected AbstractFaucetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, OPEN, POWERED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = resolvePlacementFacing(level, pos, context.getHorizontalDirection().getOpposite());
        if (facing == null) {
            return null;
        }

        return stateForPlacement(level, pos, facing);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos currentPos, BlockPos neighborPos) {
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }

        if (state.getValue(OPEN) && neighborPos.equals(pos.below())) {
            withBlockEntityDo(level, pos, AbstractFaucetBlockEntity::onTargetChanged);
        }

        scheduleSelfTick(level, pos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!hasSourceForFacing(level, pos, state.getValue(FACING))) {
            level.destroyBlock(pos, true);
            return;
        }

        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) == powered) {
            return;
        }

        level.setBlock(pos, updatePowerState(state, powered), Block.UPDATE_CLIENTS);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        if (AllItems.WRENCH.isIn(stack)) {
            return InteractionResult.PASS;
        }

        toggleManually(level, pos, state);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide || movedByPiston || !oldState.is(this)) {
            return;
        }

        if (!hasSourceForFacing(level, pos, state.getValue(FACING))) {
            level.destroyBlock(pos, true);
            return;
        }

        if (oldState.getValue(FACING) != state.getValue(FACING) && state.getValue(OPEN)) {
            withBlockEntityDo(level, pos, AbstractFaucetBlockEntity::onTargetChanged);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState rotated = getRotatedBlockState(state, context.getClickedFace());

        if (!hasSourceForFacing(level, pos, rotated.getValue(FACING))) {
            if (!level.isClientSide) {
                level.destroyBlock(pos, true);
                IWrenchable.playRemoveSound(level, pos);
            }
            return InteractionResult.SUCCESS;
        }

        KineticBlockEntity.switchToBlockState(level, pos, updateAfterWrenched(rotated, context));
        if (level.getBlockState(pos) != state) {
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    public static boolean isInfiniteWaterSource(BlockState state) {
        return state.is(BlockTags.LEAVES)
                && state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    private @Nullable Direction resolvePlacementFacing(Level level, BlockPos pos, Direction preferredFacing) {
        if (hasSourceForFacing(level, pos, preferredFacing)) {
            return preferredFacing;
        }

        for (Direction candidate : Direction.Plane.HORIZONTAL) {
            if (candidate != preferredFacing && hasSourceForFacing(level, pos, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private BlockState stateForPlacement(Level level, BlockPos pos, Direction facing) {
        boolean powered = level.hasNeighborSignal(pos);
        return defaultBlockState().setValue(FACING, facing).setValue(POWERED, powered).setValue(OPEN, powered);
    }

    private BlockState updatePowerState(BlockState state, boolean powered) {
        return state.setValue(POWERED, powered).setValue(OPEN, powered || state.getValue(OPEN) && !state.getValue(POWERED));
    }

    private void toggleManually(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide && !state.getValue(POWERED)) {
            toggleOpen(level, pos, state);
        }
    }

    private void toggleOpen(Level level, BlockPos pos, BlockState state) {
        boolean open = state.getValue(OPEN);
        level.setBlockAndUpdate(pos, state.setValue(OPEN, !open));
        playToggleSound(level, pos, open);
    }

    private boolean hasSourceForFacing(LevelReader level, BlockPos pos, Direction facing) {
        return hasFluidSource(level, pos.relative(facing.getOpposite()), facing);
    }

    private boolean hasFluidSource(LevelReader level, BlockPos sourcePos, Direction side) {
        BlockState sourceState = level.getBlockState(sourcePos);
        if (isInfiniteWaterSource(sourceState)) {
            return true;
        }

        BlockEntity blockEntity = level.getBlockEntity(sourcePos);
        if (blockEntity == null) {
            return false;
        }

        IFluidHandler sidedHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
        if (sidedHandler != null && sidedHandler.getTanks() > 0) {
            return true;
        }

        IFluidHandler unsidedHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        return unsidedHandler != null && unsidedHandler.getTanks() > 0;
    }

    private void scheduleSelfTick(Level level, BlockPos pos) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    private void playToggleSound(Level level, BlockPos pos, boolean wasOpen) {
        level.playSound(null, pos, wasOpen ? net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE
                : net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f,
                1.0f);
    }

    private static EnumMap<Direction, VoxelShape> buildShapes() {
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            shapes.put(direction, buildShape(direction));
        }
        return shapes;
    }

    private static VoxelShape buildShape(Direction facing) {
        VoxelShape shape = Shapes.empty();
        shape = Shapes.or(shape, rotatedBox(facing, 6, 6, 6, 10, 10, 15));
        shape = Shapes.or(shape, rotatedBox(facing, 5, 12, 7, 11, 13, 13));
        shape = Shapes.or(shape, rotatedBox(facing, 7, 10, 9, 9, 12, 11));
        shape = Shapes.or(shape, rotatedBox(facing, 6, 4, 6, 10, 6, 10));
        shape = Shapes.or(shape, rotatedBox(facing, 3, 3, 15, 13, 12.9, 17));
        return shape;
    }

    private static VoxelShape rotatedBox(Direction facing, double minX, double minY, double minZ, double maxX,
            double maxY, double maxZ) {
        double[] xs = {minX, maxX};
        double[] ys = {minY, maxY};
        double[] zs = {minZ, maxZ};
        double outMinX = Double.MAX_VALUE;
        double outMinY = Double.MAX_VALUE;
        double outMinZ = Double.MAX_VALUE;
        double outMaxX = -Double.MAX_VALUE;
        double outMaxY = -Double.MAX_VALUE;
        double outMaxZ = -Double.MAX_VALUE;

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    double[] point = transformPoint(facing, x, y, z);
                    outMinX = Math.min(outMinX, point[0]);
                    outMinY = Math.min(outMinY, point[1]);
                    outMinZ = Math.min(outMinZ, point[2]);
                    outMaxX = Math.max(outMaxX, point[0]);
                    outMaxY = Math.max(outMaxY, point[1]);
                    outMaxZ = Math.max(outMaxZ, point[2]);
                }
            }
        }

        return Block.box(outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ);
    }

    private static double[] transformPoint(Direction facing, double x, double y, double z) {
        return switch (facing) {
            case NORTH -> new double[] {x, y, z};
            case SOUTH -> new double[] {16 - x, y, 16 - z};
            case EAST -> new double[] {16 - z, y, x};
            case WEST -> new double[] {z, y, 16 - x};
            default -> new double[] {x, y, z};
        };
    }
}
