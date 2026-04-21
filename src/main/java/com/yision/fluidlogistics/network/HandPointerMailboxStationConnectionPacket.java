package com.yision.fluidlogistics.network;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public record HandPointerMailboxStationConnectionPacket(
    List<BlockPos> mailboxPositions,
    BlockPos stationPos
) implements ServerboundPacketPayload {

    private static final int MAX_MAILBOXES = 5;

    public HandPointerMailboxStationConnectionPacket {
        mailboxPositions = List.copyOf(mailboxPositions);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HandPointerMailboxStationConnectionPacket> STREAM_CODEC =
        StreamCodec.of(
            HandPointerMailboxStationConnectionPacket::encode,
            HandPointerMailboxStationConnectionPacket::decode
        );

    private static void encode(RegistryFriendlyByteBuf buf, HandPointerMailboxStationConnectionPacket packet) {
        buf.writeVarInt(packet.mailboxPositions.size());
        for (BlockPos mailboxPos : packet.mailboxPositions) {
            BlockPos.STREAM_CODEC.encode(buf, mailboxPos);
        }
        BlockPos.STREAM_CODEC.encode(buf, packet.stationPos);
    }

    private static HandPointerMailboxStationConnectionPacket decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BlockPos> mailboxPositions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            mailboxPositions.add(BlockPos.STREAM_CODEC.decode(buf));
        }
        BlockPos stationPos = BlockPos.STREAM_CODEC.decode(buf);
        return new HandPointerMailboxStationConnectionPacket(mailboxPositions, stationPos);
    }

    @Override
    public void handle(ServerPlayer player) {
        Level level = player.level();
        if (!level.isLoaded(stationPos)) {
            return;
        }

        BlockEntity stationBE = level.getBlockEntity(stationPos);
        if (!(stationBE instanceof StationBlockEntity station)) {
            return;
        }

        GlobalStation newGlobalStation = station.getStation();
        if (newGlobalStation == null) {
            return;
        }

        LinkedHashSet<BlockPos> uniqueMailboxes = new LinkedHashSet<>(mailboxPositions);
        if (uniqueMailboxes.isEmpty() || uniqueMailboxes.size() > MAX_MAILBOXES) {
            return;
        }

        List<PendingMailboxUpdate> pendingUpdates = new ArrayList<>(uniqueMailboxes.size());
        for (BlockPos mailboxPos : uniqueMailboxes) {
            if (!HandPointerInteractionGuard.canUseHandPointer(player, mailboxPos)) {
                return;
            }

            BlockEntity mailboxBE = level.getBlockEntity(mailboxPos);
            if (!(mailboxBE instanceof PostboxBlockEntity postbox)) {
                return;
            }

            if (isStationAlreadyConnected(postbox, mailboxPos, stationPos)) {
                return;
            }

            PackagePortTarget.TrainStationFrogportTarget target =
                new PackagePortTarget.TrainStationFrogportTarget(stationPos.subtract(mailboxPos));
            Vec3 targetLocation = target.getExactTargetLocation(postbox, level, mailboxPos);
            if (targetLocation == Vec3.ZERO || !targetLocation.closerThan(
                Vec3.atBottomCenterOf(mailboxPos),
                AllConfigs.server().logistics.packagePortRange.get())) {
                return;
            }

            pendingUpdates.add(new PendingMailboxUpdate(mailboxPos, postbox, target, newGlobalStation));
        }

        for (PendingMailboxUpdate update : pendingUpdates) {
            if (update.postbox.target != null) {
                update.postbox.target.deregister(update.postbox, level, update.mailboxPos);
            }

            GlobalStation oldGlobalStation = update.postbox.trackedGlobalStation.get();
            if (oldGlobalStation != null && oldGlobalStation != update.newGlobalStation) {
                oldGlobalStation.connectedPorts.remove(update.mailboxPos);
            }

            update.target.setup(update.postbox, level, update.mailboxPos);
            update.postbox.target = update.target;
            update.target.register(update.postbox, level, update.mailboxPos);
            update.postbox.notifyUpdate();
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_MAILBOX_STATION_CONNECTION;
    }

    private static boolean isStationAlreadyConnected(PostboxBlockEntity postbox, BlockPos mailboxPos, BlockPos stationPos) {
        if (!(postbox.target instanceof PackagePortTarget.TrainStationFrogportTarget stationTarget)) {
            return false;
        }
        return mailboxPos.offset(stationTarget.relativePos).equals(stationPos);
    }

    private record PendingMailboxUpdate(
        BlockPos mailboxPos,
        PostboxBlockEntity postbox,
        PackagePortTarget.TrainStationFrogportTarget target,
        GlobalStation newGlobalStation
    ) {
    }
}
