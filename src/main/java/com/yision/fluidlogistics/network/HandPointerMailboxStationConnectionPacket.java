package com.yision.fluidlogistics.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent.Context;

public class HandPointerMailboxStationConnectionPacket extends SimplePacketBase {

    private static final int MAX_MAILBOXES = 5;

    private final List<BlockPos> mailboxPositions;
    private final BlockPos stationPos;

    public HandPointerMailboxStationConnectionPacket(Collection<BlockPos> mailboxPositions, BlockPos stationPos) {
        this.mailboxPositions = List.copyOf(mailboxPositions);
        this.stationPos = stationPos;
    }

    public HandPointerMailboxStationConnectionPacket(BlockPos mailboxPos, BlockPos stationPos) {
        this(List.of(mailboxPos), stationPos);
    }

    public HandPointerMailboxStationConnectionPacket(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buffer.readBlockPos());
        }
        this.mailboxPositions = positions;
        this.stationPos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(mailboxPositions.size());
        for (BlockPos mailboxPos : mailboxPositions) {
            buffer.writeBlockPos(mailboxPos);
        }
        buffer.writeBlockPos(stationPos);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.mayBuild()) {
                return;
            }

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
        });
        return true;
    }

    private static boolean isStationAlreadyConnected(PostboxBlockEntity postbox, BlockPos mailboxPos, BlockPos stationPos) {
        if (!(postbox.target instanceof PackagePortTarget.TrainStationFrogportTarget stationTarget)) {
            return false;
        }
        return mailboxPos.offset(stationTarget.relativePos).equals(stationPos);
    }

    private record PendingMailboxUpdate(BlockPos mailboxPos, PostboxBlockEntity postbox,
                                        PackagePortTarget.TrainStationFrogportTarget target,
                                        GlobalStation newGlobalStation) {
    }
}
