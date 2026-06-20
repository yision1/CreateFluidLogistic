package com.yision.fluidlogistics.client.handpointer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.google.common.cache.Cache;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget.ChainConveyorFrogportTarget;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.yision.fluidlogistics.mixin.accessor.FrogportChainConveyorOBBAccessor;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.network.HandPointerFrogportConnectionPacket;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FrogportSelectionHandler {

    private static final int MAX_FROGPORTS = 5;

    private static final String CHAIN_POINT_OUTLINE = "HandPointerChainPointSelection";
    private static final String FROGPORT_TARGET = "HandPointerFrogportTarget";
    private static final String FROGPORT_HIGHLIGHT_PREFIX = "HandPointerFrogportHighlight_";

    private static final Color CHAIN_SELECTION_COLOR = new Color(0xFFFFFF);
    private static final Color FROGPORT_HIGHLIGHT_COLOR = new Color(0xDDC166);
    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;

    private static final List<BlockPos> selectedFrogports = new ArrayList<>();
    private static int statusUpdateCounter;

    public static boolean hasSelection() {
        return !selectedFrogports.isEmpty();
    }

    public static BlockPos getSelectedFrogportPos() {
        return hasSelection() ? selectedFrogports.get(0) : null;
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
        if (selectedFrogports.size() >= MAX_FROGPORTS) {
            return false;
        }

        selectedFrogports.add(pos);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f, false);

        CreateLang.builder()
            .translate("fluidlogistics.hand_pointer.frogport.added", selectedFrogports.size(), MAX_FROGPORTS)
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

    private static void resetChainTargetState() {
        ChainConveyorInteractionHandler.selectedLift = null;
        ChainConveyorInteractionHandler.selectedShape = null;
        ChainConveyorInteractionHandler.selectedChainPosition = 0f;
        ChainConveyorInteractionHandler.selectedConnection = null;
        ChainConveyorInteractionHandler.selectedBakedPosition = null;
    }

    public static void clearSelection() {
        removeFrogportHighlights();
        selectedFrogports.clear();
        statusUpdateCounter = 0;
        resetChainTargetState();
        Outliner.getInstance().remove(CHAIN_POINT_OUTLINE);
        Outliner.getInstance().remove(FROGPORT_TARGET);
    }

    public static void clearHoverPreview() {
        Outliner.getInstance().remove(FROGPORT_TARGET);
    }

    public static boolean isFrogport(Level level, BlockPos pos) {
        return AllBlocks.PACKAGE_FROGPORT.has(level.getBlockState(pos));
    }

    public static void tickChainTarget(Minecraft mc) {
        if (mc.level == null || mc.player == null || !hasSelection()) {
            resetChainTargetState();
            Outliner.getInstance().remove(CHAIN_POINT_OUTLINE);
            return;
        }

        double range = mc.gameMode == null ? 6 : mc.gameMode.getPickRange() + 1;
        Vec3 from = mc.player.getEyePosition();
        HitResult hitResult = mc.hitResult;

        double bestDiff;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            bestDiff = hitResult.getLocation().distanceToSqr(from);
        } else {
            bestDiff = range * range;
        }

        BlockPos bestLift = null;
        ChainConveyorShape bestShape = null;
        float bestChainPosition = 0f;
        BlockPos bestConnection = null;

        Cache<BlockPos, List<ChainConveyorShape>> loadedChains = ChainConveyorInteractionHandler.loadedChains.get(mc.level);
        if (loadedChains == null) {
            resetChainTargetState();
            Outliner.getInstance().remove(CHAIN_POINT_OUTLINE);
            return;
        }

        for (Entry<BlockPos, List<ChainConveyorShape>> entry : loadedChains.asMap().entrySet()) {
            BlockPos liftPos = entry.getKey();
            for (ChainConveyorShape chainConveyorShape : entry.getValue()) {
                Vec3 liftVec = Vec3.atLowerCornerOf(liftPos);
                Vec3 intersect = chainConveyorShape.intersect(from.subtract(liftVec),
                    hitResult == null ? from : hitResult.getLocation().subtract(liftVec));
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
            return;
        }

        ChainConveyorInteractionHandler.selectedBakedPosition = bestShape.getVec(bestLift, bestChainPosition);
        Outliner.getInstance()
            .chaseAABB(CHAIN_POINT_OUTLINE,
                new AABB(ChainConveyorInteractionHandler.selectedBakedPosition, ChainConveyorInteractionHandler.selectedBakedPosition))
            .colored(CHAIN_SELECTION_COLOR.getRGB())
            .lineWidth(1 / 6f)
            .disableLineNormals();
    }

    public static void renderSelection(Minecraft mc) {
        if (mc.level == null || !hasSelection()) {
            removeFrogportHighlights();
            return;
        }

        for (BlockPos frogportPos : selectedFrogports) {
            BlockState state = mc.level.getBlockState(frogportPos);
            VoxelShape shape = state.getShape(mc.level, frogportPos);
            if (!shape.isEmpty()) {
                Outliner.getInstance()
                    .showAABB(highlightKey(frogportPos), shape.bounds().move(frogportPos))
                    .colored(FROGPORT_HIGHLIGHT_COLOR.getRGB())
                    .lineWidth(0.0625F);
            }
        }

        if (ChainConveyorInteractionHandler.selectedBakedPosition != null) {
            boolean outOfRange = isCurrentTargetOutOfRange(mc.level);
            Color color = outOfRange ? new Color(0xFF6171) : new Color(0x9EF173);
            for (BlockPos frogportPos : selectedFrogports) {
                animateConnection(mc, Vec3.atCenterOf(frogportPos),
                    ChainConveyorInteractionHandler.selectedBakedPosition, color);
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
        if (!AllBlocks.PACKAGE_FROGPORT.has(mc.level.getBlockState(pos))) {
            clearHoverPreview();
            return;
        }

        if (!(mc.level.getBlockEntity(pos) instanceof PackagePortBlockEntity port)) {
            clearHoverPreview();
            return;
        }

        PackagePortTarget target = port.target;
        if (!(target instanceof ChainConveyorFrogportTarget)) {
            clearHoverPreview();
            return;
        }

        Vec3 targetLocation = target.getExactTargetLocation(port, mc.level, pos);
        if (targetLocation == null || targetLocation == Vec3.ZERO) {
            clearHoverPreview();
            return;
        }

        Outliner.getInstance()
            .chaseAABB(FROGPORT_TARGET, new AABB(targetLocation, targetLocation))
            .colored(0x9EF173)
            .lineWidth(1 / 6f)
            .disableLineNormals();

        animateConnection(mc, Vec3.atCenterOf(pos), targetLocation, new Color(0x9EF173));
    }

    public static boolean tryConnectCurrentTarget(Level level) {
        if (!hasSelection() || ChainConveyorInteractionHandler.selectedLift == null) {
            return false;
        }

        BlockPos liftPos = ChainConveyorInteractionHandler.selectedLift;
        float chainPos = ChainConveyorInteractionHandler.selectedChainPosition;
        BlockPos connection = ChainConveyorInteractionHandler.selectedConnection;

        if (isCurrentTargetOutOfRange(level)) {
            return false;
        }

        FluidLogisticsPackets.getChannel()
            .sendToServer(new HandPointerFrogportConnectionPacket(selectedFrogports, liftPos, chainPos, connection));
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
            ChainConveyorFrogportTarget previewTarget = new ChainConveyorFrogportTarget(
                liftPos.subtract(frogportPos),
                chainPos,
                connection);

            Vec3 targetLocation = previewTarget.getExactTargetLocation(null, level, frogportPos);
            if (targetLocation == Vec3.ZERO || !targetLocation.closerThan(
                Vec3.atBottomCenterOf(frogportPos),
                AllConfigs.server().logistics.packagePortRange.get())) {
                return true;
            }
        }

        return false;
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
