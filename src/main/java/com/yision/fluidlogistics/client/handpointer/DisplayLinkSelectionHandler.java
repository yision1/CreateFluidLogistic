package com.yision.fluidlogistics.client.handpointer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;

import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DisplayLinkSelectionHandler {
    private static final String DISPLAY_BOARD_HIGHLIGHT = "HandPointerDisplayBoardHighlight";
    private static final String DISPLAY_LINK_HIGHLIGHT = "HandPointerDisplayLinkHighlight";
    private static final String DISPLAY_BOARD_TARGET = "HandPointerDisplayBoardTarget";
    private static final String DISPLAY_LINK_TARGET = "HandPointerDisplayLinkTarget";

    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;

    private static BlockPos selectedDisplayBoardPos;
    private static AABB selectedDisplayBoardBounds;
    private static BlockPos hoveredDisplayLinkPos;
    private static int statusUpdateCounter;

    private DisplayLinkSelectionHandler() {
    }

    public static boolean hasSelection() {
        return selectedDisplayBoardPos != null;
    }

    public static boolean hasSelectedDisplayBoard() {
        return selectedDisplayBoardPos != null;
    }

    public static BlockPos getSelectedDisplayBoardPos() {
        return selectedDisplayBoardPos;
    }

    public static void setSelectedDisplayBoard(Level level, BlockPos pos) {
        selectedDisplayBoardPos = pos.immutable();
        selectedDisplayBoardBounds = resolveSelectionBounds(level, pos);
    }

    public static boolean isSelectedDisplayBoard(BlockPos pos) {
        return selectedDisplayBoardPos != null && selectedDisplayBoardPos.equals(pos);
    }

    public static void clearSelection() {
        selectedDisplayBoardPos = null;
        selectedDisplayBoardBounds = null;
        hoveredDisplayLinkPos = null;
        statusUpdateCounter = 0;
        Outliner.getInstance().remove(DISPLAY_BOARD_HIGHLIGHT);
        Outliner.getInstance().remove(DISPLAY_LINK_HIGHLIGHT);
        Outliner.getInstance().remove(DISPLAY_BOARD_TARGET);
        Outliner.getInstance().remove(DISPLAY_LINK_TARGET);
    }

    public static void clearHoverPreview() {
        hoveredDisplayLinkPos = null;
        Outliner.getInstance().remove(DISPLAY_BOARD_TARGET);
        Outliner.getInstance().remove(DISPLAY_LINK_TARGET);
    }

    public static void renderSelection(Minecraft mc) {
        if (mc.level == null || !hasSelection()) {
            clearHoverPreview();
            Outliner.getInstance().remove(DISPLAY_BOARD_HIGHLIGHT);
            Outliner.getInstance().remove(DISPLAY_LINK_HIGHLIGHT);
            return;
        }

        if (selectedDisplayBoardBounds != null) {
            Outliner.getInstance()
                .showAABB(DISPLAY_BOARD_HIGHLIGHT, selectedDisplayBoardBounds)
                .colored(0xDDC166)
                .lineWidth(0.0625F);
        } else {
            Outliner.getInstance().remove(DISPLAY_BOARD_HIGHLIGHT);
        }

        Outliner.getInstance().remove(DISPLAY_LINK_HIGHLIGHT);

        updateHoveredTargets(mc);

        if (hoveredDisplayLinkPos != null) {
            boolean connected = isDisplayLinkConnectedToBoard(hoveredDisplayLinkPos, mc.level, selectedDisplayBoardPos, selectedDisplayBoardBounds);
            boolean outOfRange = isDisplayLinkOutOfRange(hoveredDisplayLinkPos);
            int color = connected || outOfRange ? 0xFF6171 : 0x708DAD;
            renderDisplayLinkOutline(mc.level, hoveredDisplayLinkPos, DISPLAY_LINK_TARGET, color);
            updateHoverStatus(mc, connected, outOfRange);
        } else {
            Outliner.getInstance().remove(DISPLAY_LINK_TARGET);
            statusUpdateCounter = 0;
        }
        Outliner.getInstance().remove(DISPLAY_BOARD_TARGET);
    }

    public static boolean isDisplayLinkConnectedToSelectedBoard(BlockPos displayLinkPos, Level level) {
        return isDisplayLinkConnectedToBoard(displayLinkPos, level, selectedDisplayBoardPos, selectedDisplayBoardBounds);
    }

    public static boolean isDisplayLinkOutOfRange(BlockPos displayLinkPos) {
        return selectedDisplayBoardPos != null
            && !selectedDisplayBoardPos.closerThan(displayLinkPos, AllConfigs.server().logistics.displayLinkRange.get());
    }

    private static void updateHoveredTargets(Minecraft mc) {
        hoveredDisplayLinkPos = null;

        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (selectedDisplayBoardPos != null && AllBlocks.DISPLAY_LINK.has(state)) {
            hoveredDisplayLinkPos = pos;
        }
    }

    private static void renderDisplayLinkOutline(Level level, BlockPos pos, String key, int color) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            Outliner.getInstance().remove(key);
            return;
        }

        Outliner.getInstance()
            .showAABB(key, shape.bounds().move(pos))
            .colored(color)
            .lineWidth(0.0625F);
    }

    private static AABB resolveSelectionBounds(Level level, BlockPos pos) {
        if (level == null) {
            return null;
        }

        DisplayTarget target = DisplayTarget.get(level, pos);
        if (target != null) {
            return target.getMultiblockBounds(level, pos);
        }

        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (!shape.isEmpty()) {
            return shape.bounds().move(pos);
        }

        return new AABB(pos);
    }

    private static boolean isDisplayLinkConnectedToBoard(BlockPos displayLinkPos, Level level, BlockPos displayBoardPos, AABB displayBoardBounds) {
        if (displayLinkPos == null || level == null || displayBoardPos == null) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(displayLinkPos);
        if (!(be instanceof DisplayLinkBlockEntity displayLink)) {
            return false;
        }

        BlockPos connectedTargetPos = displayLink.getTargetPosition();
        if (connectedTargetPos == null) {
            return false;
        }

        if (connectedTargetPos.equals(displayBoardPos)) {
            return true;
        }

        return displayBoardBounds != null && displayBoardBounds.contains(Vec3.atCenterOf(connectedTargetPos));
    }

    private static void updateHoverStatus(Minecraft mc, boolean alreadyConnected, boolean outOfRange) {
        if (mc.player == null || hoveredDisplayLinkPos == null) {
            return;
        }

        statusUpdateCounter++;
        if (statusUpdateCounter < 5) {
            return;
        }
        statusUpdateCounter = 0;

        String key;
        if (alreadyConnected) {
            key = "fluidlogistics.hand_pointer.display_link.already_connected";
        } else if (outOfRange) {
            key = "fluidlogistics.hand_pointer.too_far";
        } else {
            key = "fluidlogistics.hand_pointer.display_link.can_connect";
        }

        CreateLang.builder()
            .translate(key)
            .color(alreadyConnected || outOfRange ? STATUS_INVALID_COLOR : STATUS_CONNECTABLE_COLOR)
            .sendStatus(mc.player);
    }
}
