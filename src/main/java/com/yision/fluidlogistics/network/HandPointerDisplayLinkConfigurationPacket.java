package com.yision.fluidlogistics.network;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent.Context;

public class HandPointerDisplayLinkConfigurationPacket extends SimplePacketBase {
    private final BlockPos displayLinkPos;
    private final BlockPos targetPos;

    public HandPointerDisplayLinkConfigurationPacket(BlockPos displayLinkPos, BlockPos targetPos) {
        this.displayLinkPos = displayLinkPos;
        this.targetPos = targetPos;
    }

    public HandPointerDisplayLinkConfigurationPacket(FriendlyByteBuf buffer) {
        this.displayLinkPos = buffer.readBlockPos();
        this.targetPos = buffer.readBlockPos();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(displayLinkPos);
        buffer.writeBlockPos(targetPos);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!HandPointerInteractionGuard.canUseHandPointer(player, displayLinkPos)) {
                return;
            }

            Level level = player.level();
            if (!level.isLoaded(targetPos)) {
                return;
            }
            if (!AllBlocks.DISPLAY_LINK.has(level.getBlockState(displayLinkPos))) {
                return;
            }
            if (DisplayTarget.get(level, targetPos) == null) {
                return;
            }
            if (!targetPos.closerThan(displayLinkPos, AllConfigs.server().logistics.displayLinkRange.get())) {
                return;
            }

            BlockEntity blockEntity = level.getBlockEntity(displayLinkPos);
            if (!(blockEntity instanceof DisplayLinkBlockEntity displayLink)) {
                return;
            }

            displayLink.target(targetPos);
            displayLink.updateGatheredData();
            displayLink.notifyUpdate();
        });
        return true;
    }
}
