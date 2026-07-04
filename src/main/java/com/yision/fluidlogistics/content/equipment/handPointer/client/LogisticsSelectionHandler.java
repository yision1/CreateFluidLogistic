package com.yision.fluidlogistics.content.equipment.handPointer.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.content.equipment.handPointer.logistics.LogisticsLinkResolver;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerLogisticsNetworkPacket;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LogisticsSelectionHandler {
    private static final int HIGHLIGHT_COLOR_1 = 0x708DAD;
    private static final int HIGHLIGHT_COLOR_2 = 0x90ADCD;
    private static final int STATUS_SELECTED_COLOR = 0xDDC166;
    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;
    private static final int RANGE = 64;
    private static final int SCAN_INTERVAL = 10;
    private static final int PENDING_OVERRIDE_TICKS = 200;
    private static final int HOVER_KEY_OFFSET = 2000;
    private static final float SELECTED_LINE_WIDTH = 1 / 32f;
    private static final float SELECTED_BLOCK_INFLATE = -1 / 128f;
    private static final float SELECTED_PANEL_INFLATE = -1.5f / 128f;

    private static UUID selectedNetworkId;
    private static final List<BlockPos> nearbyLogisticsCache = new ArrayList<>();
    private static BlockPos cacheCenter;
    private static int lastScanTick = -SCAN_INTERVAL;
    private static int hoverStatusUpdateCounter;
    private static final Map<HighlightTarget, PendingMembership> pendingMembership = new HashMap<>();

    private record HighlightTarget(BlockPos pos, FactoryPanelBlock.PanelSlot slot) {
        private HighlightTarget {
            pos = pos.immutable();
        }

        static HighlightTarget block(BlockPos pos) {
            return new HighlightTarget(pos, null);
        }

        static HighlightTarget panel(BlockPos pos, FactoryPanelBlock.PanelSlot slot) {
            return new HighlightTarget(pos, slot);
        }

        boolean panelSlot() {
            return slot != null;
        }
    }

    private record HoverExclusion(BlockPos pos, FactoryPanelBlock.PanelSlot slot, boolean allPanelSlots) {
        private HoverExclusion {
            pos = pos.immutable();
        }

        boolean matches(HighlightTarget target) {
            if (!pos.equals(target.pos())) {
                return false;
            }
            if (allPanelSlots) {
                return target.panelSlot();
            }
            if (slot == null) {
                return !target.panelSlot();
            }
            return slot == target.slot();
        }
    }

    private record PendingMembership(UUID addedNetworkId, UUID removedNetworkId, boolean disconnect, int expiresAt) {
    }

    private LogisticsSelectionHandler() {
    }

    public static boolean hasSelection() {
        return selectedNetworkId != null;
    }

    public static void clearSelection() {
        selectedNetworkId = null;
        nearbyLogisticsCache.clear();
        pendingMembership.clear();
        cacheCenter = null;
        hoverStatusUpdateCounter = 0;
        Outliner.getInstance().remove("HandPointerLogisticsHighlight");
    }

    public static void clearPendingMembership() {
        pendingMembership.clear();
    }

    public static void clearHoverPreview() {
        hoverStatusUpdateCounter = 0;
    }

    public static boolean isLogisticsBlockEntity(BlockEntity be) {
        if (be == null) {
            return false;
        }
        if (be instanceof FactoryPanelBlockEntity panel) {
            return panel.panels != null && panel.panels.values().stream().anyMatch(FactoryPanelBehaviour::isActive);
        }
        return BlockEntityBehaviour.get(be, LogisticallyLinkedBehaviour.TYPE) != null;
    }

    public static boolean isFactoryPanel(BlockEntity be) {
        return be instanceof FactoryPanelBlockEntity;
    }

    public static void handleNetworkClick(BlockEntity be, BlockPos pos, Player player, Level level, BlockState state,
                                          Vec3 clickLocation) {
        boolean allSlots = selectedNetworkId != null && player.isShiftKeyDown() && be instanceof FactoryPanelBlockEntity;
        Optional<LogisticsLinkResolver.ResolvedLink> resolved =
            LogisticsLinkResolver.resolve(level, pos, state, clickLocation);

        if (resolved.isEmpty()) {
            if (be instanceof FactoryPanelBlockEntity) {
                playDenySound(pos, level);
                sendPlayerMessage(player, "fluidlogistics.hand_pointer.logistics.no_panel_at_slot",
                    STATUS_INVALID_COLOR);
            }
            if (selectedNetworkId == null) {
                HandPointerModeManager.exitMode(player, level);
            }
            return;
        }

        FactoryPanelBlock.PanelSlot slot = resolved.get().panel() == null
            ? FactoryPanelBlock.PanelSlot.BOTTOM_LEFT
            : resolved.get().panel().getPanelPosition().slot();
        List<HighlightTarget> targets = getHighlightTargets(level, pos, resolved.get(), allSlots);
        HighlightTarget primaryTarget = getPrimaryHighlightTarget(pos, resolved.get());
        cleanupPendingMembership(level);
        UUID currentNetworkId = effectiveNetworkId(primaryTarget, resolved.get().networkId());
        processNetworkClick(currentNetworkId, pos, player, level, slot, allSlots, targets);
    }

    private static void processNetworkClick(UUID currentNetworkId, BlockPos pos, Player player, Level level,
                                            FactoryPanelBlock.PanelSlot slot, boolean allSlots,
                                            List<HighlightTarget> targets) {
        if (selectedNetworkId == null) {
            selectedNetworkId = currentNetworkId;
            playBlockSound(pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f, level);
            sendPlayerMessage(player, "fluidlogistics.hand_pointer.logistics.selected", STATUS_SELECTED_COLOR);
            return;
        }

        boolean isInSelectedNetwork = selectedNetworkId.equals(currentNetworkId);
        int handPointerSlot = player.getInventory().selected;
        if (isInSelectedNetwork) {
            CatnipServices.NETWORK.sendToServer(new HandPointerLogisticsNetworkPacket(
                HandPointerLogisticsNetworkPacket.Action.DISCONNECT, pos, null, slot, allSlots, handPointerSlot));
            recordDisconnectPendingMembership(level, player, targets);
            removeSelectedHighlights(level, targets);
            playBlockSound(pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f, level);
            return;
        }

        CatnipServices.NETWORK.sendToServer(new HandPointerLogisticsNetworkPacket(
            HandPointerLogisticsNetworkPacket.Action.CONNECT, pos, selectedNetworkId, slot, allSlots, handPointerSlot));
        recordConnectPendingMembership(level, targets, selectedNetworkId);
        removeSelectedHighlights(level, targets);
        playBlockSound(pos, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f, level);
    }

    public static void renderSelection(Minecraft mc) {
        if (!hasSelection()) {
            Outliner.getInstance().remove("HandPointerLogisticsHighlight");
            return;
        }
        drawNetworkOutlines(mc, selectedNetworkId, HIGHLIGHT_COLOR_1, getHoverExclusion(mc));
    }

    public static void renderHoverPreview(Minecraft mc) {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || !(mc.hitResult instanceof BlockHitResult hitResult)
            || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            clearHoverPreview();
            return;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Optional<LogisticsLinkResolver.ResolvedLink> resolved =
            LogisticsLinkResolver.resolve(level, pos, state, hitResult.getLocation());
        if (resolved.isEmpty()) {
            clearHoverPreview();
            return;
        }

        HighlightTarget primaryTarget = getPrimaryHighlightTarget(pos, resolved.get());
        cleanupPendingMembership(level);
        UUID hoveredNetwork = effectiveNetworkId(primaryTarget, resolved.get().networkId());
        if (selectedNetworkId == null) {
            int loadedLinks = drawNetworkOutlines(mc, hoveredNetwork, flashingHighlightColor(), null);
            sendHoverStatus(player, "fluidlogistics.hand_pointer.logistics.hover_network_size",
                HIGHLIGHT_COLOR_1, loadedLinks);
            return;
        }

        boolean sameNetwork = selectedNetworkId.equals(hoveredNetwork);
        int color = sameNetwork ? STATUS_INVALID_COLOR : STATUS_CONNECTABLE_COLOR;
        if (resolved.get().panel() != null && !player.isShiftKeyDown()) {
            renderHoverFactoryPanelSlot(resolved.get().panel(), color);
        } else {
            renderHoverBlockShapeHighlight(level, pos, color);
        }
        sendHoverStatus(player, sameNetwork
            ? "fluidlogistics.hand_pointer.logistics.hover_will_disconnect"
            : "fluidlogistics.hand_pointer.logistics.hover_will_connect", color);
    }

    private static int drawNetworkOutlines(Minecraft mc, UUID networkId, int color, HoverExclusion hoverExclusion) {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || networkId == null) {
            return 0;
        }
        cleanupPendingMembership(level);

        int rendered = 0;
        for (LogisticallyLinkedBehaviour behaviour : LogisticallyLinkedBehaviour.getAllPresent(networkId, false, true)) {
            SmartBlockEntity be = behaviour.blockEntity;
            HighlightTarget target = HighlightTarget.block(be.getBlockPos());
            if (!player.canInteractWithBlock(be.getBlockPos(), RANGE)) {
                continue;
            }
            if (!networkId.equals(behaviour.freqId)) {
                removeBlockShapeHighlight(level, be.getBlockPos(), 0);
                continue;
            }
            if (isPendingRemoved(target, networkId) || isHoverExcluded(target, hoverExclusion)) {
                removeBlockShapeHighlight(level, be.getBlockPos(), 0);
                continue;
            }
            renderBlockShapeHighlight(level, be.getBlockPos(), color, 0);
            rendered++;
        }

        BlockPos playerPos = player.blockPosition();
        refreshNearbyLogisticsCacheIfNeeded(level, playerPos);
        for (BlockPos pos : nearbyLogisticsCache) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FactoryPanelBlockEntity panel) {
                rendered += renderMatchingFactoryPanels(panel, networkId, color, hoverExclusion);
            }
        }
        rendered += renderPendingAddedTargets(player, level, networkId, color, hoverExclusion);
        return rendered;
    }

    private static void renderBlockShapeHighlight(Level level, BlockPos pos, int color, int keyOffset) {
        renderBlockShapeHighlight(level, pos, color, keyOffset, SELECTED_BLOCK_INFLATE, SELECTED_LINE_WIDTH);
    }

    private static void renderHoverBlockShapeHighlight(Level level, BlockPos pos, int color) {
        renderBlockShapeHighlight(level, pos, color, HOVER_KEY_OFFSET);
    }

    private static void renderBlockShapeHighlight(Level level, BlockPos pos, int color, int keyOffset,
                                                  float inflate, float lineWidth) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) {
            return;
        }

        List<AABB> boxes = shape.toAabbs();
        for (int i = 0; i < boxes.size(); i++) {
            AABB box = boxes.get(i);
            Outliner.getInstance()
                .showAABB(Pair.of(pos, i + keyOffset), box.inflate(inflate).move(pos), 2)
                .lineWidth(lineWidth)
                .disableLineNormals()
                .colored(color);
        }
    }

    private static void refreshNearbyLogisticsCacheIfNeeded(Level level, BlockPos playerPos) {
        int tick = AnimationTickHolder.getTicks();
        if (cacheCenter != null
            && cacheCenter.closerThan(playerPos, 8)
            && tick - lastScanTick < SCAN_INTERVAL) {
            return;
        }

        nearbyLogisticsCache.clear();
        cacheCenter = playerPos;
        lastScanTick = tick;

        int chunkRadius = (RANGE + 15) / 16;
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }

                ChunkPos chunkPos = new ChunkPos(cx, cz);
                level.getChunk(chunkPos.x, chunkPos.z).getBlockEntities().forEach((pos, be) -> {
                    if (playerPos.closerThan(pos, RANGE) && be instanceof FactoryPanelBlockEntity) {
                        nearbyLogisticsCache.add(pos.immutable());
                    }
                });
            }
        }
    }

    private static int renderMatchingFactoryPanels(FactoryPanelBlockEntity panel, UUID networkId, int color,
                                                   HoverExclusion hoverExclusion) {
        if (panel.panels == null) {
            return 0;
        }
        int rendered = 0;
        for (FactoryPanelBehaviour fp : panel.panels.values()) {
            if (fp == null || !fp.isActive() || !networkId.equals(fp.network)) {
                continue;
            }
            HighlightTarget target = HighlightTarget.panel(panel.getBlockPos(), fp.getPanelPosition().slot());
            if (isPendingRemoved(target, networkId) || isHoverExcluded(target, hoverExclusion)) {
                removeFactoryPanelSlot(fp, 1000);
                continue;
            }
            renderFactoryPanelSlot(fp, color, 1000);
            rendered++;
        }
        return rendered;
    }

    private static int renderPendingAddedTargets(LocalPlayer player, Level level, UUID networkId, int color,
                                                 HoverExclusion hoverExclusion) {
        int rendered = 0;
        for (Map.Entry<HighlightTarget, PendingMembership> entry : pendingMembership.entrySet()) {
            HighlightTarget target = entry.getKey();
            PendingMembership membership = entry.getValue();
            if (!networkId.equals(membership.addedNetworkId())
                || !player.canInteractWithBlock(target.pos(), RANGE)) {
                continue;
            }
            if (isHoverExcluded(target, hoverExclusion)) {
                removeSelectedHighlight(level, target);
                continue;
            }
            if (!target.panelSlot()) {
                renderBlockShapeHighlight(level, target.pos(), color, 0);
                rendered++;
                continue;
            }
            BlockEntity be = level.getBlockEntity(target.pos());
            if (!(be instanceof FactoryPanelBlockEntity panel) || panel.panels == null) {
                continue;
            }
            FactoryPanelBehaviour fp = panel.panels.get(target.slot());
            if (fp == null || !fp.isActive()) {
                continue;
            }
            renderFactoryPanelSlot(fp, color, 1000);
            rendered++;
        }
        return rendered;
    }

    private static void removeSelectedHighlights(Level level, List<HighlightTarget> targets) {
        for (HighlightTarget target : targets) {
            removeSelectedHighlight(level, target);
        }
    }

    private static void removeSelectedHighlight(Level level, HighlightTarget target) {
        if (!target.panelSlot()) {
            removeBlockShapeHighlight(level, target.pos(), 0);
            return;
        }
        BlockEntity be = level.getBlockEntity(target.pos());
        if (!(be instanceof FactoryPanelBlockEntity panel) || panel.panels == null) {
            return;
        }
        FactoryPanelBehaviour fp = panel.panels.get(target.slot());
        if (fp != null) {
            removeFactoryPanelSlot(fp, 1000);
        }
    }

    private static void renderFactoryPanelSlot(FactoryPanelBehaviour fp, int color, int keyOffset) {
        renderFactoryPanelSlot(fp, color, keyOffset, SELECTED_PANEL_INFLATE, SELECTED_LINE_WIDTH);
    }

    private static void renderHoverFactoryPanelSlot(FactoryPanelBehaviour fp, int color) {
        renderFactoryPanelSlot(fp, color, HOVER_KEY_OFFSET);
    }

    private static void renderFactoryPanelSlot(FactoryPanelBehaviour fp, int color, int keyOffset,
                                               float inflate, float lineWidth) {
        Outliner.getInstance()
            .showAABB(Pair.of(fp.getPanelPosition(), keyOffset),
                FactoryPanelConnectionHandler.getBB(fp.blockEntity.getBlockState(), fp.getPanelPosition())
                    .inflate(inflate), 2)
            .lineWidth(lineWidth)
            .disableLineNormals()
            .colored(color);
    }

    private static void removeBlockShapeHighlight(Level level, BlockPos pos, int keyOffset) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) {
            return;
        }

        List<AABB> boxes = shape.toAabbs();
        for (int i = 0; i < boxes.size(); i++) {
            Outliner.getInstance().remove(Pair.of(pos, i + keyOffset));
        }
    }

    private static void removeFactoryPanelSlot(FactoryPanelBehaviour fp, int keyOffset) {
        Outliner.getInstance().remove(Pair.of(fp.getPanelPosition(), keyOffset));
    }

    private static int flashingHighlightColor() {
        return AnimationTickHolder.getTicks() % 16 < 8 ? HIGHLIGHT_COLOR_1 : HIGHLIGHT_COLOR_2;
    }

    private static List<HighlightTarget> getHighlightTargets(Level level, BlockPos pos,
                                                             LogisticsLinkResolver.ResolvedLink resolved,
                                                             boolean allSlots) {
        if (resolved.panel() == null) {
            return List.of(HighlightTarget.block(pos));
        }
        if (!allSlots || !(level.getBlockEntity(pos) instanceof FactoryPanelBlockEntity panel)
            || panel.panels == null) {
            return List.of(HighlightTarget.panel(pos, resolved.panel().getPanelPosition().slot()));
        }
        return panel.panels.values().stream()
            .filter(fp -> fp != null && fp.isActive())
            .map(fp -> HighlightTarget.panel(pos, fp.getPanelPosition().slot()))
            .toList();
    }

    private static HighlightTarget getPrimaryHighlightTarget(BlockPos pos, LogisticsLinkResolver.ResolvedLink resolved) {
        if (resolved.panel() == null) {
            return HighlightTarget.block(pos);
        }
        return HighlightTarget.panel(pos, resolved.panel().getPanelPosition().slot());
    }

    private static HoverExclusion getHoverExclusion(Minecraft mc) {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || !(mc.hitResult instanceof BlockHitResult hitResult)
            || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hitResult.getBlockPos();
        Optional<LogisticsLinkResolver.ResolvedLink> resolved =
            LogisticsLinkResolver.resolve(level, pos, level.getBlockState(pos), hitResult.getLocation());
        if (resolved.isEmpty()) {
            return null;
        }
        if (resolved.get().panel() == null) {
            return new HoverExclusion(pos, null, false);
        }
        return new HoverExclusion(pos, resolved.get().panel().getPanelPosition().slot(), player.isShiftKeyDown());
    }

    private static UUID effectiveNetworkId(HighlightTarget target, UUID actualNetwork) {
        PendingMembership membership = pendingMembership.get(target);
        if (membership != null && membership.addedNetworkId() != null) {
            return membership.addedNetworkId();
        }
        return actualNetwork;
    }

    private static void recordConnectPendingMembership(Level level, List<HighlightTarget> targets, UUID addedNetworkId) {
        recordPendingMembership(level, targets, target -> addedNetworkId, false);
    }

    private static void recordDisconnectPendingMembership(Level level, Player player, List<HighlightTarget> targets) {
        recordPendingMembership(level, targets,
            target -> HandPointerLogisticsNetworkPacket.disconnectedNetworkId(player.getUUID(), target.pos(),
                target.slot()),
            true);
    }

    private static void recordPendingMembership(Level level, List<HighlightTarget> targets,
                                                Function<HighlightTarget, UUID> addedNetworkId, boolean disconnect) {
        cleanupPendingMembership(level);
        int expiresAt = AnimationTickHolder.getTicks() + PENDING_OVERRIDE_TICKS;
        for (HighlightTarget target : targets) {
            pendingMembership.put(target,
                new PendingMembership(addedNetworkId.apply(target), actualNetworkId(level, target), disconnect,
                    expiresAt));
        }
    }

    private static boolean isPendingRemoved(HighlightTarget target, UUID networkId) {
        PendingMembership membership = pendingMembership.get(target);
        return membership != null && networkId.equals(membership.removedNetworkId());
    }

    private static boolean isHoverExcluded(HighlightTarget target, HoverExclusion hoverExclusion) {
        return hoverExclusion != null && hoverExclusion.matches(target);
    }

    private static void cleanupPendingMembership(Level level) {
        int tick = AnimationTickHolder.getTicks();
        pendingMembership.entrySet().removeIf(entry -> {
            PendingMembership membership = entry.getValue();
            if (membership.expiresAt() <= tick) {
                return true;
            }
            if (level == null) {
                return false;
            }
            UUID actualNetworkId = actualNetworkId(level, entry.getKey());
            if (actualNetworkId == null) {
                return false;
            }
            if (membership.disconnect()) {
                return membership.removedNetworkId() != null && !membership.removedNetworkId().equals(actualNetworkId);
            }
            return membership.addedNetworkId() != null && membership.addedNetworkId().equals(actualNetworkId);
        });
    }

    private static UUID actualNetworkId(Level level, HighlightTarget target) {
        return LogisticsLinkResolver.resolve(level, target.pos(), target.slot())
            .map(LogisticsLinkResolver.ResolvedLink::networkId)
            .orElse(null);
    }

    private static void playBlockSound(BlockPos pos, net.minecraft.sounds.SoundEvent sound, float volume, float pitch,
                                       Level level) {
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    private static void playDenySound(BlockPos pos, Level level) {
        AllSoundEvents.DENY.playAt(level, pos, 1, 1, false);
    }

    private static void sendPlayerMessage(Player player, String translationKey, int color) {
        CreateLang.builder()
            .translate(translationKey)
            .color(color)
            .sendStatus(player);
    }

    private static void sendHoverStatus(Player player, String translationKey, int color, Object... args) {
        hoverStatusUpdateCounter++;
        if (hoverStatusUpdateCounter < 10) {
            return;
        }
        hoverStatusUpdateCounter = 0;
        CreateLang.builder()
            .translate(translationKey, args)
            .color(color)
            .sendStatus(player);
    }
}
