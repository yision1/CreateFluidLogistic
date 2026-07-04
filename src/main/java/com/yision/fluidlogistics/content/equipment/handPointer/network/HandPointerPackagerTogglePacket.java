package com.yision.fluidlogistics.content.equipment.handPointer.network;

import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.yision.fluidlogistics.util.PackagerTargetHelper;
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
        if (!HandPointerInteractionGuard.canUseHandPointer(player, pos)) {
            return;
        }

        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!PackagerTargetHelper.isToggleTarget(blockEntity, state)
            || !(blockEntity instanceof IPackagerOverrideData data)) {
            return;
        }

        boolean wasPowered = state.getValue(BlockStateProperties.POWERED);
        BlockState toggledState = state.setValue(BlockStateProperties.POWERED, !wasPowered);
        level.setBlock(pos, toggledState, Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(pos, toggledState.getBlock());

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
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_PACKAGER_TOGGLE;
    }
}
