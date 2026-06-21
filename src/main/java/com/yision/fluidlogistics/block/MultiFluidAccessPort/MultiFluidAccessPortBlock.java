package com.yision.fluidlogistics.block.MultiFluidAccessPort;

import javax.annotation.Nullable;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.content.redstone.DirectedDirectionalBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

@SuppressWarnings("deprecation")
public class MultiFluidAccessPortBlock extends DirectedDirectionalBlock
    implements IBE<MultiFluidAccessPortBlockEntity>, IWrenchable {

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
        if (!FeatureToggle.isEnabled(FeatureToggle.MULTI_FLUID_ACCESS_PORT)) {
            return null;
        }
        BlockState state = defaultBlockState();
        Direction preferredFacing = null;

        for (Direction face : context.getNearestLookingDirections()) {
            BlockPos adjacentPos = context.getClickedPos().relative(face);
            IFluidHandler handler = getBlockFluidHandler(context.getLevel(), adjacentPos, face.getOpposite());
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
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
        boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        withBlockEntityDo(level, pos, MultiFluidAccessPortBlockEntity::updateConnectedStorage);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
        BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        boolean onClient = level.isClientSide;

        if (stack.isEmpty()) {
            return InteractionResult.PASS;
        }

        IFluidHandler handler = getBlockFluidHandler(level, pos, hitResult.getDirection());
        if (handler == null) {
            return InteractionResult.PASS;
        }

        FluidStack previousFluid = handler.getTanks() > 0 ? handler.getFluidInTank(0).copy() : FluidStack.EMPTY;
        FluidHelper.FluidExchange exchange = tryEmpty(level, player, hand, stack, handler);
        if (exchange == null) {
            exchange = tryFill(level, player, hand, stack, handler);
        }

        if (exchange == null) {
            if (GenericItemEmptying.canItemBeEmptied(level, stack) || GenericItemFilling.canItemBeFilled(level, stack)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (!onClient) {
            withBlockEntityDo(level, pos, MultiFluidAccessPortBlockEntity::updateConnectedStorage);
        }

        FluidStack currentFluid = handler.getTanks() > 0 ? handler.getFluidInTank(0).copy() : FluidStack.EMPTY;
        if (!onClient && !sameFluid(previousFluid, currentFluid)) {
            float pitch = Mth.clamp(1 - (currentFluid.getAmount() / 16000f), 0, 1);
            pitch = pitch / 1.5f + .5f + (level.random.nextFloat() - .5f) / 4f;
            level.playSound(null, pos,
                exchange == FluidHelper.FluidExchange.ITEM_TO_TANK ? FluidHelper.getEmptySound(currentFluid)
                    : FluidHelper.getFillSound(previousFluid),
                net.minecraft.sounds.SoundSource.BLOCKS, .5f, pitch);
        }

        return InteractionResult.SUCCESS;
    }

    private FluidHelper.FluidExchange tryEmpty(Level level, Player player, InteractionHand hand, ItemStack heldItem,
        IFluidHandler handler) {
        if (!GenericItemEmptying.canItemBeEmptied(level, heldItem)) {
            return null;
        }

        Pair<FluidStack, ItemStack> simulatedEmptying = GenericItemEmptying.emptyItem(level, heldItem, true);
        FluidStack simulatedFluid = simulatedEmptying.getFirst();
        if (simulatedFluid.isEmpty()) {
            return null;
        }

        int simulatedFill = handler.fill(simulatedFluid.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (simulatedFill != simulatedFluid.getAmount()) {
            return null;
        }

        if (level.isClientSide) {
            return FluidHelper.FluidExchange.ITEM_TO_TANK;
        }

        ItemStack copyOfHeld = heldItem.copy();
        Pair<FluidStack, ItemStack> actualEmptying = GenericItemEmptying.emptyItem(level, copyOfHeld, false);
        FluidStack actualFluid = actualEmptying.getFirst();
        if (!sameAmountFluid(actualFluid, simulatedFluid)) {
            return null;
        }

        int actualFill = handler.fill(actualFluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (actualFill != actualFluid.getAmount()) {
            return null;
        }

        if (!player.isCreative()) {
            if (copyOfHeld.isEmpty()) {
                player.setItemInHand(hand, actualEmptying.getSecond());
            } else {
                player.setItemInHand(hand, copyOfHeld);
                player.getInventory().placeItemBackInInventory(actualEmptying.getSecond());
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
            if (requiredAmount == -1) {
                continue;
            }

            FluidStack requestedDrain = copyWithAmount(fluid, requiredAmount);
            if (requestedDrain.isEmpty()) {
                continue;
            }

            FluidStack simulatedDrain = handler.drain(requestedDrain.copy(), IFluidHandler.FluidAction.SIMULATE);
            if (!sameAmountFluid(simulatedDrain, requestedDrain)) {
                continue;
            }

            if (level.isClientSide) {
                return FluidHelper.FluidExchange.TANK_TO_ITEM;
            }

            ItemStack workingStack = heldItem.copy();
            ItemStack out = GenericItemFilling.fillItem(level, requiredAmount, workingStack, simulatedDrain.copy());
            if (out.isEmpty()) {
                continue;
            }

            FluidStack actualDrain = handler.drain(simulatedDrain.copy(), IFluidHandler.FluidAction.EXECUTE);
            if (!sameAmountFluid(actualDrain, simulatedDrain)) {
                continue;
            }

            if (!player.isCreative()) {
                player.setItemInHand(hand, workingStack);
                player.getInventory().placeItemBackInInventory(out);
            }
            return FluidHelper.FluidExchange.TANK_TO_ITEM;
        }

        return null;
    }

    private @Nullable IFluidHandler getBlockFluidHandler(Level level, BlockPos pos, @Nullable Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }

        IFluidHandler sidedHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
        if (sidedHandler != null || side == null) {
            return sidedHandler;
        }

        return blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
    }

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    private static boolean sameFluid(FluidStack first, FluidStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }

        return first.isFluidEqual(second) && FluidStack.areFluidStackTagsEqual(first, second);
    }

    private static boolean sameAmountFluid(FluidStack actual, FluidStack expected) {
        return !actual.isEmpty()
            && actual.getAmount() == expected.getAmount()
            && sameFluid(actual, expected);
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
