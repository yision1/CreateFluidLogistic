package com.yision.fluidlogistics.network;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlockEntity;
import com.yision.fluidlogistics.util.IPackagerOverrideData;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public record HandPointerPackagerTogglePacket(BlockPos pos) implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, HandPointerPackagerTogglePacket> STREAM_CODEC =
        StreamCodec.of(
            HandPointerPackagerTogglePacket::encode,
            HandPointerPackagerTogglePacket::decode
        );

    private static void encode(RegistryFriendlyByteBuf buf, HandPointerPackagerTogglePacket packet) {
        BlockPos.STREAM_CODEC.encode(buf, packet.pos);
    }

    private static HandPointerPackagerTogglePacket decode(RegistryFriendlyByteBuf buf) {
        return new HandPointerPackagerTogglePacket(BlockPos.STREAM_CODEC.decode(buf));
    }

    public static void send(BlockPos pos) {
        CatnipServices.NETWORK.sendToServer(new HandPointerPackagerTogglePacket(pos));
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!player.mayBuild()) {
            return;
        }

        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return;
        }

        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        boolean isCreatePackager = AllBlocks.PACKAGER.has(state) || AllBlocks.REPACKAGER.has(state);
        boolean isFluidPackager = com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state);
        if ((!isCreatePackager && !isFluidPackager) || !state.hasProperty(BlockStateProperties.POWERED)) {
            return;
        }

        boolean wasPowered = state.getValue(BlockStateProperties.POWERED);
        BlockState toggledState = state.setValue(BlockStateProperties.POWERED, !wasPowered);
        level.setBlock(pos, toggledState, Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(pos, toggledState.getBlock());

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IPackagerOverrideData data)) {
            return;
        }

        if (data.fluidlogistics$isManualOverrideLocked()) {
            data.fluidlogistics$setManualOverrideLocked(false);
            boolean hasSignal = level.hasNeighborSignal(pos);
            if (toggledState.getValue(BlockStateProperties.POWERED) != hasSignal) {
                BlockState synced = toggledState.setValue(BlockStateProperties.POWERED, hasSignal);
                level.setBlock(pos, synced, Block.UPDATE_CLIENTS);
                level.updateNeighborsAt(pos, synced.getBlock());
                syncBlockEntityPowerState(blockEntity, hasSignal);
            } else {
                syncBlockEntityPowerState(blockEntity, hasSignal);
            }
        } else {
            syncBlockEntityPowerState(blockEntity, !wasPowered);
            data.fluidlogistics$setManualOverrideLocked(true);
            blockEntity.setChanged();
        }
    }

    private static void syncBlockEntityPowerState(BlockEntity blockEntity, boolean powered) {
        if (blockEntity instanceof PackagerBlockEntity packager) {
            packager.redstonePowered = powered;
            if (powered) {
                packager.activate();
            } else {
                packager.setChanged();
                packager.notifyUpdate();
            }
            return;
        }
        if (blockEntity instanceof FluidPackagerBlockEntity fluidPackager) {
            fluidPackager.redstonePowered = powered;
            if (powered) {
                fluidPackager.activate();
            } else {
                fluidPackager.setChanged();
                fluidPackager.notifyUpdate();
            }
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_PACKAGER_TOGGLE;
    }
}
