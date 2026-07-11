package com.yision.fluidlogistics.content.logistics.copperFrogport;

import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlock;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CopperFrogportBlock extends FrogportBlock {

    public static final DirectionProperty ATTACHED_DIRECTION =
        DirectionProperty.create("attached_direction");

    private static final VoxelShape BODY = Block.box(2, 2, 2, 14, 14, 14);
    private static final VoxelShape DOWN_SHAPE =
        Shapes.or(BODY, Block.box(0, 0, 0, 16, 4, 16));
    private static final VoxelShape UP_SHAPE =
        Shapes.or(BODY, Block.box(0, 12, 0, 16, 16, 16));
    private static final VoxelShape NORTH_SHAPE =
        Shapes.or(BODY, Block.box(0, 0, 0, 16, 16, 4));
    private static final VoxelShape SOUTH_SHAPE =
        Shapes.or(BODY, Block.box(0, 0, 12, 16, 16, 16));
    private static final VoxelShape WEST_SHAPE =
        Shapes.or(BODY, Block.box(0, 0, 0, 4, 16, 16));
    private static final VoxelShape EAST_SHAPE =
        Shapes.or(BODY, Block.box(12, 0, 0, 16, 16, 16));

    public CopperFrogportBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ATTACHED_DIRECTION, Direction.UP));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (getAttachedDirection(state)) {
            case DOWN -> DOWN_SHAPE;
            case UP -> UP_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(ATTACHED_DIRECTION, context.getClickedFace().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ATTACHED_DIRECTION,
            rotation.rotate(getAttachedDirection(state)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(getAttachedDirection(state)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ATTACHED_DIRECTION);
    }

    @Override
    public BlockEntityType<? extends CopperFrogportBlockEntity> getBlockEntityType() {
        return AllBlockEntities.COPPER_FROGPORT.get();
    }

    public static Direction getAttachedDirection(BlockState state) {
        if (state.hasProperty(ATTACHED_DIRECTION)) {
            return state.getValue(ATTACHED_DIRECTION);
        }
        return Direction.UP;
    }

    public static Vec3 getConnectionSource(BlockPos pos, BlockState state) {
        return getConnectionSource(pos, getAttachedDirection(state));
    }

    public static Vec3 getConnectionSource(BlockPos pos, Direction attachedDirection) {
        return Vec3.atCenterOf(pos).add(
            attachedDirection.getStepX() * 0.5,
            attachedDirection.getStepY() * 0.5,
            attachedDirection.getStepZ() * 0.5
        );
    }
}
