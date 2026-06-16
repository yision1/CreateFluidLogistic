package com.yision.fluidlogistics.network;

import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.yision.fluidlogistics.item.HandPointerItem;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public record HandPointerAuthorizeLogisticsNetworkPacket(BlockPos sourcePos, FactoryPanelBlock.PanelSlot panelSlot)
    implements ServerboundPacketPayload {

    private static final int STATUS_SUCCESS_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;
    private static final int STATUS_NEUTRAL_COLOR = 0xA5A5A5;

    public static final StreamCodec<RegistryFriendlyByteBuf, HandPointerAuthorizeLogisticsNetworkPacket> STREAM_CODEC =
        StreamCodec.of(
            HandPointerAuthorizeLogisticsNetworkPacket::encode,
            HandPointerAuthorizeLogisticsNetworkPacket::decode
        );

    private static void encode(RegistryFriendlyByteBuf buf, HandPointerAuthorizeLogisticsNetworkPacket packet) {
        BlockPos.STREAM_CODEC.encode(buf, packet.sourcePos);
        FactoryPanelBlock.PanelSlot.STREAM_CODEC.encode(buf, packet.panelSlot);
    }

    private static HandPointerAuthorizeLogisticsNetworkPacket decode(RegistryFriendlyByteBuf buf) {
        return new HandPointerAuthorizeLogisticsNetworkPacket(
            BlockPos.STREAM_CODEC.decode(buf),
            FactoryPanelBlock.PanelSlot.STREAM_CODEC.decode(buf)
        );
    }

    public static void send(BlockPos pos, FactoryPanelBlock.PanelSlot slot) {
        CatnipServices.NETWORK.sendToServer(new HandPointerAuthorizeLogisticsNetworkPacket(pos, slot));
    }

    @Override
    public void handle(ServerPlayer player) {
        Level level = player.level();
        if (!level.isLoaded(sourcePos)) {
            return;
        }

        if (player.distanceToSqr(sourcePos.getX() + 0.5D, sourcePos.getY() + 0.5D, sourcePos.getZ() + 0.5D) > 64.0D) {
            return;
        }

        ItemStack heldStack = player.getMainHandItem();
        if (!(heldStack.getItem() instanceof HandPointerItem)) {
            return;
        }

        UUID networkId = resolveNetworkId(level.getBlockEntity(sourcePos));
        if (networkId == null) {
            return;
        }

        if (!Create.LOGISTICS.mayAdministrate(networkId, player)) {
            displayStatus(player, "logistically_linked.protected", STATUS_INVALID_COLOR);
            return;
        }

        if (!HandPointerItem.authorizeNetwork(heldStack, networkId)) {
            displayStatus(player, "create.fluidlogistics.hand_pointer.authorization_exists", STATUS_NEUTRAL_COLOR);
            return;
        }

        displayStatus(player, "create.fluidlogistics.hand_pointer.authorization_added", STATUS_SUCCESS_COLOR);
    }

    private UUID resolveNetworkId(BlockEntity be) {
        if (be instanceof PackagerLinkBlockEntity link) {
            return link.behaviour.freqId;
        }
        if (be instanceof StockTickerBlockEntity ticker) {
            return ticker.behaviour.freqId;
        }
        if (be instanceof RedstoneRequesterBlockEntity requester) {
            return requester.behaviour.freqId;
        }
        if (be instanceof FactoryPanelBlockEntity panel) {
            if (panel.panels == null || panel.panels.isEmpty()) {
                return null;
            }

            FactoryPanelBehaviour fp = panel.panels.get(panelSlot);
            if (fp == null || !fp.isActive()) {
                fp = panel.panels.values().stream().findFirst().orElse(null);
            }
            return fp == null ? null : fp.network;
        }
        return null;
    }

    private static void displayStatus(ServerPlayer player, String translationKey, int color) {
        player.displayClientMessage(
            Component.translatable(translationKey)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))),
            true
        );
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_AUTHORIZE_LOGISTICS_NETWORK;
    }
}
