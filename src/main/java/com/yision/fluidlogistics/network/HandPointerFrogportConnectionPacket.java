package com.yision.fluidlogistics.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent.Context;

public class HandPointerFrogportConnectionPacket extends SimplePacketBase {

    private static final int MAX_FROGPORTS = 5;

    private final List<BlockPos> frogportPositions;
    private final BlockPos chainConveyorPos;
    private final float chainPosition;
    private final BlockPos connection;

    public HandPointerFrogportConnectionPacket(Collection<BlockPos> frogportPositions, BlockPos chainConveyorPos, float chainPosition,
        BlockPos connection) {
        this.frogportPositions = List.copyOf(frogportPositions);
        this.chainConveyorPos = chainConveyorPos;
        this.chainPosition = chainPosition;
        this.connection = connection;
    }

    public HandPointerFrogportConnectionPacket(BlockPos frogportPos, BlockPos chainConveyorPos, float chainPosition,
        BlockPos connection) {
        this(List.of(frogportPos), chainConveyorPos, chainPosition, connection);
    }

    public HandPointerFrogportConnectionPacket(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buffer.readBlockPos());
        }
        this.frogportPositions = positions;
        this.chainConveyorPos = buffer.readBlockPos();
        this.chainPosition = buffer.readFloat();
        this.connection = buffer.readBoolean() ? buffer.readBlockPos() : null;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(frogportPositions.size());
        for (BlockPos frogportPos : frogportPositions) {
            buffer.writeBlockPos(frogportPos);
        }
        buffer.writeBlockPos(chainConveyorPos);
        buffer.writeFloat(chainPosition);
        buffer.writeBoolean(connection != null);
        if (connection != null) {
            buffer.writeBlockPos(connection);
        }
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.mayBuild()) {
                return;
            }

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
                    new ChainConveyorFrogportTarget(chainConveyorPos.subtract(frogportPos), chainPosition, connection);
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
        });
        return true;
    }

    private record PendingFrogportUpdate(BlockPos frogportPos, PackagePortBlockEntity frogport,
                                         ChainConveyorFrogportTarget target) {
    }
}
