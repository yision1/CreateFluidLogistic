package com.yision.fluidlogistics.content.logistics.copperFrogport;

import com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;

import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public record CopperFrogportPlacementRequestPacket(BlockPos pos) implements ClientboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, CopperFrogportPlacementRequestPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, CopperFrogportPlacementRequestPacket::pos,
            CopperFrogportPlacementRequestPacket::new
        );

    @Override
    @OnlyIn(Dist.CLIENT)
    public void handle(LocalPlayer player) {
        PackagePortTargetSelectionHandler.flushSettings(pos);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.COPPER_FROGPORT_PLACEMENT_REQUEST;
    }
}
