package com.yision.fluidlogistics.block.MultiFluidAccessPort;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.content.redstone.DirectedDirectionalBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class MultiFluidAccessPortBlock extends DirectedDirectionalBlock
    implements IBE<MultiFluidAccessPortBlockEntity>, IWrenchable, IBlockExtension {

    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;

    public MultiFluidAccessPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ATTACHED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(ATTACHED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        Direction preferredFacing = null;

        for (Direction face : context.getNearestLookingDirections()) {
            BlockPos adjacentPos = context.getClickedPos().relative(face);
            IFluidHandler handler =
                context.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, adjacentPos, face.getOpposite());
            if (handler != null) {
                preferredFacing = face;
                break;
            }
        }

        if (preferredFacing == null) {
            Direction facing = context.getNearestLookingDirection();
            preferredFacing = context.getPlayer() != null && context.getPlayer().isShiftKeyDown() ? facing
                : facing.getOpposite();
        }

        if (preferredFacing.getAxis() == Axis.Y) {
            state = state.setValue(TARGET, preferredFacing == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR);
            preferredFacing = context.getHorizontalDirection().getOpposite();
        }

        return state.setValue(FACING, preferredFacing);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (oldState.getBlock() == state.getBlock()) {
            return;
        }
        withBlockEntityDo(level, pos, MultiFluidAccessPortBlockEntity::updateConnectedStorage);
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(state, level, pos, neighbor);
        withBlockEntityDo(level, pos, MultiFluidAccessPortBlockEntity::updateConnectedStorage);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
        boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        withBlockEntityDo(level, pos, MultiFluidAccessPortBlockEntity::updateConnectedStorage);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
        Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        Direction side = hitResult.getDirection();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        FluidStack previousFluid = handler.getFluidInTank(0).copy();
        FluidHelper.FluidExchange exchange = tryEmpty(level, player, hand, stack, handler);
        if (exchange == null) {
            exchange = tryFill(level, player, hand, stack, handler);
        }

        if (exchange == null) {
            if (GenericItemEmptying.canItemBeEmptied(level, stack) || GenericItemFilling.canItemBeFilled(level, stack)) {
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, MultiFluidAccessPortBlockEntity::updateConnectedStorage);
        }

        FluidStack currentFluid = handler.getFluidInTank(0);
        if (!level.isClientSide && !FluidStack.isSameFluidSameComponents(previousFluid, currentFluid)) {
            float pitch = Mth.clamp(1 - (currentFluid.getAmount() / 16000f), 0, 1);
            pitch = pitch / 1.5f + .5f + (level.random.nextFloat() - .5f) / 4f;
            level.playSound(null, pos,
                exchange == FluidHelper.FluidExchange.ITEM_TO_TANK ? FluidHelper.getEmptySound(currentFluid)
                    : FluidHelper.getFillSound(previousFluid),
                net.minecraft.sounds.SoundSource.BLOCKS, .5f, pitch);
        }

        return ItemInteractionResult.SUCCESS;
    }

    private FluidHelper.FluidExchange tryEmpty(Level level, Player player, InteractionHand hand, ItemStack heldItem,
        IFluidHandler handler) {
        if (!GenericItemEmptying.canItemBeEmptied(level, heldItem)) {
            return null;
        }

        Pair<FluidStack, ItemStack> emptyingResult = GenericItemEmptying.emptyItem(level, heldItem, true);
        FluidStack fluidStack = emptyingResult.getFirst();
        if (fluidStack.isEmpty() || fluidStack.getAmount() != handler.fill(fluidStack, IFluidHandler.FluidAction.SIMULATE)) {
            return null;
        }

        if (level.isClientSide) {
            return FluidHelper.FluidExchange.ITEM_TO_TANK;
        }

        ItemStack copyOfHeld = heldItem.copy();
        emptyingResult = GenericItemEmptying.emptyItem(level, copyOfHeld, false);
        handler.fill(fluidStack, IFluidHandler.FluidAction.EXECUTE);

        if (!player.isCreative()) {
            if (copyOfHeld.isEmpty()) {
                player.setItemInHand(hand, emptyingResult.getSecond());
            } else {
                player.setItemInHand(hand, copyOfHeld);
                player.getInventory().placeItemBackInInventory(emptyingResult.getSecond());
            }
        }

        return FluidHelper.FluidExchange.ITEM_TO_TANK;
    }

    private FluidHelper.FluidExchange tryFill(Level level, Player player, InteractionHand hand, ItemStack heldItem,
        IFluidHandler handler) {
        if (!GenericItemFilling.canItemBeFilled(level, heldItem)) {
            return null;
        }

        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (fluid.isEmpty()) {
                continue;
            }
            int requiredAmount = GenericItemFilling.getRequiredAmountForItem(level, heldItem, fluid.copy());
            if (requiredAmount == -1 || requiredAmount > fluid.getAmount()) {
                continue;
            }

            if (level.isClientSide) {
                return FluidHelper.FluidExchange.TANK_TO_ITEM;
            }

            ItemStack held = player.isCreative() ? heldItem.copy() : heldItem;
            ItemStack out = GenericItemFilling.fillItem(level, requiredAmount, held, fluid.copy());
            FluidStack drained = fluid.copyWithAmount(requiredAmount);
            handler.drain(drained, IFluidHandler.FluidAction.EXECUTE);

            if (!player.isCreative()) {
                player.setItemInHand(hand, held);
                player.getInventory().placeItemBackInInventory(out);
            }
            return FluidHelper.FluidExchange.TANK_TO_ITEM;
        }

        return null;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!state.getValue(ATTACHED)) {
            return 0;
        }
        BlockPos targetPos = pos.relative(getTargetDirection(state));
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.is(this)) {
            return 0;
        }
        return targetState.hasAnalogOutputSignal() ? targetState.getAnalogOutputSignal(level, targetPos) : 0;
    }

    @Override
    public Class<MultiFluidAccessPortBlockEntity> getBlockEntityClass() {
        return MultiFluidAccessPortBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MultiFluidAccessPortBlockEntity> getBlockEntityType() {
        return AllBlockEntities.MULTI_FLUID_ACCESS_PORT.get();
    }
}
