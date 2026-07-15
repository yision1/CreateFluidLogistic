package com.yision.fluidlogistics.content.equipment.handPointer.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.cache.Cache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperChainConveyorFrogportTarget;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperFrogportBlock;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperFrogportBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.RaycastHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerFrogportConnectionPacket;
import com.yision.fluidlogistics.mixin.accessor.FrogportChainConveyorOBBAccessor;

import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FrogportSelectionHandler {

    private static final String CHAIN_POINT_OUTLINE = "HandPointerChainPointSelection";
    private static final String FROGPORT_HIGHLIGHT_PREFIX = "HandPointerFrogportHighlight_";

    private static final Color CHAIN_SELECTION_COLOR = new Color(0xFFFFFF);
    private static final Color FROGPORT_HIGHLIGHT_COLOR = new Color(0xDDC166);
    private static final Color VALID_CONNECTION_COLOR = new Color(0x9EDE73);
    private static final Color INVALID_CONNECTION_COLOR = new Color(0xFF7171);
    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;

    private static final List<BlockPos> selectedFrogports = new ArrayList<>();
    private static int statusUpdateCounter;

    public static boolean hasSelection() {
        return !selectedFrogports.isEmpty();
    }

    public static BlockPos getSelectedFrogportPos() {
        return hasSelection() ? selectedFrogports.getFirst() : null;
    }

    public static boolean isFrogportSelected(BlockPos pos) {
        return selectedFrogports.contains(pos);
    }

    public static void setSelection(BlockPos frogportPos) {
        selectedFrogports.clear();
        selectedFrogports.add(frogportPos);
        statusUpdateCounter = 0;
        resetChainTargetState();
    }

    public static boolean addFrogport(BlockPos pos, Player player, Level level) {
        int maxFrogports = Config.getHandPointerMaxFrogports();
        if (selectedFrogports.size() >= maxFrogports) {
            return false;
        }

        selectedFrogports.add(pos);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f, false);

        CreateLang.builder()
            .translate("fluidlogistics.hand_pointer.frogport.added", selectedFrogports.size(), maxFrogports)
            .color(0xDDC166)
            .sendStatus(player);
        return true;
    }

    public static void removeFrogport(BlockPos pos, Player player, Level level) {
        if (!selectedFrogports.remove(pos)) {
            return;
        }

        removeFrogportHighlight(pos);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);

        if (hasSelection()) {
            CreateLang.builder()
                .translate("fluidlogistics.hand_pointer.frogport.removed")
                .color(0xA5A5A5)
                .sendStatus(player);
        }
    }

    public static void clearSelection() {
        removeFrogportHighlights();
        selectedFrogports.clear();
        statusUpdateCounter = 0;
        resetChainTargetState();
        Outliner.getInstance().remove(CHAIN_POINT_OUTLINE);
    }

    public static boolean isFrogport(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FrogportBlockEntity;
    }

    public static boolean refreshChainTargetFromRaycast(Minecraft mc) {
        if (mc.level == null || mc.player == null || !hasSelection()) {
            resetChainTargetState();
            Outliner.getInstance().remove(CHAIN_POINT_OUTLINE);
            return false;
        }

        double range = mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        Vec3 from = mc.player.getEyePosition();
        Vec3 to = RaycastHelper.getTraceTarget(mc.player, range, from);
        HitResult hitResult = mc.hitResult;

        double bestDiff = Float.MAX_VALUE;
        if (hitResult != null) {
            bestDiff = hitResult.getLocation().distanceToSqr(from);
        }

        BlockPos bestLift = null;
        ChainConveyorShape bestShape = null;
        float bestChainPosition = 0f;
        BlockPos bestConnection = null;

        Cache<BlockPos, List<ChainConveyorShape>> loadedChains = ChainConveyorInteractionHandler.loadedChains.get(mc.level);
        if (loadedChains == null) {
            resetChainTargetState();
            Outliner.getInstance().remove(CHAIN_POINT_OUTLINE);
            return false;
        }

        for (Entry<BlockPos, List<ChainConveyorShape>> entry : loadedChains.asMap().entrySet()) {
            BlockPos liftPos = entry.getKey();
            for (ChainConveyorShape chainConveyorShape : entry.getValue()) {
                Vec3 liftVec = Vec3.atLowerCornerOf(liftPos);
                Vec3 intersect = chainConveyorShape.intersect(from.subtract(liftVec), to.subtract(liftVec));
                if (intersect == null) {
                    continue;
                }
                double distanceToSqr = intersect.add(liftVec).distanceToSqr(from);
                if (distanceToSqr > bestDiff) {
                    continue;
                }
                bestDiff = distanceToSqr;
                bestLift = liftPos;
                bestShape = chainConveyorShape;
                bestChainPosition = chainConveyorShape.getChainPosition(intersect);
                if (chainConveyorShape instanceof ChainConveyorShape.ChainConveyorOBB obb) {
                    bestConnection = ((FrogportChainConveyorOBBAccessor) obb).fluidlogistics$getConnection();
                } else {
                    bestConnection = null;
                }
            }
        }

        ChainConveyorInteractionHandler.selectedLift = bestLift;
        ChainConveyorInteractionHandler.selectedShape = bestShape;
        ChainConveyorInteractionHandler.selectedChainPosition = bestChainPosition;
        ChainConveyorInteractionHandler.selectedConnection = bestConnection;

        if (bestLift == null || bestShape == null) {
            ChainConveyorInteractionHandler.selectedBakedPosition = null;
            Outliner.getInstance().remove(CHAIN_POINT_OUTLINE);
            return false;
        }

        ChainConveyorInteractionHandler.selectedBakedPosition = bestShape.getVec(bestLift, bestChainPosition);
        Outliner.getInstance()
            .chaseAABB(CHAIN_POINT_OUTLINE,
                new AABB(ChainConveyorInteractionHandler.selectedBakedPosition, ChainConveyorInteractionHandler.selectedBakedPosition))
            .colored(CHAIN_SELECTION_COLOR.getRGB())
            .lineWidth(1 / 6f)
            .disableLineNormals();
        return true;
    }

    public static void tickChainTarget(Minecraft mc) {
        refreshChainTargetFromRaycast(mc);
    }

    public static void drawChainContour(PoseStack ms, MultiBufferSource buffer, Vec3 camera) {
        if (ChainConveyorInteractionHandler.selectedLift == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Cache<BlockPos, List<ChainConveyorShape>> loadedChains = ChainConveyorInteractionHandler.loadedChains.get(mc.level);
        if (loadedChains == null) {
            return;
        }

        VertexConsumer vb = buffer.getBuffer(RenderType.lines());
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> toVisit = new ArrayList<>();
        toVisit.add(ChainConveyorInteractionHandler.selectedLift);

        while (!toVisit.isEmpty()) {
            BlockPos current = toVisit.remove(toVisit.size() - 1);
            if (!visited.add(current)) {
                continue;
            }

            List<ChainConveyorShape> shapes = loadedChains.getIfPresent(current);
            if (shapes != null) {
                for (ChainConveyorShape shape : shapes) {
                    ms.pushPose();
                    ms.translate(current.getX() - camera.x, current.getY() - camera.y, current.getZ() - camera.z);
        if (shape instanceof ChainConveyorShape.ChainConveyorOBB obb) {
            obb.drawOutline(current, ms, vb);
        } else if (shape instanceof ChainConveyorShape.ChainConveyorBB bb) {
            bb.drawOutline(current, ms, vb);
        }
                    ms.popPose();
                }
            }

            var beOpt = mc.level.getBlockEntity(current, AllBlockEntityTypes.CHAIN_CONVEYOR.get());
            beOpt.ifPresent(be -> {
                for (BlockPos connection : be.connections) {
                    BlockPos connectedPos = current.offset(connection);
                    if (!visited.contains(connectedPos) && mc.level.getBlockState(connectedPos).is(AllBlocks.CHAIN_CONVEYOR.get())) {
                        toVisit.add(connectedPos);
                    }
                }
            });
        }
    }

    public static void renderSelection(Minecraft mc) {
        if (mc.level == null || !hasSelection()) {
            removeFrogportHighlights();
            return;
        }

        Level level = mc.level;

        for (BlockPos frogportPos : selectedFrogports) {
            BlockState state = level.getBlockState(frogportPos);
            VoxelShape shape = state.getShape(level, frogportPos);
            if (!shape.isEmpty()) {
                Outliner.getInstance()
                    .showAABB(highlightKey(frogportPos), shape.bounds().move(frogportPos))
                    .colored(FROGPORT_HIGHLIGHT_COLOR.getRGB())
                    .lineWidth(0.0625F);
            }
        }

        if (ChainConveyorInteractionHandler.selectedBakedPosition != null) {
            Vec3 end = ChainConveyorInteractionHandler.selectedBakedPosition;
            boolean outOfRange = isCurrentTargetOutOfRange(level);
            Color color = outOfRange ? INVALID_CONNECTION_COLOR : VALID_CONNECTION_COLOR;

            for (BlockPos frogportPos : selectedFrogports) {
                PackagePortTargetSelectionHandler.animateConnection(
                    mc,
                    getConnectionSource(level, frogportPos),
                    end,
                    color
                );
            }
            updateConnectionStatus(mc, outOfRange);
        }
    }

    private static void updateConnectionStatus(Minecraft mc, boolean outOfRange) {
        if (mc.player == null || ChainConveyorInteractionHandler.selectedBakedPosition == null) {
            return;
        }

        statusUpdateCounter++;
        if (statusUpdateCounter < 5) {
            return;
        }
        statusUpdateCounter = 0;

        CreateLang.builder()
            .translate(outOfRange ? "fluidlogistics.hand_pointer.too_far"
                : "fluidlogistics.hand_pointer.frogport.can_connect")
            .color(outOfRange ? STATUS_INVALID_COLOR : STATUS_CONNECTABLE_COLOR)
            .sendStatus(mc.player);
    }

    public static boolean tryConnectCurrentTarget(Level level) {
        if (!hasSelection()) {
            return false;
        }

        if (!refreshChainTargetFromRaycast(Minecraft.getInstance()) || isCurrentTargetOutOfRange(level)) {
            return false;
        }

        HandPointerFrogportConnectionPacket.send(
            selectedFrogports,
            ChainConveyorInteractionHandler.selectedLift,
            ChainConveyorInteractionHandler.selectedChainPosition,
            ChainConveyorInteractionHandler.selectedConnection);
        return true;
    }

    public static boolean isCurrentTargetOutOfRange(Level level) {
        if (!hasSelection() || level == null || ChainConveyorInteractionHandler.selectedLift == null) {
            return false;
        }

        BlockPos liftPos = ChainConveyorInteractionHandler.selectedLift;
        float chainPos = ChainConveyorInteractionHandler.selectedChainPosition;
        BlockPos connection = ChainConveyorInteractionHandler.selectedConnection;

        for (BlockPos frogportPos : selectedFrogports) {
            BlockEntity blockEntity = level.getBlockEntity(frogportPos);
            ChainConveyorFrogportTarget previewTarget = blockEntity instanceof CopperFrogportBlockEntity
                ? new CopperChainConveyorFrogportTarget(
                    liftPos.subtract(frogportPos), chainPos, connection, false)
                : new ChainConveyorFrogportTarget(
                    liftPos.subtract(frogportPos), chainPos, connection, false);
            if (!previewTarget.canSupport(blockEntity)) {
                return true;
            }
            Vec3 targetLocation = previewTarget.getExactTargetLocation(null, level, frogportPos);
            if (targetLocation == Vec3.ZERO || targetLocation.distanceTo(
                Vec3.atBottomCenterOf(frogportPos))
                > AllConfigs.server().logistics.packagePortRange.get()) {
                return true;
            }
        }

        return false;
    }

    public static void playTooFarFeedback(Minecraft mc, BlockPos clickedPos) {
        if (mc.level == null || !hasSelection()) {
            return;
        }
        for (BlockPos frogportPos : selectedFrogports) {
            PackagePortTargetSelectionHandler.animateConnection(
                mc,
                getConnectionSource(mc.level, frogportPos),
                Vec3.atCenterOf(clickedPos),
                INVALID_CONNECTION_COLOR
            );
        }
    }

    private static void resetChainTargetState() {
        ChainConveyorInteractionHandler.selectedLift = null;
        ChainConveyorInteractionHandler.selectedShape = null;
        ChainConveyorInteractionHandler.selectedChainPosition = 0f;
        ChainConveyorInteractionHandler.selectedConnection = null;
        ChainConveyorInteractionHandler.selectedBakedPosition = null;
    }

    private static void removeFrogportHighlights() {
        for (BlockPos frogportPos : selectedFrogports) {
            removeFrogportHighlight(frogportPos);
        }
    }

    private static void removeFrogportHighlight(BlockPos frogportPos) {
        Outliner.getInstance().remove(highlightKey(frogportPos));
    }

    private static String highlightKey(BlockPos frogportPos) {
        return FROGPORT_HIGHLIGHT_PREFIX + frogportPos.asLong();
    }

    private static Vec3 getConnectionSource(Level level, BlockPos frogportPos) {
        BlockState state = level.getBlockState(frogportPos);
        return level.getBlockEntity(frogportPos) instanceof CopperFrogportBlockEntity
            ? CopperFrogportBlock.getConnectionSource(frogportPos, state)
            : Vec3.atBottomCenterOf(frogportPos);
    }
}
