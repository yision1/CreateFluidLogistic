package com.yision.fluidlogistics.network;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidAdditionalStock;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record FactoryPanelSetFluidAdditionalStockPacket(
        FactoryPanelPosition panelPosition,
        int additionalStock
) implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, FactoryPanelSetFluidAdditionalStockPacket> STREAM_CODEC =
            StreamCodec.of(
                    FactoryPanelSetFluidAdditionalStockPacket::encode,
                    FactoryPanelSetFluidAdditionalStockPacket::decode
            );

    private static void encode(RegistryFriendlyByteBuf buf, FactoryPanelSetFluidAdditionalStockPacket packet) {
        FactoryPanelPosition.STREAM_CODEC.encode(buf, packet.panelPosition);
        buf.writeVarInt(packet.additionalStock);
    }

    private static FactoryPanelSetFluidAdditionalStockPacket decode(RegistryFriendlyByteBuf buf) {
        FactoryPanelPosition panelPosition = FactoryPanelPosition.STREAM_CODEC.decode(buf);
        int additionalStock = buf.readVarInt();
        return new FactoryPanelSetFluidAdditionalStockPacket(panelPosition, additionalStock);
    }

    @Override
    public void handle(ServerPlayer player) {
        FluidGaugeHelper.applyPanelSetting(player, panelPosition, IFluidAdditionalStock.class,
            additionalStockData -> additionalStockData.fluidlogistics$setAdditionalStock(additionalStock));
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.FACTORY_PANEL_SET_FLUID_ADDITIONAL_STOCK;
    }
}
