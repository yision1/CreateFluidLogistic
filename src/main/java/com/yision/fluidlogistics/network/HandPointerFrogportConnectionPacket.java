package com.yision.fluidlogistics.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public record HandPointerFrogportConnectionPacket(
    List<BlockPos> frogportPositions,
    BlockPos chainConveyorPos,
    float chainPosition,
    BlockPos connection
) implements ServerboundPacketPayload {

    private static final int MAX_FROGPORTS = 5;

    public HandPointerFrogportConnectionPacket {
        frogportPositions = List.copyOf(frogportPositions);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HandPointerFrogportConnectionPacket> STREAM_CODEC =
        StreamCodec.of(
            HandPointerFrogportConnectionPacket::encode,
            HandPointerFrogportConnectionPacket::decode
        );

    public static void send(Collection<BlockPos> frogportPositions, BlockPos chainConveyorPos, float chainPosition, BlockPos connection) {
        CatnipServices.NETWORK.sendToServer(
            new HandPointerFrogportConnectionPacket(new ArrayList<>(frogportPositions), chainConveyorPos, chainPosition, connection));
    }

    private static void encode(RegistryFriendlyByteBuf buf, HandPointerFrogportConnectionPacket packet) {
        buf.writeVarInt(packet.frogportPositions.size());
        for (BlockPos frogportPos : packet.frogportPositions) {
            BlockPos.STREAM_CODEC.encode(buf, frogportPos);
        }
        BlockPos.STREAM_CODEC.encode(buf, packet.chainConveyorPos);
        buf.writeFloat(packet.chainPosition);
        if (packet.connection != null) {
            buf.writeBoolean(true);
            BlockPos.STREAM_CODEC.encode(buf, packet.connection);
        } else {
            buf.writeBoolean(false);
        }
    }

    private static HandPointerFrogportConnectionPacket decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BlockPos> frogportPositions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            frogportPositions.add(BlockPos.STREAM_CODEC.decode(buf));
        }

        BlockPos chainConveyorPos = BlockPos.STREAM_CODEC.decode(buf);
        float chainPosition = buf.readFloat();
        BlockPos connection = buf.readBoolean() ? BlockPos.STREAM_CODEC.decode(buf) : null;
        return new HandPointerFrogportConnectionPacket(frogportPositions, chainConveyorPos, chainPosition, connection);
    }

    @Override
    public void handle(ServerPlayer player) {
        Level level = player.level();
        if (!level.isLoaded(chainConveyorPos)) {
            return;
        }

        BlockEntity chainBE = level.getBlockEntity(chainConveyorPos);
        if (!(chainBE instanceof ChainConveyorBlockEntity)) {
            return;
        }

        LinkedHashSet<BlockPos> uniqueFrogports = new LinkedHashSet<>(frogportPositions);
        if (uniqueFrogports.isEmpty() || uniqueFrogports.size() > MAX_FROGPORTS) {
            return;
        }

        List<PendingFrogportUpdate> pendingUpdates = new ArrayList<>(uniqueFrogports.size());
        for (BlockPos frogportPos : uniqueFrogports) {
            if (!HandPointerInteractionGuard.canUseHandPointer(player, frogportPos)) {
                return;
            }

            BlockEntity frogportBE = level.getBlockEntity(frogportPos);
            if (!(frogportBE instanceof PackagePortBlockEntity frogport)) {
                return;
            }

            ChainConveyorFrogportTarget newTarget =
                new ChainConveyorFrogportTarget(chainConveyorPos.subtract(frogportPos), chainPosition, connection, false);
            if (!newTarget.canSupport(frogport)) {
                return;
            }

            Vec3 targetLocation = newTarget.getExactTargetLocation(frogport, level, frogportPos);
            if (targetLocation == Vec3.ZERO || !targetLocation.closerThan(
                Vec3.atBottomCenterOf(frogportPos),
                AllConfigs.server().logistics.packagePortRange.get() + 2)) {
                return;
            }

            pendingUpdates.add(new PendingFrogportUpdate(frogportPos, frogport, newTarget));
        }

        for (PendingFrogportUpdate update : pendingUpdates) {
            if (update.frogport.target != null) {
                update.frogport.target.deregister(update.frogport, level, update.frogportPos);
            }

            update.target.setup(update.frogport, level, update.frogportPos);
            update.frogport.target = update.target;
            update.target.register(update.frogport, level, update.frogportPos);
            update.frogport.notifyUpdate();
            update.frogport.filterChanged();
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_FROGPORT_CONNECTION;
    }

    private record PendingFrogportUpdate(
        BlockPos frogportPos,
        PackagePortBlockEntity frogport,
        ChainConveyorFrogportTarget target
    ) {
    }
}
