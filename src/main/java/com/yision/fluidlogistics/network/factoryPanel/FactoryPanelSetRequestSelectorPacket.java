package com.yision.fluidlogistics.network.factoryPanel;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public record FactoryPanelSetRequestSelectorPacket(
        FactoryPanelPosition panelPosition,
        InteractionHand hand
) implements ServerboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, FactoryPanelSetRequestSelectorPacket> STREAM_CODEC =
            StreamCodec.of(
                    FactoryPanelSetRequestSelectorPacket::encode,
                    FactoryPanelSetRequestSelectorPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, FactoryPanelSetRequestSelectorPacket packet) {
        FactoryPanelPosition.STREAM_CODEC.encode(buf, packet.panelPosition);
        buf.writeEnum(packet.hand);
    }

    private static FactoryPanelSetRequestSelectorPacket decode(RegistryFriendlyByteBuf buf) {
        FactoryPanelPosition panelPosition = FactoryPanelPosition.STREAM_CODEC.decode(buf);
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        return new FactoryPanelSetRequestSelectorPacket(panelPosition, hand);
    }

    @Override
    public void handle(ServerPlayer player) {
        FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), panelPosition);
        if (behaviour == null || !Create.LOGISTICS.mayInteract(behaviour.network, player)) {
            return;
        }

        ItemStack key = PackageResources.resolveRequestKey(player.getItemInHand(hand)).orElse(ItemStack.EMPTY);
        if (key.isEmpty() || !behaviour.setFilter(key)) {
            return;
        }

        player.level().playSound(null, behaviour.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.BLOCKS, 0.25f, 0.1f);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.FACTORY_PANEL_SET_REQUEST_SELECTOR;
    }
}
