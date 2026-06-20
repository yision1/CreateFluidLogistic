package com.yision.fluidlogistics.item;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankBlock;
import com.yision.fluidlogistics.block.MultiFluidTank.MultiFluidTankBlockEntity;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

public class MultiFluidTankItem extends BlockItem {

    public MultiFluidTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        InteractionResult initialResult = super.place(ctx);
        if (!initialResult.consumesAction()) {
            return initialResult;
        }
        tryMultiPlace(ctx);
        return initialResult;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack,
            BlockState state) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }

        CompoundTag nbt = stack.getTagElement("BlockEntityTag");
        if (nbt != null) {
            nbt.remove("Luminosity");
            nbt.remove("Size");
            nbt.remove("Height");
            nbt.remove("Controller");
            nbt.remove("LastKnownPos");
            clampTankContents(nbt, MultiFluidTankBlockEntity.getCapacityMultiplier());
        }

        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    private void tryMultiPlace(BlockPlaceContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }

        Direction face = ctx.getClickedFace();
        if (!face.getAxis().isVertical()) {
            return;
        }

        ItemStack stack = ctx.getItemInHand();
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockPos placedOnPos = pos.relative(face.getOpposite());
        BlockState placedOnState = world.getBlockState(placedOnPos);

        if (!MultiFluidTankBlock.isTank(placedOnState)) {
            return;
        }

        MultiFluidTankBlockEntity tankAt = ConnectivityHandler.partAt(AllBlockEntities.MULTI_FLUID_TANK.get(), world,
                placedOnPos);
        if (tankAt == null) {
            return;
        }

        MultiFluidTankBlockEntity controllerBE = tankAt.getControllerBE();
        if (controllerBE == null) {
            return;
        }

        int width = controllerBE.getWidth();
        if (width == 1) {
            return;
        }

        int tanksToPlace = 0;
        BlockPos startPos = face == Direction.DOWN ? controllerBE.getBlockPos().below()
                : controllerBE.getBlockPos().above(controllerBE.getHeight());

        if (startPos.getY() != pos.getY()) {
            return;
        }

        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos offsetPos = startPos.offset(xOffset, 0, zOffset);
                BlockState blockState = world.getBlockState(offsetPos);
                if (MultiFluidTankBlock.isTank(blockState)) {
                    continue;
                }
                if (!blockState.canBeReplaced()) {
                    return;
                }
                tanksToPlace++;
            }
        }

        if (!player.isCreative() && stack.getCount() < tanksToPlace) {
            return;
        }

        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos offsetPos = startPos.offset(xOffset, 0, zOffset);
                BlockState blockState = world.getBlockState(offsetPos);
                if (MultiFluidTankBlock.isTank(blockState)) {
                    continue;
                }
                BlockPlaceContext context = BlockPlaceContext.at(ctx, offsetPos, face);
                player.getPersistentData().putBoolean("SilenceTankSound", true);
                super.place(context);
                player.getPersistentData().remove("SilenceTankSound");
            }
        }
    }

    static void clampTankContents(CompoundTag nbt, int maxAmount) {
        if (!nbt.contains("TankContent", Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag fluids = nbt.getCompound("TankContent");
        int remaining = maxAmount;
        for (String key : fluids.getAllKeys()) {
            if (!fluids.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }

            FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluids.getCompound(key));
            if (fluid.isEmpty()) {
                fluids.remove(key);
                continue;
            }

            int amount = Math.min(remaining, fluid.getAmount());
            if (amount <= 0) {
                fluids.remove(key);
                continue;
            }

            fluid.setAmount(amount);
            fluids.put(key, fluid.writeToNBT(new CompoundTag()));
            remaining -= amount;
        }
    }
}
