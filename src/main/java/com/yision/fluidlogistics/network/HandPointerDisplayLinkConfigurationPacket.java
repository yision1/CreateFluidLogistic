package com.yision.fluidlogistics.network;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.yision.fluidlogistics.item.HandPointerItem;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public record HandPointerDisplayLinkConfigurationPacket(
        BlockPos displayLinkPos,
        BlockPos targetPos
) implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, HandPointerDisplayLinkConfigurationPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, HandPointerDisplayLinkConfigurationPacket::displayLinkPos,
            BlockPos.STREAM_CODEC, HandPointerDisplayLinkConfigurationPacket::targetPos,
            HandPointerDisplayLinkConfigurationPacket::new
        );

    public static void send(BlockPos displayLinkPos, BlockPos targetPos) {
        CatnipServices.NETWORK.sendToServer(new HandPointerDisplayLinkConfigurationPacket(displayLinkPos, targetPos));
    }

    @Override
    public void handle(ServerPlayer player) {
        if (!player.mayBuild()) {
            return;
        }

        if (!(player.getMainHandItem().getItem() instanceof HandPointerItem)) {
            return;
        }

        Level level = player.level();
        if (!level.isLoaded(displayLinkPos) || !level.isLoaded(targetPos)) {
            return;
        }

        if (player.distanceToSqr(displayLinkPos.getX() + 0.5D, displayLinkPos.getY() + 0.5D, displayLinkPos.getZ() + 0.5D) > 64.0D) {
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

        BlockEntity be = level.getBlockEntity(displayLinkPos);
        if (!(be instanceof DisplayLinkBlockEntity displayLink)) {
            return;
        }

        displayLink.target(targetPos);
        displayLink.updateGatheredData();
        displayLink.notifyUpdate();
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_DISPLAY_LINK_CONFIGURATION;
    }
}
