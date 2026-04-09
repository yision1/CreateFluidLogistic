package com.yision.fluidlogistics.network;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.yision.fluidlogistics.item.HandPointerItem;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public record HandPointerLogisticsNetworkPacket(
    Action action,
    BlockPos sourcePos,
    UUID targetNetworkId,
    FactoryPanelBlock.PanelSlot panelSlot
) implements ServerboundPacketPayload {

    public enum Action {
        CONNECT,
        DISCONNECT
    }
    
    private static final int STATUS_INVALID_COLOR = 0xFF6171;

    public static final StreamCodec<RegistryFriendlyByteBuf, HandPointerLogisticsNetworkPacket> STREAM_CODEC =
        StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.VAR_INT.encode(buf, packet.action.ordinal());
                BlockPos.STREAM_CODEC.encode(buf, packet.sourcePos);
                if (packet.targetNetworkId == null) {
                    buf.writeBoolean(false);
                } else {
                    buf.writeBoolean(true);
                    UUIDUtil.STREAM_CODEC.encode(buf, packet.targetNetworkId);
                }
                FactoryPanelBlock.PanelSlot.STREAM_CODEC.encode(buf, packet.panelSlot);
            },
            buf -> {
                int actionOrdinal = ByteBufCodecs.VAR_INT.decode(buf);
                Action action = Action.values()[actionOrdinal];
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                UUID target = buf.readBoolean() ? UUIDUtil.STREAM_CODEC.decode(buf) : null;
                FactoryPanelBlock.PanelSlot slot = FactoryPanelBlock.PanelSlot.STREAM_CODEC.decode(buf);
                return new HandPointerLogisticsNetworkPacket(action, pos, target, slot);
            }
        );

    @Override
    public void handle(ServerPlayer player) {
        Level level = player.level();
        if (!level.isLoaded(sourcePos)) {
            return;
        }

        BlockEntity be = level.getBlockEntity(sourcePos);
        if (be instanceof PackagerLinkBlockEntity link) {
            handlePackagerLink(player, level, sourcePos, link);
        } else if (be instanceof FactoryPanelBlockEntity panel) {
            handleFactoryPanel(player, level, sourcePos, panel);
        } else if (be instanceof StockTickerBlockEntity ticker) {
            handleStockTicker(player, level, sourcePos, ticker);
        } else if (be instanceof RedstoneRequesterBlockEntity requester) {
            handleRedstoneRequester(player, level, sourcePos, requester);
        }
    }

    private void handlePackagerLink(ServerPlayer player, Level level, BlockPos pos, PackagerLinkBlockEntity link) {
        LogisticallyLinkedBehaviour behaviour = link.behaviour;
        switch (action) {
            case CONNECT -> {
                UUID oldNetwork = behaviour.freqId;
                if (!mayMoveBetweenNetworksOrWarn(player, oldNetwork, targetNetworkId)) {
                    return;
                }

                moveGlobalBehaviourToNetwork(level, pos, behaviour, targetNetworkId, null);

                invalidateSummary(oldNetwork);
                invalidateSummary(targetNetworkId);
                markDirtyAndNotify(link);
            }
            case DISCONNECT -> {
                if (!mayAdministrateOrWarn(player, behaviour.freqId)) {
                    return;
                }

                UUID oldNetwork = behaviour.freqId;

                UUID newNetwork = disconnectedNetworkId(player, pos);
                moveGlobalBehaviourToNetwork(level, pos, behaviour, newNetwork, player.getUUID());

                invalidateSummary(oldNetwork);
                invalidateSummary(newNetwork);
                markDirtyAndNotify(link);
            }
        }
    }

    private void handleFactoryPanel(ServerPlayer player, Level level, BlockPos pos, FactoryPanelBlockEntity panel) {
        purgeLegacyRegisteredPosFromAllNetworks(level, pos);

        if (panel.panels == null || panel.panels.isEmpty()) {
            return;
        }

        FactoryPanelBehaviour fp = panel.panels.get(panelSlot);
        if (fp == null || !fp.isActive()) {
            fp = panel.panels.values().stream().findFirst().orElse(null);
            if (fp == null) {
                return;
            }
        }

        switch (action) {
            case CONNECT -> {
                UUID oldNetwork = fp.network;
                if (!mayMoveBetweenNetworksOrWarn(player, oldNetwork, targetNetworkId)) {
                    return;
                }

                fp.network = targetNetworkId;

                invalidateSummary(oldNetwork);
                invalidateSummary(targetNetworkId);
                markDirtyAndNotify(panel);
            }
            case DISCONNECT -> {
                UUID oldNetwork = fp.network;
                if (!mayAdministrateOrWarn(player, oldNetwork)) {
                    return;
                }

                UUID newNetwork = disconnectedNetworkId(player, pos, fp.getPanelPosition().slot());
                fp.network = newNetwork;

                invalidateSummary(oldNetwork);
                invalidateSummary(newNetwork);
                markDirtyAndNotify(panel);
            }
        }
    }

    private void handleRedstoneRequester(ServerPlayer player, Level level, BlockPos pos, RedstoneRequesterBlockEntity requester) {
        LogisticallyLinkedBehaviour behaviour = requester.behaviour;
        purgeLegacyRegisteredPosFromAllNetworks(level, pos);

        switch (action) {
            case CONNECT -> {
                UUID oldNetwork = behaviour.freqId;
                if (!mayMoveBetweenNetworksOrWarn(player, oldNetwork, targetNetworkId)) {
                    return;
                }

                moveLocalBehaviourToNetwork(behaviour, targetNetworkId);

                invalidateSummary(oldNetwork);
                invalidateSummary(targetNetworkId);
                markDirtyAndNotify(requester);
            }
            case DISCONNECT -> {
                UUID oldNetwork = behaviour.freqId;
                if (!mayAdministrateOrWarn(player, oldNetwork)) {
                    return;
                }

                UUID newNetwork = disconnectedNetworkId(player, pos);
                moveLocalBehaviourToNetwork(behaviour, newNetwork);

                invalidateSummary(oldNetwork);
                invalidateSummary(newNetwork);
                markDirtyAndNotify(requester);
            }
        }
    }

    private void handleStockTicker(ServerPlayer player, Level level, BlockPos pos, StockTickerBlockEntity ticker) {
        LogisticallyLinkedBehaviour behaviour = ticker.behaviour;
        purgeLegacyRegisteredPosFromAllNetworks(level, pos);

        switch (action) {
            case CONNECT -> {
                UUID oldNetwork = behaviour.freqId;
                if (!mayMoveBetweenNetworksOrWarn(player, oldNetwork, targetNetworkId)) {
                    return;
                }

                moveLocalBehaviourToNetwork(behaviour, targetNetworkId);

                invalidateSummary(oldNetwork);
                invalidateSummary(targetNetworkId);
                markDirtyAndNotify(ticker);
            }
            case DISCONNECT -> {
                UUID oldNetwork = behaviour.freqId;
                if (!mayAdministrateOrWarn(player, oldNetwork)) {
                    return;
                }

                UUID newNetwork = disconnectedNetworkId(player, pos);
                moveLocalBehaviourToNetwork(behaviour, newNetwork);

                invalidateSummary(oldNetwork);
                invalidateSummary(newNetwork);
                markDirtyAndNotify(ticker);
            }
        }
    }

    private static UUID disconnectedNetworkId(ServerPlayer player, BlockPos pos) {
        return disconnectedNetworkId(player, pos, null);
    }

    private static UUID disconnectedNetworkId(ServerPlayer player, BlockPos pos, FactoryPanelBlock.PanelSlot slot) {
        String slotSuffix = slot == null ? "" : "_" + slot.name();
        String seed = "fluidlogistics:disconnected_" + player.getUUID() + "_" + pos + slotSuffix;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean mayAdministrateOrWarn(ServerPlayer player, UUID networkId) {
        if (mayAdministrate(player, networkId)) {
            return true;
        }
        player.displayClientMessage(
            Component.translatable("logistically_linked.protected")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_INVALID_COLOR))),
            true
        );
        return false;
    }

    private static boolean mayMoveBetweenNetworksOrWarn(ServerPlayer player, UUID sourceNetworkId, UUID targetNetworkId) {
        if (targetNetworkId == null) {
            return false;
        }
        if (!mayAdministrateOrWarn(player, sourceNetworkId)) {
            return false;
        }
        if (sourceNetworkId.equals(targetNetworkId)) {
            return true;
        }
        return mayAdministrateOrWarn(player, targetNetworkId);
    }

    private static boolean mayAdministrate(ServerPlayer player, UUID networkId) {
        if (Create.LOGISTICS.mayAdministrate(networkId, player)) {
            return true;
        }

        ItemStack heldStack = player.getMainHandItem();
        return HandPointerItem.isAuthorizedFor(heldStack, networkId);
    }

    private static void invalidateSummary(UUID networkId) {
        LogisticsManager.SUMMARIES.invalidate(networkId);
        LogisticsManager.ACCURATE_SUMMARIES.invalidate(networkId);
    }

    private static void markDirtyAndNotify(BlockEntity be) {
        be.setChanged();
        if (be instanceof SmartBlockEntity smart) {
            smart.notifyUpdate();
        }
    }

    private static void moveGlobalBehaviourToNetwork(Level level, BlockPos pos, LogisticallyLinkedBehaviour behaviour,
                                                     UUID newNetwork, UUID ownedBy) {
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
        UUID oldNetwork = behaviour.freqId;

        LogisticallyLinkedBehaviour.remove(behaviour);
        Create.LOGISTICS.linkRemoved(oldNetwork, globalPos);
        behaviour.freqId = newNetwork;
        Create.LOGISTICS.linkAdded(newNetwork, globalPos, ownedBy);
        Create.LOGISTICS.linkLoaded(newNetwork, globalPos);
        LogisticallyLinkedBehaviour.keepAlive(behaviour);
    }

    private static void moveLocalBehaviourToNetwork(LogisticallyLinkedBehaviour behaviour, UUID newNetwork) {
        LogisticallyLinkedBehaviour.remove(behaviour);
        behaviour.freqId = newNetwork;
        LogisticallyLinkedBehaviour.keepAlive(behaviour);
    }

    private static void purgeLegacyRegisteredPosFromAllNetworks(Level level, BlockPos pos) {
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
        boolean changed = false;

        var networkIterator = Create.LOGISTICS.logisticsNetworks.entrySet().iterator();
        while (networkIterator.hasNext()) {
            LogisticsNetwork network = networkIterator.next().getValue();
            if (network.totalLinks.remove(globalPos)) {
                changed = true;
            }
            network.loadedLinks.remove(globalPos);
            if (network.totalLinks.isEmpty()) {
                networkIterator.remove();
                changed = true;
            }
        }

        if (changed) {
            Create.LOGISTICS.markDirty();
        }
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.HAND_POINTER_LOGISTICS_NETWORK;
    }
}
