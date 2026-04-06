package com.yision.fluidlogistics.network;

import java.util.Locale;

import com.yision.fluidlogistics.FluidLogistics;

import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.CatnipPacketRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public enum FluidLogisticsPackets implements BasePacketPayload.PacketTypeProvider {
    CLIPBOARD_SET_ADDRESS(ClipboardSetAddressPacket.class, ClipboardSetAddressPacket.STREAM_CODEC),
    FACTORY_PANEL_SET_FLUID_FILTER(FactoryPanelSetFluidFilterPacket.class, FactoryPanelSetFluidFilterPacket.STREAM_CODEC),
    FACTORY_PANEL_SET_FLUID_ADDITIONAL_STOCK(FactoryPanelSetFluidAdditionalStockPacket.class, FactoryPanelSetFluidAdditionalStockPacket.STREAM_CODEC),
    FACTORY_PANEL_SET_FLUID_PROMISE_LIMIT(FactoryPanelSetFluidPromiseLimitPacket.class, FactoryPanelSetFluidPromiseLimitPacket.STREAM_CODEC),
    FACTORY_PANEL_SET_FLUID_RESTOCK_THRESHOLD(FactoryPanelSetFluidRestockThresholdPacket.class, FactoryPanelSetFluidRestockThresholdPacket.STREAM_CODEC),
    HAND_POINTER_FROGPORT_CONNECTION(HandPointerFrogportConnectionPacket.class, HandPointerFrogportConnectionPacket.STREAM_CODEC),
    HAND_POINTER_MAILBOX_STATION_CONNECTION(HandPointerMailboxStationConnectionPacket.class, HandPointerMailboxStationConnectionPacket.STREAM_CODEC),
    HAND_POINTER_LOGISTICS_NETWORK(HandPointerLogisticsNetworkPacket.class, HandPointerLogisticsNetworkPacket.STREAM_CODEC),
    HAND_POINTER_CLEAR_CLIPBOARD_ADDRESS(HandPointerClearClipboardAddressPacket.class, HandPointerClearClipboardAddressPacket.STREAM_CODEC),
    HAND_POINTER_PACKAGER_TOGGLE(HandPointerPackagerTogglePacket.class, HandPointerPackagerTogglePacket.STREAM_CODEC),
    HAND_POINTER_MODE_ENTERED(HandPointerModeEnteredPacket.class, HandPointerModeEnteredPacket.STREAM_CODEC),
    PORTABLE_STOCK_TICKER_OPEN(PortableStockTickerOpenPacket.class, PortableStockTickerOpenPacket.STREAM_CODEC),
    PORTABLE_STOCK_TICKER_STOCK_REQUEST(PortableStockTickerStockRequestPacket.class, PortableStockTickerStockRequestPacket.STREAM_CODEC),
    PORTABLE_STOCK_TICKER_STOCK_RESPONSE(PortableStockTickerStockResponsePacket.class, PortableStockTickerStockResponsePacket.STREAM_CODEC),
    PORTABLE_STOCK_TICKER_ORDER_REQUEST(PortableStockTickerOrderRequestPacket.class, PortableStockTickerOrderRequestPacket.STREAM_CODEC),
    PORTABLE_STOCK_TICKER_SAVE_ADDRESS(PortableStockTickerSaveAddressPacket.class, PortableStockTickerSaveAddressPacket.STREAM_CODEC),
    PORTABLE_STOCK_TICKER_HIDDEN_CATEGORIES(PortableStockTickerHiddenCategoriesPacket.class, PortableStockTickerHiddenCategoriesPacket.STREAM_CODEC);

    private final CatnipPacketRegistry.PacketType<?> type;

    <T extends BasePacketPayload> FluidLogisticsPackets(Class<T> clazz, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        String name = this.name().toLowerCase(Locale.ROOT);
        this.type = new CatnipPacketRegistry.PacketType<>(
                new CustomPacketPayload.Type<>(FluidLogistics.asResource(name)),
                clazz, codec
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> getType() {
        return (CustomPacketPayload.Type<T>) this.type.type();
    }

    public static void register() {
        CatnipPacketRegistry packetRegistry = new CatnipPacketRegistry(FluidLogistics.MODID, "1.0");
        for (FluidLogisticsPackets packet : FluidLogisticsPackets.values()) {
            packetRegistry.registerPacket(packet.type);
        }
        packetRegistry.registerAllPackets();
    }
}
