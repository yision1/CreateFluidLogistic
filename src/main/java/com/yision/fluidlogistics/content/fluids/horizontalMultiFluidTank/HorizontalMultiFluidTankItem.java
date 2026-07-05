package com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankBlock;
import com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidTank.MultiFluidTankItem;
import com.yision.fluidlogistics.registry.AllBlockEntities;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class HorizontalMultiFluidTankItem extends BlockItem {

    public HorizontalMultiFluidTankItem(Block block, Properties properties) {
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
            MultiFluidTankItem.clampTankContents(nbt, HorizontalMultiFluidTankBlockEntity.getCapacityMultiplier());
        }

        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    private void tryMultiPlace(BlockPlaceContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }

        Direction face = ctx.getClickedFace();
        if (!face.getAxis().isHorizontal()) {
            return;
        }

        ItemStack stack = ctx.getItemInHand();
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockPos placedOnPos = pos.relative(face.getOpposite());
        BlockState placedOnState = world.getBlockState(placedOnPos);

        if (!HorizontalMultiFluidTankBlock.isVessel(placedOnState) || SymmetryWandItem.presentInHotbar(player)) {
            return;
        }

        HorizontalMultiFluidTankBlockEntity tankAt = ConnectivityHandler.partAt(
                AllBlockEntities.HORIZONTAL_MULTI_FLUID_TANK.get(), world, placedOnPos);
        if (tankAt == null) {
            return;
        }

        HorizontalMultiFluidTankBlockEntity controllerBE = tankAt.getControllerBE();
        if (controllerBE == null) {
            return;
        }

        int width = controllerBE.getWidth();
        if (width == 1) {
            return;
        }

        Axis vesselAxis = placedOnState.getOptionalValue(HorizontalMultiFluidTankBlock.AXIS).orElse(null);
        if (vesselAxis == null || face.getAxis() != vesselAxis) {
            return;
        }

        Direction vesselFacing = Direction.fromAxisAndDirection(vesselAxis, Direction.AxisDirection.POSITIVE);
        BlockPos startPos = face == vesselFacing.getOpposite()
                ? controllerBE.getBlockPos().relative(vesselFacing.getOpposite())
                : controllerBE.getBlockPos().relative(vesselFacing, controllerBE.getHeight());

        if (VecHelper.getCoordinate(startPos, vesselAxis) != VecHelper.getCoordinate(pos, vesselAxis)) {
            return;
        }

        int tanksToPlace = 0;
        for (int xOffset = 0; xOffset < width; xOffset++) {
            for (int zOffset = 0; zOffset < width; zOffset++) {
                BlockPos offsetPos = vesselAxis == Axis.X ? startPos.offset(0, xOffset, zOffset)
                        : startPos.offset(xOffset, zOffset, 0);
                BlockState blockState = world.getBlockState(offsetPos);
                if (HorizontalMultiFluidTankBlock.isVessel(blockState)) {
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
                BlockPos offsetPos = vesselAxis == Axis.X ? startPos.offset(0, xOffset, zOffset)
                        : startPos.offset(xOffset, zOffset, 0);
                BlockState blockState = world.getBlockState(offsetPos);
                if (HorizontalMultiFluidTankBlock.isVessel(blockState)) {
                    continue;
                }
                BlockPlaceContext context = BlockPlaceContext.at(ctx, offsetPos, face);
                player.getPersistentData().putBoolean("SilenceVesselSound", true);
                super.place(context);
                player.getPersistentData().remove("SilenceVesselSound");
            }
        }
    }
}
