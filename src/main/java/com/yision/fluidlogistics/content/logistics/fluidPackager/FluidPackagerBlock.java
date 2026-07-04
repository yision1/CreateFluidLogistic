package com.yision.fluidlogistics.content.logistics.fluidPackager;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.content.fluids.multiFluidAccessPort.MultiFluidAccessPortBlockEntity;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;

public class FluidPackagerBlock extends PackagerBlock {

    public FluidPackagerBlock(Properties properties) {
        super(properties);
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
            if (level.getBlockEntity(targetPos) instanceof MultiFluidAccessPortBlockEntity port
                && port.blocksFluidPackagerPlacement(preferredFacing)) {
                CreateLang.translate("fluid_packager.no_portable_fluid_interface")
                    .sendStatus(player);
                return null;
            }
        }

        return defaultBlockState()
            .setValue(POWERED, level.hasNeighborSignal(context.getClickedPos()))
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
    public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
        return AllBlockEntities.FLUID_PACKAGER.get();
    }
}
