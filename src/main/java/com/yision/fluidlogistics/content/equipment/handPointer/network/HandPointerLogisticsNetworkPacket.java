package com.yision.fluidlogistics.content.equipment.handPointer.network;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.content.equipment.handPointer.HandPointerItem;
import com.yision.fluidlogistics.content.equipment.handPointer.logistics.LogisticsLinkResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent.Context;

public class HandPointerLogisticsNetworkPacket extends SimplePacketBase {

    public enum Action {
        CONNECT,
        DISCONNECT
    }

    private static final int STATUS_INVALID_COLOR = 0xFF6171;
    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;

    private final Action action;
    private final BlockPos sourcePos;
    private final UUID targetNetworkId;
    private final FactoryPanelBlock.PanelSlot panelSlot;
    private final boolean allSlots;
    private final int handPointerSlot;

    public HandPointerLogisticsNetworkPacket(Action action, BlockPos sourcePos, UUID targetNetworkId,
                                             FactoryPanelBlock.PanelSlot panelSlot, boolean allSlots,
                                             int handPointerSlot) {
        this.action = action;
        this.sourcePos = sourcePos;
        this.targetNetworkId = targetNetworkId;
        this.panelSlot = panelSlot;
        this.allSlots = allSlots;
        this.handPointerSlot = handPointerSlot;
    }

    public HandPointerLogisticsNetworkPacket(Action action, BlockPos sourcePos, UUID targetNetworkId,
                                             FactoryPanelBlock.PanelSlot panelSlot) {
        this(action, sourcePos, targetNetworkId, panelSlot, false, -1);
    }

    public HandPointerLogisticsNetworkPacket(Action action, BlockPos sourcePos, UUID targetNetworkId,
                                             FactoryPanelBlock.PanelSlot panelSlot, boolean allSlots) {
        this(action, sourcePos, targetNetworkId, panelSlot, allSlots, -1);
    }

    public HandPointerLogisticsNetworkPacket(FriendlyByteBuf buffer) {
        this.action = readAction(buffer);
        this.sourcePos = buffer.readBlockPos();
        this.targetNetworkId = buffer.readBoolean() ? buffer.readUUID() : null;
        this.panelSlot = HandPointerAuthorizeLogisticsNetworkPacket.readPanelSlot(buffer);
        this.allSlots = buffer.readBoolean();
        this.handPointerSlot = buffer.readVarInt();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(action.ordinal());
        buffer.writeBlockPos(sourcePos);
        buffer.writeBoolean(targetNetworkId != null);
        if (targetNetworkId != null) {
            buffer.writeUUID(targetNetworkId);
        }
        HandPointerAuthorizeLogisticsNetworkPacket.writePanelSlot(buffer, panelSlot);
        buffer.writeBoolean(allSlots);
        buffer.writeVarInt(handPointerSlot);
    }

    private static Action readAction(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        Action[] values = Action.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return Action.CONNECT;
        }
        return values[ordinal];
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            Level level = player.level();
            ItemStack handPointerStack = getHandPointerStack(player);
            if (!HandPointerInteractionGuard.canUseHandPointer(player, sourcePos, handPointerStack)) {
                return;
            }

            if (level.getBlockEntity(sourcePos) instanceof FactoryPanelBlockEntity panel) {
                handleFactoryPanel(player, level, sourcePos, panel, handPointerStack);
                return;
            }

