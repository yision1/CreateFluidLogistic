package com.yision.fluidlogistics.network;

import com.yision.fluidlogistics.network.factoryPanel.FactoryPanelSetResourceRestockSettingPacket;
import com.yision.fluidlogistics.network.factoryPanel.FactoryPanelSetFluidFilterPacket;
import com.yision.fluidlogistics.network.factoryPanel.FactoryPanelSetRequestSelectorPacket;
import com.yision.fluidlogistics.content.fluids.faucet.network.FaucetDripParticlePacket;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.network.MechanicalFluidGunPackets;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperFrogportPlacementRequestPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerPackagerTogglePacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerOpenFilterMenuPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerMailboxStationConnectionPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerLogisticsNetworkPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerFrogportConnectionPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerDisplayLinkConfigurationPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerClearClipboardAddressPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerAuthorizeLogisticsNetworkPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerArmPlacementPacket;
import java.util.Locale;

import com.yision.fluidlogistics.FluidLogistics;

import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.CatnipPacketRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public enum FluidLogisticsPackets implements BasePacketPayload.PacketTypeProvider {
    CLIPBOARD_SET_ADDRESS(ClipboardSetAddressPacket.class, ClipboardSetAddressPacket.STREAM_CODEC),
    COPPER_FROGPORT_PLACEMENT_REQUEST(CopperFrogportPlacementRequestPacket.class, CopperFrogportPlacementRequestPacket.STREAM_CODEC),
    FACTORY_PANEL_SET_FLUID_FILTER(FactoryPanelSetFluidFilterPacket.class, FactoryPanelSetFluidFilterPacket.STREAM_CODEC),
    FACTORY_PANEL_SET_REQUEST_SELECTOR(FactoryPanelSetRequestSelectorPacket.class, FactoryPanelSetRequestSelectorPacket.STREAM_CODEC),
    FACTORY_PANEL_SET_RESOURCE_RESTOCK_SETTING(FactoryPanelSetResourceRestockSettingPacket.class,
            FactoryPanelSetResourceRestockSettingPacket.STREAM_CODEC),
    HAND_POINTER_AUTHORIZE_LOGISTICS_NETWORK(HandPointerAuthorizeLogisticsNetworkPacket.class, HandPointerAuthorizeLogisticsNetworkPacket.STREAM_CODEC),
    HAND_POINTER_ARM_PLACEMENT(HandPointerArmPlacementPacket.class, HandPointerArmPlacementPacket.STREAM_CODEC),
    HAND_POINTER_DISPLAY_LINK_CONFIGURATION(HandPointerDisplayLinkConfigurationPacket.class, HandPointerDisplayLinkConfigurationPacket.STREAM_CODEC),
    HAND_POINTER_FROGPORT_CONNECTION(HandPointerFrogportConnectionPacket.class, HandPointerFrogportConnectionPacket.STREAM_CODEC),
    HAND_POINTER_MAILBOX_STATION_CONNECTION(HandPointerMailboxStationConnectionPacket.class, HandPointerMailboxStationConnectionPacket.STREAM_CODEC),
    HAND_POINTER_LOGISTICS_NETWORK(HandPointerLogisticsNetworkPacket.class, HandPointerLogisticsNetworkPacket.STREAM_CODEC),
    HAND_POINTER_CLEAR_CLIPBOARD_ADDRESS(HandPointerClearClipboardAddressPacket.class, HandPointerClearClipboardAddressPacket.STREAM_CODEC),
    HAND_POINTER_OPEN_FILTER_MENU(HandPointerOpenFilterMenuPacket.class, HandPointerOpenFilterMenuPacket.STREAM_CODEC),
    HAND_POINTER_PACKAGER_TOGGLE(HandPointerPackagerTogglePacket.class, HandPointerPackagerTogglePacket.STREAM_CODEC),
    FAUCET_DRIP_PARTICLE(FaucetDripParticlePacket.class, FaucetDripParticlePacket.STREAM_CODEC),
    MECHANICAL_FLUID_GUN_TARGET(MechanicalFluidGunPackets.TargetPacket.class, MechanicalFluidGunPackets.TargetPacket.STREAM_CODEC),
    MECHANICAL_FLUID_GUN_ITEM_TARGET_SELECTION(MechanicalFluidGunPackets.ItemTargetSelectionPacket.class, MechanicalFluidGunPackets.ItemTargetSelectionPacket.STREAM_CODEC),
    MECHANICAL_FLUID_GUN_SPRAY_PARTICLE(MechanicalFluidGunPackets.SprayParticlePacket.class, MechanicalFluidGunPackets.SprayParticlePacket.STREAM_CODEC);

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
