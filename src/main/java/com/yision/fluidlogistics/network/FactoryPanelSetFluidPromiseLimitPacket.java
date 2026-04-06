package com.yision.fluidlogistics.network;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidPromiseLimit;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record FactoryPanelSetFluidPromiseLimitPacket(
        FactoryPanelPosition panelPosition,
        int limit
) implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, FactoryPanelSetFluidPromiseLimitPacket> STREAM_CODEC =
            StreamCodec.of(
                    FactoryPanelSetFluidPromiseLimitPacket::encode,
                    FactoryPanelSetFluidPromiseLimitPacket::decode
            );

    private static void encode(RegistryFriendlyByteBuf buf, FactoryPanelSetFluidPromiseLimitPacket packet) {
        FactoryPanelPosition.STREAM_CODEC.encode(buf, packet.panelPosition);
        buf.writeVarInt(packet.limit);
    }

    private static FactoryPanelSetFluidPromiseLimitPacket decode(RegistryFriendlyByteBuf buf) {
        FactoryPanelPosition panelPosition = FactoryPanelPosition.STREAM_CODEC.decode(buf);
        int limit = buf.readVarInt();
        return new FactoryPanelSetFluidPromiseLimitPacket(panelPosition, limit);
    }

    @Override
    public void handle(ServerPlayer player) {
        FluidGaugeHelper.applyPanelSetting(player, panelPosition, IFluidPromiseLimit.class,
            promiseLimitData -> promiseLimitData.fluidlogistics$setPromiseLimit(limit));
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.FACTORY_PANEL_SET_FLUID_PROMISE_LIMIT;
    }
}