            LogisticsLinkResolver.resolve(level, sourcePos, panelSlot)
                .ifPresent(resolved -> handleLinkedBehaviour(player, level, sourcePos, resolved, handPointerStack));
        });
        return true;
    }

    private ItemStack getHandPointerStack(ServerPlayer player) {
        if (handPointerSlot >= 0 && handPointerSlot < 9
            && handPointerSlot < player.getInventory().getContainerSize()) {
            return player.getInventory().getItem(handPointerSlot);
        }
        return player.getMainHandItem();
    }

    private void handleFactoryPanel(ServerPlayer player, Level level, BlockPos pos, FactoryPanelBlockEntity panel,
                                    ItemStack handPointerStack) {
        purgeLegacyRegisteredPosFromAllNetworks(level, pos);

        List<FactoryPanelBehaviour> panels = getTargetPanels(panel);
        if (panels.isEmpty()) {
            displayStatus(player, "create.fluidlogistics.hand_pointer.logistics.no_panel_at_slot", STATUS_INVALID_COLOR);
            return;
        }

        switch (action) {
            case CONNECT -> {
                if (targetNetworkId == null) {
                    return;
                }
                for (FactoryPanelBehaviour fp : panels) {
                    if (!mayMoveBetweenNetworksOrWarn(player, fp.network, targetNetworkId, handPointerStack)) {
                        return;
                    }
                }
                for (FactoryPanelBehaviour fp : panels) {
                    moveFactoryPanelToNetwork(fp, targetNetworkId);
                }
                markDirtyAndNotify(panel);
                displayResultStatus(player);
            }
            case DISCONNECT -> {
                for (FactoryPanelBehaviour fp : panels) {
                    if (!mayAdministrateOrWarn(player, fp.network, handPointerStack)) {
                        return;
                    }
                }
                for (FactoryPanelBehaviour fp : panels) {
                    moveFactoryPanelToNetwork(fp,
                        disconnectedNetworkId(player.getUUID(), pos, fp.getPanelPosition().slot()));
                }
                markDirtyAndNotify(panel);
                displayResultStatus(player);
            }
        }
    }

    private void handleLinkedBehaviour(ServerPlayer player, Level level, BlockPos pos,
                                       LogisticsLinkResolver.ResolvedLink resolved, ItemStack handPointerStack) {
        LogisticallyLinkedBehaviour behaviour = resolved.behaviour();
        if (behaviour == null) {
            return;
        }
        if (!resolved.global()) {
            purgeLegacyRegisteredPosFromAllNetworks(level, pos);
        }

        switch (action) {
            case CONNECT -> {
                UUID oldNetwork = behaviour.freqId;
                if (!mayMoveBetweenNetworksOrWarn(player, oldNetwork, targetNetworkId, handPointerStack)) {
                    return;
                }

                moveBehaviourToNetwork(level, pos, behaviour, targetNetworkId, resolved.global(), null);
                invalidateSummary(oldNetwork);
                invalidateSummary(targetNetworkId);
                markDirtyAndNotify(behaviour.blockEntity);
                displayResultStatus(player);
            }
            case DISCONNECT -> {
                UUID oldNetwork = behaviour.freqId;
                if (!mayAdministrateOrWarn(player, oldNetwork, handPointerStack)) {
                    return;
                }

                UUID newNetwork = disconnectedNetworkId(player, pos);
                moveBehaviourToNetwork(level, pos, behaviour, newNetwork, resolved.global(), player.getUUID());
                invalidateSummary(oldNetwork);
                invalidateSummary(newNetwork);
                markDirtyAndNotify(behaviour.blockEntity);
                displayResultStatus(player);
            }
        }
    }

    private static UUID disconnectedNetworkId(ServerPlayer player, BlockPos pos) {
        return disconnectedNetworkId(player.getUUID(), pos, null);
    }

    public static UUID disconnectedNetworkId(UUID playerId, BlockPos pos, FactoryPanelBlock.PanelSlot slot) {
        String slotSuffix = slot == null ? "" : "_" + slot.name();
        String seed = "fluidlogistics:disconnected_" + playerId + "_" + pos + slotSuffix;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean mayAdministrateOrWarn(ServerPlayer player, UUID networkId, ItemStack handPointerStack) {
        if (mayAdministrate(player, networkId, handPointerStack)) {
            return true;
        }
        player.displayClientMessage(
            Component.translatable("logistically_linked.protected")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(STATUS_INVALID_COLOR))),
            true
        );
        return false;
    }

    private static boolean mayMoveBetweenNetworksOrWarn(ServerPlayer player, UUID sourceNetworkId, UUID targetNetworkId,
                                                       ItemStack handPointerStack) {
        if (targetNetworkId == null) {
            return false;
        }
        if (!mayAdministrateOrWarn(player, sourceNetworkId, handPointerStack)) {
            return false;
        }
        if (sourceNetworkId.equals(targetNetworkId)) {
            return true;
        }
        return mayAdministrateOrWarn(player, targetNetworkId, handPointerStack);
    }

    private static boolean mayAdministrate(ServerPlayer player, UUID networkId, ItemStack handPointerStack) {
        if (Create.LOGISTICS.mayAdministrate(networkId, player)) {
            return true;
        }

        return HandPointerItem.isAuthorizedFor(handPointerStack, networkId);
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

    private List<FactoryPanelBehaviour> getTargetPanels(FactoryPanelBlockEntity panel) {
        if (panel.panels == null || panel.panels.isEmpty()) {
            return List.of();
        }
        if (allSlots) {
            return panel.panels.values().stream()
                .filter(FactoryPanelBehaviour::isActive)
                .toList();
        }
        return LogisticsLinkResolver.resolvePanel(panel, panelSlot)
            .stream()
            .toList();
    }

    private static void moveFactoryPanelToNetwork(FactoryPanelBehaviour fp, UUID newNetwork) {
        UUID oldNetwork = fp.network;
        if (oldNetwork.equals(newNetwork)) {
            return;
        }

        clearOldPanelPromises(fp, oldNetwork, newNetwork);
        fp.setNetwork(newNetwork);
        invalidateSummary(oldNetwork);
        invalidateSummary(newNetwork);
    }

    private static void clearOldPanelPromises(FactoryPanelBehaviour fp, UUID oldNetwork, UUID newNetwork) {
        if (fp.panelBE().restocker || fp.getFilter().isEmpty() || oldNetwork.equals(newNetwork)
            || !Create.LOGISTICS.hasQueuedPromises(oldNetwork)) {
            return;
        }
        Create.LOGISTICS.getQueuedPromises(oldNetwork).forceClear(fp.getFilter());
    }

    private void displayResultStatus(ServerPlayer player) {
        String key = action == Action.CONNECT
            ? "create.fluidlogistics.hand_pointer.logistics.added"
            : "create.fluidlogistics.hand_pointer.logistics.removed";
        int color = action == Action.CONNECT ? STATUS_CONNECTABLE_COLOR : STATUS_INVALID_COLOR;
        displayStatus(player, key, color);
    }

    private static void displayStatus(ServerPlayer player, String translationKey, int color) {
        player.displayClientMessage(
            Component.translatable(translationKey)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))),
            true
        );
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

    private static void moveBehaviourToNetwork(Level level, BlockPos pos, LogisticallyLinkedBehaviour behaviour,
                                               UUID newNetwork, boolean global, UUID ownedBy) {
        if (global) {
            moveGlobalBehaviourToNetwork(level, pos, behaviour, newNetwork, ownedBy);
        } else {
            moveLocalBehaviourToNetwork(behaviour, newNetwork);
        }
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
}
