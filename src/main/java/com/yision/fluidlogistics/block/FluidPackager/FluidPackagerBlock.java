package com.yision.fluidlogistics.block.FluidPackager;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.item.FluidPackageItem;
import com.yision.fluidlogistics.util.IPackagerOverrideData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;

public class FluidPackagerBlock extends WrenchableDirectionalBlock implements IBE<FluidPackagerBlockEntity>, IWrenchable {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    public FluidPackagerBlock(Properties properties) {
        super(properties);
        BlockState defaultBlockState = defaultBlockState();
        if (defaultBlockState.hasProperty(LINKED))
            defaultBlockState = defaultBlockState.setValue(LINKED, false);
        registerDefaultState(defaultBlockState.setValue(POWERED, false));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferredFacing = null;
        Level level = context.getLevel();

        for (Direction face : context.getNearestLookingDirections()) {
            BlockPos adjacentPos = context.getClickedPos().relative(face);
            BlockEntity be = level.getBlockEntity(adjacentPos);
            if (be instanceof FluidPackagerBlockEntity)
                continue;

            var sidedHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, adjacentPos, face.getOpposite());
            var unsidedHandler = sidedHandler != null ? sidedHandler
                : level.getCapability(Capabilities.FluidHandler.BLOCK, adjacentPos, null);
            if (unsidedHandler != null) {
                preferredFacing = face.getOpposite();
                break;
            }
        }

        Player player = context.getPlayer();
        if (preferredFacing == null) {
            Direction facing = context.getNearestLookingDirection();
            preferredFacing = player != null && player.isShiftKeyDown() ? facing : facing.getOpposite();
        }

        if (player != null && !(player instanceof FakePlayer)) {
            BlockPos targetPos = context.getClickedPos().relative(preferredFacing.getOpposite());
            BlockState targetState = level.getBlockState(targetPos);
            if (AllBlocks.PORTABLE_FLUID_INTERFACE.has(targetState)) {
                CreateLang.translate("fluid_packager.no_portable_fluid_interface")
                    .sendStatus(player);
                return null;
            }
        }

        return super.getStateForPlacement(context)
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()))
                .setValue(FACING, preferredFacing);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (AllItems.WRENCH.isIn(stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (AllBlocks.FACTORY_GAUGE.isIn(stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (AllBlocks.STOCK_LINK.isIn(stack) && !(state.hasProperty(LINKED) && state.getValue(LINKED)))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (AllBlocks.PACKAGE_FROGPORT.isIn(stack))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (onBlockEntityUseItemOn(level, pos, be -> {
            if (be.heldBox.isEmpty()) {
                if (be.animationTicks > 0)
                    return ItemInteractionResult.SUCCESS;
                if (FluidPackageItem.isFluidPackage(stack)) {
                    if (level.isClientSide())
                        return ItemInteractionResult.SUCCESS;
                    if (!be.unwrapBox(stack.copy(), true))
                        return ItemInteractionResult.SUCCESS;
                    be.unwrapBox(stack.copy(), false);
                    be.triggerStockCheck();
                    stack.shrink(1);
                    AllSoundEvents.DEPOT_PLOP.playOnServer(level, pos);
                    if (stack.isEmpty())
                        player.setItemInHand(hand, ItemStack.EMPTY);
                    return ItemInteractionResult.SUCCESS;
                }
                return ItemInteractionResult.SUCCESS;
            }
            if (be.animationTicks > 0)
                return ItemInteractionResult.SUCCESS;
            if (!level.isClientSide()) {
                player.getInventory().placeItemBackInInventory(be.heldBox.copy());
                AllSoundEvents.playItemPickup(player);
                be.heldBox = ItemStack.EMPTY;
                be.notifyUpdate();
            }
            return ItemInteractionResult.SUCCESS;
        }).consumesAction())
            return ItemInteractionResult.SUCCESS;

        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(POWERED, LINKED));
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        if (neighbor.relative(state.getOptionalValue(FACING).orElse(Direction.UP)).equals(pos))
            withBlockEntityDo(level, pos, FluidPackagerBlockEntity::triggerStockCheck);
    }

    @Override
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos,
                                boolean isMoving) {
        if (worldIn.isClientSide)
            return;

        BlockEntity blockEntity = worldIn.getBlockEntity(pos);
        if (blockEntity instanceof IPackagerOverrideData data && data.fluidlogistics$isManualOverrideLocked()) {
            InvManipulationBehaviour behaviour = BlockEntityBehaviour.get(worldIn, pos, InvManipulationBehaviour.TYPE);
            if (behaviour != null)
                behaviour.onNeighborChanged(fromPos);
            return;
        }

        InvManipulationBehaviour behaviour = BlockEntityBehaviour.get(worldIn, pos, InvManipulationBehaviour.TYPE);
        if (behaviour != null)
            behaviour.onNeighborChanged(fromPos);

        boolean previouslyPowered = state.getValue(POWERED);
        if (previouslyPowered == worldIn.hasNeighborSignal(pos))
            return;
        worldIn.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS);
        if (!previouslyPowered)
            withBlockEntityDo(worldIn, pos, FluidPackagerBlockEntity::activate);
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        IBE.onRemove(pState, pLevel, pPos, pNewState);
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side) {
        return false;
    }

    @Override
    public Class<FluidPackagerBlockEntity> getBlockEntityClass() {
        return FluidPackagerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FluidPackagerBlockEntity> getBlockEntityType() {
        return AllBlockEntities.FLUID_PACKAGER.get();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState pState) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState pState, Level pLevel, BlockPos pPos) {
        return getBlockEntityOptional(pLevel, pPos).map(pbe -> {
                    boolean empty = pbe.inventory.getStackInSlot(0).isEmpty();
                    if (pbe.animationTicks != 0)
                        empty = false;
                    return empty ? 0 : 15;
                })
                .orElse(0);
    }
}
