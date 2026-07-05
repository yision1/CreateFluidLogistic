package com.yision.fluidlogistics.content.equipment.handPointer.client;

import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunTargetConfig;
import com.yision.fluidlogistics.content.fluids.fluidHatch.FluidHatchFluidHandlerForwarder;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.network.MechanicalFluidGunPackets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MechanicalFluidGunSelectionHandler {

    private static final String GUN_HIGHLIGHT = "HandPointerMechanicalFluidGunHighlight";
    private static final int GUN_HIGHLIGHT_COLOR = 0x7FCDE0;
    private static final int TARGET_HIGHLIGHT_COLOR = 0xDDC166;

    public record SubmitResult(boolean success, int sentCount, int skippedCount) {
    }

    private static BlockPos selectedGunPos;
    private static final List<MechanicalFluidGunPackets.TargetPacket.TargetEntry> targets = new ArrayList<>();

    private MechanicalFluidGunSelectionHandler() {
    }

    public static void enterMode(Level level, BlockPos pos) {
        selectedGunPos = pos.immutable();
        targets.clear();

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MechanicalFluidGunBlockEntity gun && gun.hasTarget()) {
            for (MechanicalFluidGunTargetConfig target : gun.getTargets()) {
                targets.add(new MechanicalFluidGunPackets.TargetPacket.TargetEntry(target.absoluteFrom(pos).immutable(), target.face()));
            }
        }
    }

    public static boolean isSelectedGun(BlockPos pos) {
        return selectedGunPos != null && selectedGunPos.equals(pos);
    }

    public static boolean isTargetCandidate(Level level, BlockPos pos) {
        return isValidCandidate(level, pos);
    }

    public static boolean setTarget(Level level, BlockPos pos, @Nullable Direction face) {
        if (!isValidCandidate(level, pos)) {
            return false;
        }

        Iterator<MechanicalFluidGunPackets.TargetPacket.TargetEntry> iterator = targets.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().pos().equals(pos)) {
                iterator.remove();
                clearTargetOutline(pos);
                return true;
            }
        }

        if (targets.size() >= MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS) {
            return false;
        }

        targets.add(new MechanicalFluidGunPackets.TargetPacket.TargetEntry(pos.immutable(), getTargetFace(level, pos, face)));
        return true;
    }

    public static boolean isTargetSelected(BlockPos pos) {
        for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
            if (target.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public static SubmitResult submit(Level level) {
        if (selectedGunPos == null) {
            return new SubmitResult(false, 0, 0);
        }

        if (targets.isEmpty()) {
            FluidLogisticsPackets.getChannel().sendToServer(MechanicalFluidGunPackets.TargetPacket.clearTarget(selectedGunPos));
            return new SubmitResult(true, 0, 0);
        }

        List<MechanicalFluidGunPackets.TargetPacket.TargetEntry> inRange = new ArrayList<>();
        int skipped = 0;

        for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
            if (!isValidCandidate(level, target.pos())) {
                skipped++;
                continue;
            }
            if (!MechanicalFluidGunBlock.isTargetInRange(selectedGunPos, target.pos())) {
                skipped++;
                continue;
            }
            inRange.add(target);
        }

        if (inRange.isEmpty()) {
            return new SubmitResult(false, 0, skipped);
        }

        FluidLogisticsPackets.getChannel().sendToServer(MechanicalFluidGunPackets.TargetPacket.setTargets(selectedGunPos, List.copyOf(inRange)));
        return new SubmitResult(true, inRange.size(), skipped);
    }

    public static boolean clearTarget() {
        if (selectedGunPos == null) {
            return false;
        }

        FluidLogisticsPackets.getChannel().sendToServer(MechanicalFluidGunPackets.TargetPacket.clearTarget(selectedGunPos));
        clearTargetOutlines();
        targets.clear();
        return true;
    }

    public static int getTargetCount() {
        return targets.size();
    }

    public static void clearSelection() {
        selectedGunPos = null;
        clearHoverPreview();
        targets.clear();
        Outliner.getInstance().remove(GUN_HIGHLIGHT);
    }

    public static void clearHoverPreview() {
        clearTargetOutlines();
    }

    public static void renderSelection(Minecraft mc) {
        if (mc.level == null || selectedGunPos == null) {
            clearSelection();
            return;
        }

        BlockState gunState = mc.level.getBlockState(selectedGunPos);
        VoxelShape gunShape = gunState.getShape(mc.level, selectedGunPos);
        if (!gunShape.isEmpty()) {
            Outliner.getInstance()
                .showAABB(GUN_HIGHLIGHT, gunShape.bounds().move(selectedGunPos))
                .colored(GUN_HIGHLIGHT_COLOR)
                .lineWidth(0.0625F);
        }

        for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
            if (!isValidCandidate(mc.level, target.pos())) {
                continue;
            }

            BlockPos pos = target.pos();
            BlockState state = mc.level.getBlockState(pos);
            VoxelShape shape = state.getShape(mc.level, pos);
            if (shape.isEmpty()) {
                continue;
            }

            Outliner.getInstance()
                .showAABB(targetSlot(pos), shape.bounds().move(pos))
                .colored(TARGET_HIGHLIGHT_COLOR)
                .lineWidth(0.0625F);
        }
    }

    private static boolean isValidCandidate(Level level, BlockPos pos) {
        if (selectedGunPos == null || pos == null) {
            return false;
        }
        return MechanicalFluidGunBlock.isSelectableCandidate(level, selectedGunPos, pos);
    }

    private static @Nullable Direction getTargetFace(Level level, BlockPos pos, @Nullable Direction clickedFace) {
        Direction hatchSide = FluidHatchFluidHandlerForwarder.getExposedSide(level.getBlockState(pos));
        return hatchSide == null ? clickedFace : hatchSide;
    }

    private static String targetSlot(BlockPos pos) {
        return "HandPointerMechanicalFluidGunTarget_" + pos.asLong();
    }

    private static void clearTargetOutline(BlockPos pos) {
        Outliner.getInstance().remove(targetSlot(pos));
    }

    private static void clearTargetOutlines() {
        for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
            clearTargetOutline(target.pos());
        }
    }
}
