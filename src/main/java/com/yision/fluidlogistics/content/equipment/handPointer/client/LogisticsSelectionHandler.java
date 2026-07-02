package com.yision.fluidlogistics.content.equipment.handPointer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerLogisticsNetworkPacket;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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

    private static UUID selectedNetworkId;
    private static final List<BlockPos> nearbyLogisticsCache = new ArrayList<>();
    private static BlockPos cacheCenter;
    private static int lastScanTick = -SCAN_INTERVAL;

    private LogisticsSelectionHandler() {
    }

    public static boolean hasSelection() {
        return selectedNetworkId != null;
    }

    public static void clearSelection() {
        selectedNetworkId = null;
        nearbyLogisticsCache.clear();
        cacheCenter = null;
        Outliner.getInstance().remove("HandPointerLogisticsHighlight");
    }

    public static boolean isLogisticsBlockEntity(BlockEntity be) {
        return be instanceof PackagerLinkBlockEntity
            || be instanceof FactoryPanelBlockEntity
            || be instanceof StockTickerBlockEntity
            || be instanceof RedstoneRequesterBlockEntity;
    }

    public static void handleNetworkClick(BlockEntity be, BlockPos pos, Player player, Level level, BlockState state,
                                          Vec3 clickLocation) {
        if (be instanceof PackagerLinkBlockEntity link) {
            processNetworkClick(link.behaviour.freqId, pos, player, level, FactoryPanelBlock.PanelSlot.BOTTOM_LEFT);
            return;
        }

        if (be instanceof FactoryPanelBlockEntity panel) {
            FactoryPanelBlock.PanelSlot slot = clickLocation != null
                ? FactoryPanelBlock.getTargetedSlot(pos, state, clickLocation)
                : FactoryPanelBlock.PanelSlot.BOTTOM_LEFT;

            if (panel.panels == null || panel.panels.isEmpty()) {
                return;
            }

            FactoryPanelBehaviour fp = panel.panels.get(slot);
            if (fp == null || !fp.isActive()) {
                slot = panel.panels.keySet().stream().findFirst().orElse(FactoryPanelBlock.PanelSlot.BOTTOM_LEFT);
                fp = panel.panels.get(slot);
            }
            if (fp == null) {
                return;
            }

            processNetworkClick(fp.network, pos, player, level, slot);
            return;
        }

        if (be instanceof StockTickerBlockEntity ticker) {
            processNetworkClick(ticker.behaviour.freqId, pos, player, level, FactoryPanelBlock.PanelSlot.BOTTOM_LEFT);
            return;
        }

        if (be instanceof RedstoneRequesterBlockEntity requester) {
            processNetworkClick(requester.behaviour.freqId, pos, player, level, FactoryPanelBlock.PanelSlot.BOTTOM_LEFT);
        }
    }

    private static void processNetworkClick(UUID currentNetworkId, BlockPos pos, Player player, Level level,
                                            FactoryPanelBlock.PanelSlot slot) {
        if (selectedNetworkId == null) {
            selectedNetworkId = currentNetworkId;
            playBlockSound(pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f, level);
            sendPlayerMessage(player, "fluidlogistics.hand_pointer.logistics.selected", STATUS_SELECTED_COLOR);
            return;
        }

        boolean isInSelectedNetwork = selectedNetworkId.equals(currentNetworkId);
        if (isInSelectedNetwork) {
            CatnipServices.NETWORK.sendToServer(new HandPointerLogisticsNetworkPacket(
                HandPointerLogisticsNetworkPacket.Action.DISCONNECT, pos, null, slot));
            playBlockSound(pos, SoundEvents.LEVER_CLICK, 0.3f, 0.5f, level);
            sendPlayerMessage(player, "fluidlogistics.hand_pointer.logistics.removed", STATUS_INVALID_COLOR);
            return;
        }

        CatnipServices.NETWORK.sendToServer(new HandPointerLogisticsNetworkPacket(
            HandPointerLogisticsNetworkPacket.Action.CONNECT, pos, selectedNetworkId, slot));
        playBlockSound(pos, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f, level);
        sendPlayerMessage(player, "fluidlogistics.hand_pointer.logistics.added", STATUS_CONNECTABLE_COLOR);
    }

    public static void renderSelection(Minecraft mc) {
        if (!hasSelection()) {
            Outliner.getInstance().remove("HandPointerLogisticsHighlight");
            return;
        }
        drawNetworkOutlines(mc);
    }

    private static void drawNetworkOutlines(Minecraft mc) {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || selectedNetworkId == null) {
            return;
        }

        int color = AnimationTickHolder.getTicks() % 16 < 8 ? HIGHLIGHT_COLOR_1 : HIGHLIGHT_COLOR_2;
        BlockPos playerPos = player.blockPosition();

        refreshNearbyLogisticsCacheIfNeeded(level, playerPos);
        for (BlockPos pos : nearbyLogisticsCache) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                continue;
            }
            renderIfMatchingNetwork(be, level, pos, color);
        }
    }

    private static void renderIfMatchingNetwork(BlockEntity be, Level level, BlockPos pos, int color) {
        if (be instanceof PackagerLinkBlockEntity link) {
            if (selectedNetworkId.equals(link.behaviour.freqId)) {
                renderBlockShapeHighlight(level, pos, color, 0);
            }
            return;
        }
        if (be instanceof StockTickerBlockEntity ticker) {
            if (selectedNetworkId.equals(ticker.behaviour.freqId)) {
                renderBlockShapeHighlight(level, pos, color, 0);
            }
            return;
        }
        if (be instanceof RedstoneRequesterBlockEntity requester) {
            if (selectedNetworkId.equals(requester.behaviour.freqId)) {
                renderBlockShapeHighlight(level, pos, color, 0);
            }
            return;
        }
        if (be instanceof FactoryPanelBlockEntity panel && panel.panels != null && !panel.panels.isEmpty()) {
            renderMatchingFactoryPanels(panel, pos, color);
        }
    }

    private static void renderBlockShapeHighlight(Level level, BlockPos pos, int color, int keyOffset) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) {
            return;
        }

        List<AABB> boxes = shape.toAabbs();
        for (int i = 0; i < boxes.size(); i++) {
            AABB box = boxes.get(i);
            Outliner.getInstance()
                .showAABB(Pair.of(pos, i + keyOffset), box.inflate(-1 / 128f).move(pos), 2)
                .lineWidth(1 / 32f)
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
                    if (!playerPos.closerThan(pos, RANGE)) {
                        return;
                    }
                    if (isLogisticsBlockEntity(be)) {
                        nearbyLogisticsCache.add(pos.immutable());
                    }
                });
            }
        }
    }

    private static void renderMatchingFactoryPanels(FactoryPanelBlockEntity panel, BlockPos pos, int color) {
        float xRot = Mth.RAD_TO_DEG * FactoryPanelBlock.getXRot(panel.getBlockState()) + 90;
        float yRot = Mth.RAD_TO_DEG * FactoryPanelBlock.getYRot(panel.getBlockState());
        Direction connectedDirection = FactoryPanelBlock.connectedDirection(panel.getBlockState());
        Vec3 inflateAxes = VecHelper.axisAlingedPlaneOf(connectedDirection);

        for (FactoryPanelBehaviour fp : panel.panels.values()) {
            if (fp == null || !fp.isActive() || !selectedNetworkId.equals(fp.network)) {
                continue;
            }

            FactoryPanelPosition panelPosition = fp.getPanelPosition();
            Vec3 vec = new Vec3(.25 + panelPosition.slot().xOffset * .5, 1 / 16f, .25 + panelPosition.slot().yOffset * .5);
            vec = VecHelper.rotateCentered(vec, 180, Axis.Y);
            vec = VecHelper.rotateCentered(vec, xRot, Axis.X);
            vec = VecHelper.rotateCentered(vec, yRot, Axis.Y);
            AABB bb = new AABB(vec, vec).inflate(1 / 16f)
                .inflate(inflateAxes.x * 3 / 16f, inflateAxes.y * 3 / 16f, inflateAxes.z * 3 / 16f);

            Outliner.getInstance()
                .showAABB(Pair.of(pos, panelPosition.slot().ordinal() + 1000), bb.move(pos), 2)
                .lineWidth(1 / 32f)
                .disableLineNormals()
                .colored(color);
        }
    }

    private static void playBlockSound(BlockPos pos, net.minecraft.sounds.SoundEvent sound, float volume, float pitch,
                                       Level level) {
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    private static void sendPlayerMessage(Player player, String translationKey, int color) {
        CreateLang.builder()
            .translate(translationKey)
            .color(color)
            .sendStatus(player);
    }
}
