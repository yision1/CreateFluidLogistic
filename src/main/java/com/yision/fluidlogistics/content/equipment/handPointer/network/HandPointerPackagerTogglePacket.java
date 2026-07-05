package com.yision.fluidlogistics.content.equipment.handPointer.network;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.util.IPackagerOverrideData;
import com.yision.fluidlogistics.util.PackagerTargetHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.network.NetworkEvent.Context;

public class HandPointerPackagerTogglePacket extends SimplePacketBase {

    private final BlockPos pos;

    public HandPointerPackagerTogglePacket(BlockPos pos) {
        this.pos = pos;
    }

    public HandPointerPackagerTogglePacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!HandPointerInteractionGuard.canUseHandPointer(player, pos)) {
                return;
            }

            Level level = player.level();

            BlockState state = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!PackagerTargetHelper.isToggleTarget(blockEntity, state)) {
                return;
            }

            if (!(blockEntity instanceof IPackagerOverrideData data)) {
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
                    BlockState syncedState = toggledState.setValue(BlockStateProperties.POWERED, hasSignal);
                    level.setBlock(pos, syncedState, Block.UPDATE_CLIENTS);
                    level.updateNeighborsAt(pos, syncedState.getBlock());
                }
                fluidlogistics$syncPowerState(blockEntity, hasSignal);
                return;
            }

            fluidlogistics$syncPowerState(blockEntity, !wasPowered);
            data.fluidlogistics$setManualOverrideLocked(true);
            blockEntity.setChanged();
        });
        return true;
    }

    private static void fluidlogistics$syncPowerState(BlockEntity blockEntity, boolean powered) {
        if (blockEntity instanceof PackagerBlockEntity packager) {
            packager.redstonePowered = powered;
            if (powered) {
                packager.activate();
                return;
            }

            packager.setChanged();
            packager.notifyUpdate();
        }
    }
}
