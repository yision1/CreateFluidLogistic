package com.yision.fluidlogistics.client.handpointer;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.yision.fluidlogistics.network.HandPointerMailboxStationConnectionPacket;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MailboxSelectionHandler {
    private static final int MAX_MAILBOXES = 5;
    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;

    private static final String STATION_HIGHLIGHT = "HandPointerStationHighlight";
    private static final String MAILBOX_TARGET = "HandPointerMailboxTarget";
    private static final String MAILBOX_HIGHLIGHT_PREFIX = "HandPointerMailboxHighlight_";

    private static final List<BlockPos> selectedMailboxes = new ArrayList<>();
    private static BlockPos selectedStationPos;
    private static int statusUpdateCounter;

    public static boolean hasSelection() {
        return !selectedMailboxes.isEmpty();
    }

    public static BlockPos getSelectedMailboxPos() {
        return hasSelection() ? selectedMailboxes.getFirst() : null;
    }

    public static boolean isMailboxSelected(BlockPos pos) {
        return selectedMailboxes.contains(pos);
    }

    public static void setSelection(BlockPos mailboxPos) {
        selectedMailboxes.clear();
        selectedMailboxes.add(mailboxPos);
        selectedStationPos = null;
        statusUpdateCounter = 0;
    }

    public static boolean addMailbox(BlockPos pos, Player player, Level level) {
        if (selectedMailboxes.size() >= MAX_MAILBOXES) {
            return false;
        }

        selectedMailboxes.add(pos);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f, false);

        CreateLang.builder()
            .translate("fluidlogistics.hand_pointer.mailbox.added", selectedMailboxes.size(), MAX_MAILBOXES)
            .color(0xDDC166)
            .sendStatus(player);
        return true;
    }

    public static void removeMailbox(BlockPos pos, Player player, Level level) {
        if (!selectedMailboxes.remove(pos)) {
            return;
        }

        removeMailboxHighlight(pos);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);

        if (hasSelection()) {
            CreateLang.builder()
                .translate("fluidlogistics.hand_pointer.mailbox.removed")
                .color(0xA5A5A5)
                .sendStatus(player);
        }
    }

    public static void clearSelection() {
        removeMailboxHighlights();
        selectedMailboxes.clear();
        selectedStationPos = null;
        statusUpdateCounter = 0;
        Outliner.getInstance().remove(STATION_HIGHLIGHT);
        Outliner.getInstance().remove(MAILBOX_TARGET);
    }

    public static void clearHoverPreview() {
        Outliner.getInstance().remove(MAILBOX_TARGET);
    }

    public static boolean isMailbox(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PostboxBlockEntity;
    }

    public static boolean isStation(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StationBlockEntity;
    }

    public static void tickStationTarget(Minecraft mc) {
        if (mc.level == null || mc.player == null || !hasSelection()) {
            selectedStationPos = null;
            return;
        }

        double range = 6;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK
            || mc.hitResult.getLocation().distanceToSqr(mc.player.getEyePosition()) > range * range) {
            selectedStationPos = null;
            return;
        }

        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        selectedStationPos = isStation(mc.level, pos) ? pos : null;
    }

    public static void renderSelection(Minecraft mc) {
        if (mc.level == null || !hasSelection()) {
            removeMailboxHighlights();
            Outliner.getInstance().remove(STATION_HIGHLIGHT);
            return;
        }

        Level level = mc.level;

        for (BlockPos mailboxPos : selectedMailboxes) {
            BlockState mailboxState = level.getBlockState(mailboxPos);
            VoxelShape mailboxShape = mailboxState.getShape(level, mailboxPos);
            if (!mailboxShape.isEmpty()) {
                Outliner.getInstance()
                    .showAABB(highlightKey(mailboxPos), mailboxShape.bounds().move(mailboxPos))
                    .colored(0xDDC166)
                    .lineWidth(0.0625F);
            }
        }

        if (selectedStationPos == null) {
            Outliner.getInstance().remove(STATION_HIGHLIGHT);
            return;
        }

        boolean outOfRange = isCurrentTargetOutOfRange(level);
        boolean alreadyConnected = isCurrentTargetAlreadyConnected(level);

        BlockState stationState = level.getBlockState(selectedStationPos);
        VoxelShape stationShape = stationState.getShape(level, selectedStationPos);
        if (!stationShape.isEmpty()) {
            int color = alreadyConnected || outOfRange ? 0xFF6171 : 0x708DAD;
            Outliner.getInstance()
                .showAABB(STATION_HIGHLIGHT, stationShape.bounds().move(selectedStationPos))
                .colored(color)
                .lineWidth(0.0625F);
        }

        int color = alreadyConnected || outOfRange ? 0xFF6171 : 0x9EF173;
        for (BlockPos mailboxPos : selectedMailboxes) {
            animateConnection(mc, Vec3.atCenterOf(mailboxPos), Vec3.atCenterOf(selectedStationPos), new Color(color));
        }
        updateConnectionStatus(mc, alreadyConnected, outOfRange);
    }

    private static void updateConnectionStatus(Minecraft mc, boolean alreadyConnected, boolean outOfRange) {
        if (mc.player == null || selectedStationPos == null) {
            return;
        }

        statusUpdateCounter++;
        if (statusUpdateCounter < 5) {
            return;
        }
        statusUpdateCounter = 0;

        String key;
        if (alreadyConnected) {
            key = "fluidlogistics.hand_pointer.mailbox.already_connected";
        } else if (outOfRange) {
            key = "fluidlogistics.hand_pointer.too_far";
        } else {
            key = "fluidlogistics.hand_pointer.mailbox.can_connect";
        }

        CreateLang.builder()
            .translate(key)
            .color(alreadyConnected || outOfRange ? STATUS_INVALID_COLOR : STATUS_CONNECTABLE_COLOR)
            .sendStatus(mc.player);
    }

    public static void renderHoveredConnectionPreview(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.hitResult == null) {
            clearHoverPreview();
            return;
        }

        if (mc.hitResult.getType() != HitResult.Type.BLOCK) {
            clearHoverPreview();
            return;
        }

        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof PostboxBlockEntity postbox)) {
            clearHoverPreview();
            return;
        }

        if (!(postbox.target instanceof PackagePortTarget.TrainStationFrogportTarget stationTarget)) {
            clearHoverPreview();
            return;
        }

        Vec3 targetLocation = stationTarget.getExactTargetLocation(postbox, mc.level, pos);
        if (targetLocation == null || targetLocation == Vec3.ZERO) {
            clearHoverPreview();
            return;
        }

        Outliner.getInstance()
            .chaseAABB(MAILBOX_TARGET, new AABB(targetLocation, targetLocation))
            .colored(0x9EF173)
            .lineWidth(1 / 6f)
            .disableLineNormals();
        animateConnection(mc, Vec3.atCenterOf(pos), targetLocation, new Color(0x9EF173));
    }

    public static boolean tryConnectCurrentTarget(Level level) {
        if (!hasSelection() || selectedStationPos == null) {
            return false;
        }

        if (isCurrentTargetAlreadyConnected(level) || isCurrentTargetOutOfRange(level)) {
            return false;
        }

        CatnipServices.NETWORK.sendToServer(new HandPointerMailboxStationConnectionPacket(selectedMailboxes, selectedStationPos));
        return true;
    }

    public static boolean isCurrentTargetAlreadyConnected(Level level) {
        if (!hasSelection() || selectedStationPos == null) {
            return false;
        }

        for (BlockPos mailboxPos : selectedMailboxes) {
            if (isStationAlreadyConnected(level, mailboxPos, selectedStationPos)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isCurrentTargetOutOfRange(Level level) {
        if (!hasSelection() || selectedStationPos == null) {
            return false;
        }

        Vec3 stationCenter = Vec3.atBottomCenterOf(selectedStationPos);
        for (BlockPos mailboxPos : selectedMailboxes) {
            if (!stationCenter.closerThan(
                Vec3.atBottomCenterOf(mailboxPos),
                AllConfigs.server().logistics.packagePortRange.get())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isStationAlreadyConnected(Level level, BlockPos mailboxPos, BlockPos stationPos) {
        BlockEntity mailboxBe = level.getBlockEntity(mailboxPos);
        if (!(mailboxBe instanceof PostboxBlockEntity postbox)) {
            return false;
        }
        if (!(postbox.target instanceof PackagePortTarget.TrainStationFrogportTarget stationTarget)) {
            return false;
        }
        return mailboxPos.offset(stationTarget.relativePos).equals(stationPos);
    }

    private static void removeMailboxHighlights() {
        for (BlockPos mailboxPos : selectedMailboxes) {
            removeMailboxHighlight(mailboxPos);
        }
    }

    private static void removeMailboxHighlight(BlockPos mailboxPos) {
        Outliner.getInstance().remove(highlightKey(mailboxPos));
    }

    private static String highlightKey(BlockPos mailboxPos) {
        return MAILBOX_HIGHLIGHT_PREFIX + mailboxPos.asLong();
    }

    private static void animateConnection(Minecraft mc, Vec3 source, Vec3 target, Color color) {
        DustParticleOptions data = new DustParticleOptions(color.asVectorF(), 1.0F);
        double totalFlyingTicks = 10;
        int segments = (((int) totalFlyingTicks) / 3) + 1;
        double tickOffset = totalFlyingTicks / segments;

        for (int i = 0; i < segments; i++) {
            double ticks = ((AnimationTickHolder.getRenderTime() / 3) % tickOffset) + i * tickOffset;
            Vec3 vec = source.lerp(target, ticks / totalFlyingTicks);
            mc.level.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
        }
    }
}
