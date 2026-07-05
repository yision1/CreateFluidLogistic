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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

@SuppressWarnings("deprecation")
public class FluidPackagerBlock extends PackagerBlock {

    public FluidPackagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferredFacing = null;
        Level level = context.getLevel();

        for (Direction face : context.getNearestLookingDirections()) {
            BlockEntity be = level.getBlockEntity(context.getClickedPos().relative(face));
            if (be instanceof FluidPackagerBlockEntity)
                continue;
            if (be != null && be.hasLevel() &&
                    be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).isPresent()) {
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
            if (blocksPortableFluidInterfacePort(level, context.getClickedPos())) {
                CreateLang.translate("fluid_packager.no_portable_fluid_interface")
                        .sendStatus(player);
                return null;
            }

            BlockPos targetPos = context.getClickedPos().relative(preferredFacing.getOpposite());
            if (AllBlocks.PORTABLE_FLUID_INTERFACE.has(level.getBlockState(targetPos))) {
                CreateLang.translate("fluid_packager.no_portable_fluid_interface")
                        .sendStatus(player);
                return null;
            }
        }

        return defaultBlockState()
                .setValue(POWERED, level.hasNeighborSignal(context.getClickedPos()))
                .setValue(FACING, preferredFacing);
    }

    private boolean blocksPortableFluidInterfacePort(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
            if (blockEntity instanceof MultiFluidAccessPortBlockEntity port
                    && port.blocksFluidPackagerPlacement(direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);

        if (AllItems.WRENCH.isIn(stack))
            return InteractionResult.PASS;
        
        if (AllBlocks.FACTORY_GAUGE.isIn(stack))
            return InteractionResult.PASS;
        if (AllBlocks.STOCK_LINK.isIn(stack) && !(state.hasProperty(LINKED) && state.getValue(LINKED)))
            return InteractionResult.PASS;
        if (AllBlocks.PACKAGE_FROGPORT.isIn(stack))
            return InteractionResult.PASS;

        if (onBlockEntityUse(level, pos, be -> {
            if (be.heldBox.isEmpty()) {
                if (be.animationTicks > 0)
                    return InteractionResult.SUCCESS;
                if (FluidPackageItem.isFluidPackage(stack)) {
                    if (level.isClientSide())
                        return InteractionResult.SUCCESS;
                    if (!be.unwrapBox(stack.copy(), true))
                        return InteractionResult.SUCCESS;
                    be.unwrapBox(stack.copy(), false);
                    be.triggerStockCheck();
                    stack.shrink(1);
                    AllSoundEvents.DEPOT_PLOP.playOnServer(level, pos);
                    if (stack.isEmpty())
                        player.setItemInHand(hand, ItemStack.EMPTY);
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.SUCCESS;
            }
            if (be.animationTicks > 0)
                return InteractionResult.SUCCESS;
            if (!level.isClientSide()) {
                player.getInventory().placeItemBackInInventory(be.heldBox.copy());
                AllSoundEvents.playItemPickup(player);
                be.heldBox = ItemStack.EMPTY;
                be.notifyUpdate();
            }
            return InteractionResult.SUCCESS;
        }).consumesAction())
            return InteractionResult.SUCCESS;

        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
        return AllBlockEntities.FLUID_PACKAGER.get();
    }
}
