package com.yision.fluidlogistics.network;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidRestockThreshold;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record FactoryPanelSetFluidRestockThresholdPacket(
        FactoryPanelPosition panelPosition,
        int threshold
) implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, FactoryPanelSetFluidRestockThresholdPacket> STREAM_CODEC =
            StreamCodec.of(
                    FactoryPanelSetFluidRestockThresholdPacket::encode,
                    FactoryPanelSetFluidRestockThresholdPacket::decode
            );

    private static void encode(RegistryFriendlyByteBuf buf, FactoryPanelSetFluidRestockThresholdPacket packet) {
        FactoryPanelPosition.STREAM_CODEC.encode(buf, packet.panelPosition);
        buf.writeVarInt(packet.threshold);
    }

    private static FactoryPanelSetFluidRestockThresholdPacket decode(RegistryFriendlyByteBuf buf) {
        FactoryPanelPosition panelPosition = FactoryPanelPosition.STREAM_CODEC.decode(buf);
        int threshold = buf.readVarInt();
        return new FactoryPanelSetFluidRestockThresholdPacket(panelPosition, threshold);
    }

    @Override
    public void handle(ServerPlayer player) {
        FluidGaugeHelper.applyPanelSetting(player, panelPosition, IFluidRestockThreshold.class,
            thresholdData -> thresholdData.fluidlogistics$setRestockThreshold(threshold));
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.FACTORY_PANEL_SET_FLUID_RESTOCK_THRESHOLD;
    }
}
