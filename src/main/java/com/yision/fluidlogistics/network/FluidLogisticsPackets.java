package com.yision.fluidlogistics.network;

import com.yision.fluidlogistics.FluidLogistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import net.minecraftforge.network.simple.SimpleChannel;

public class FluidLogisticsPackets {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(FluidLogistics.asResource("main"))
        .networkProtocolVersion(() -> PROTOCOL_VERSION)
        .clientAcceptedVersions(PROTOCOL_VERSION::equals)
        .serverAcceptedVersions(PROTOCOL_VERSION::equals)
        .simpleChannel();

    public static void register() {
        int index = 0;
        CHANNEL.messageBuilder(FactoryPanelSetFluidFilterPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(FactoryPanelSetFluidFilterPacket::write)
            .decoder(FactoryPanelSetFluidFilterPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(FactoryPanelSetFluidRestockThresholdPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(FactoryPanelSetFluidRestockThresholdPacket::write)
            .decoder(FactoryPanelSetFluidRestockThresholdPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(FactoryPanelSetFluidPromiseLimitPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(FactoryPanelSetFluidPromiseLimitPacket::write)
            .decoder(FactoryPanelSetFluidPromiseLimitPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(FactoryPanelSetFluidAdditionalStockPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(FactoryPanelSetFluidAdditionalStockPacket::write)
            .decoder(FactoryPanelSetFluidAdditionalStockPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(HandPointerFrogportConnectionPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(HandPointerFrogportConnectionPacket::write)
            .decoder(HandPointerFrogportConnectionPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(HandPointerMailboxStationConnectionPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(HandPointerMailboxStationConnectionPacket::write)
            .decoder(HandPointerMailboxStationConnectionPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(HandPointerPackagerTogglePacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(HandPointerPackagerTogglePacket::write)
            .decoder(HandPointerPackagerTogglePacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(HandPointerClearClipboardAddressPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(HandPointerClearClipboardAddressPacket::write)
            .decoder(HandPointerClearClipboardAddressPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(HandPointerArmPlacementPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(HandPointerArmPlacementPacket::write)
            .decoder(HandPointerArmPlacementPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(HandPointerDisplayLinkConfigurationPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(HandPointerDisplayLinkConfigurationPacket::write)
            .decoder(HandPointerDisplayLinkConfigurationPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(HandPointerOpenFilterMenuPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(HandPointerOpenFilterMenuPacket::write)
            .decoder(HandPointerOpenFilterMenuPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(ClipboardSetAddressPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ClipboardSetAddressPacket::write)
            .decoder(ClipboardSetAddressPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(SmartFaucetDripParticlePacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SmartFaucetDripParticlePacket::write)
            .decoder(SmartFaucetDripParticlePacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(MechanicalFluidGunPackets.TargetPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(MechanicalFluidGunPackets.TargetPacket::write)
            .decoder(MechanicalFluidGunPackets.TargetPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(MechanicalFluidGunPackets.ItemTargetSelectionPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(MechanicalFluidGunPackets.ItemTargetSelectionPacket::write)
            .decoder(MechanicalFluidGunPackets.ItemTargetSelectionPacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();

        CHANNEL.messageBuilder(MechanicalFluidGunPackets.SprayParticlePacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(MechanicalFluidGunPackets.SprayParticlePacket::write)
            .decoder(MechanicalFluidGunPackets.SprayParticlePacket::new)
            .consumerNetworkThread((packet, contextSupplier) -> {
                if (packet.handle(contextSupplier.get())) {
                    contextSupplier.get().setPacketHandled(true);
                }
            })
            .add();
    }

    public static SimpleChannel getChannel() {
        return CHANNEL;
    }

    public static void sendToNear(Level level, BlockPos pos, int range, Object message) {
        CHANNEL.send(PacketDistributor.NEAR.with(TargetPoint.p(pos.getX(), pos.getY(), pos.getZ(), range, level.dimension())),
            message);
    }
}
