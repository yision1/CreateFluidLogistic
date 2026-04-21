package com.yision.fluidlogistics.client.handpointer;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.mixin.accessor.ArmBlockEntityAccessor;
import com.yision.fluidlogistics.network.HandPointerArmPlacementPacket;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ArmSelectionHandler {

    private static final int MAX_ARMS = 5;

    private static final List<BlockPos> selectedArms = new ArrayList<>();
    private static List<ArmInteractionPoint> currentArmSelection = new ArrayList<>();
    private static BlockPos sourceArmPos;
    private static ListTag originalSelectionTag = new ListTag();
    private static boolean sessionSubmitted;

    public static boolean hasSelection() {
        return !selectedArms.isEmpty();
    }

    public static List<BlockPos> getSelectedArms() {
        return selectedArms;
    }

    public static List<ArmInteractionPoint> getCurrentArmSelection() {
        return currentArmSelection;
    }

    public static void clearSelection(Level level) {
        if (!sessionSubmitted) {
            restoreOriginalSelection(level);
        }
        removeArmHighlights();
        selectedArms.clear();
        currentArmSelection.clear();
        sourceArmPos = null;
        originalSelectionTag = new ListTag();
        sessionSubmitted = false;
    }

    public static boolean isArmSelected(BlockPos pos) {
        return selectedArms.contains(pos);
    }

    public static boolean addArm(BlockPos pos, Player player, Level level) {
        if (isArmSelected(pos)) {
            selectedArms.remove(pos);
            selectedArms.add(pos);
            return true;
        }

        if (selectedArms.size() >= MAX_ARMS) {
            return false;
        }

        selectedArms.add(pos);
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f, false);

        if (selectedArms.size() == 1) {
            loadInteractionPoints(pos, level);
        }

        CreateLang.builder()
            .translate("fluidlogistics.hand_pointer.arm.arm_added", selectedArms.size(), MAX_ARMS)
            .color(0xDDC166)
            .sendStatus(player);
        return true;
    }

    public static void enterArmMode(BlockPos pos, Player player, Level level) {
        selectedArms.clear();
        currentArmSelection.clear();
        selectedArms.add(pos);
        sourceArmPos = pos;
        sessionSubmitted = false;

        loadInteractionPoints(pos, level);
        originalSelectionTag = snapshotSelection(currentArmSelection, pos);

        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f, false);

        showSummary(player, pos);
    }

    private static void loadInteractionPoints(BlockPos pos, Level level) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ArmBlockEntity arm)) {
            return;
        }

        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;
        currentArmSelection.clear();
        currentArmSelection.addAll(accessor.getInputs());
        currentArmSelection.addAll(accessor.getOutputs());
    }

    public static void handlePointInteraction(Level level, BlockPos pos, BlockState state, Player player) {
        ArmInteractionPoint selected = getSelectedPoint(pos);
        if (selected == null) {
            ArmInteractionPoint point = ArmInteractionPoint.create(level, pos, state);
            if (point == null) {
                return;
            }
            selected = point;
            currentArmSelection.add(point);
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.3f, 1.5f, false);
        }

        selected.cycleMode();

        ArmInteractionPoint.Mode mode = selected.getMode();
        CreateLang.builder()
            .translate(mode.getTranslationKey(),
                CreateLang.blockName(state).style(ChatFormatting.WHITE))
            .color(mode.getColor())
            .sendStatus(player);

        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3f, 2.0f, false);
    }

    public static void removePoint(BlockPos pos, Player player, Level level) {
        ArmInteractionPoint point = getSelectedPoint(pos);
        if (point != null) {
            currentArmSelection.remove(point);
            CreateLang.builder()
                .translate("fluidlogistics.hand_pointer.arm.point_removed")
                .style(ChatFormatting.RED)
                .sendStatus(player);
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f, false);
        }
    }

    public static void submitSession(Player player, Level level) {
        int totalRemoved = 0;

        for (BlockPos armPos : selectedArms) {
            List<ArmInteractionPoint> filtered = new ArrayList<>();

            for (ArmInteractionPoint point : currentArmSelection) {
                if (point.getPos().closerThan(armPos, ArmBlockEntity.getRange())) {
                    filtered.add(point);
                } else {
                    totalRemoved++;
                }
            }

            HandPointerArmPlacementPacket.send(filtered, armPos);
        }

        sessionSubmitted = true;

        level.playLocalSound(selectedArms.getFirst().getX() + 0.5,
            selectedArms.getFirst().getY() + 0.5,
            selectedArms.getFirst().getZ() + 0.5,
            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8f, 1.0f, false);

        if (totalRemoved > 0) {
            CreateLang.builder()
                .translate("fluidlogistics.hand_pointer.arm.points_out_of_range", totalRemoved)
                .style(ChatFormatting.RED)
                .sendStatus(player);
        } else {
            CreateLang.builder()
                .translate("fluidlogistics.hand_pointer.arm.submitted")
                .color(0x9EF173)
                .sendStatus(player);
        }
    }

    private static void showSummary(Player player, BlockPos armPos) {
        int inputs = 0;
        int outputs = 0;
        for (ArmInteractionPoint point : currentArmSelection) {
            if (point.getPos().closerThan(armPos, ArmBlockEntity.getRange())) {
                if (point.getMode() == ArmInteractionPoint.Mode.DEPOSIT) {
                    outputs++;
                } else {
                    inputs++;
                }
            }
        }
        if (inputs + outputs > 0) {
            CreateLang.builder()
                .translate("mechanical_arm.summary", inputs, outputs)
                .style(ChatFormatting.WHITE)
                .sendStatus(player);
        }
    }

    private static ArmInteractionPoint getSelectedPoint(BlockPos pos) {
        for (ArmInteractionPoint point : currentArmSelection) {
            if (point.getPos().equals(pos)) {
                return point;
            }
        }
        return null;
    }

    private static ListTag snapshotSelection(List<ArmInteractionPoint> points, BlockPos anchor) {
        ListTag snapshot = new ListTag();
        for (ArmInteractionPoint point : points) {
            snapshot.add(point.serialize(anchor));
        }
        return snapshot;
    }

    private static void restoreOriginalSelection(Level level) {
        if (level == null || sourceArmPos == null || originalSelectionTag.isEmpty()) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(sourceArmPos);
        if (!(blockEntity instanceof ArmBlockEntity arm)) {
            return;
        }

        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;
        List<ArmInteractionPoint> inputs = accessor.getInputs();
        List<ArmInteractionPoint> outputs = accessor.getOutputs();
        inputs.clear();
        outputs.clear();

        for (Tag tag : originalSelectionTag) {
            ArmInteractionPoint point = ArmInteractionPoint.deserialize((CompoundTag) tag, level, sourceArmPos);
            if (point == null) {
                continue;
            }
            if (point.getMode() == ArmInteractionPoint.Mode.DEPOSIT) {
                outputs.add(point);
            } else if (point.getMode() == ArmInteractionPoint.Mode.TAKE) {
                inputs.add(point);
            }
        }
    }

    public static boolean isArmInteractable(BlockEntity be, BlockState state, Level level, BlockPos pos) {
        if (be instanceof ArmBlockEntity) {
            return true;
        }
        return ArmInteractionPoint.isInteractable(level, pos, state);
    }

    public static void renderSelection(Minecraft mc) {
        if (mc.level == null || !hasSelection()) {
            removeArmHighlights();
            return;
        }

        Level level = mc.level;

        for (BlockPos armPos : selectedArms) {
            BlockState armState = level.getBlockState(armPos);
            VoxelShape shape = armState.getShape(level, armPos);
            if (!shape.isEmpty()) {
                Outliner.getInstance()
                    .showAABB("HandPointerArmHighlight_" + armPos.asLong(),
                        shape.bounds().move(armPos))
                    .colored(0xDDC166)
                    .lineWidth(0.0625F);
            }
        }

        Iterator<ArmInteractionPoint> iterator = currentArmSelection.iterator();
        while (iterator.hasNext()) {
            ArmInteractionPoint point = iterator.next();
            if (!point.isValid()) {
                iterator.remove();
                continue;
            }

            BlockPos pos = point.getPos();
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(level, pos);
            if (!shape.isEmpty()) {
                Outliner.getInstance()
                    .showAABB(point, shape.bounds().move(pos))
                    .colored(point.getMode().getColor())
                    .lineWidth(0.0625F);
            }
        }
    }

    private static void removeArmHighlights() {
        for (BlockPos armPos : selectedArms) {
            Outliner.getInstance().remove("HandPointerArmHighlight_" + armPos.asLong());
        }
    }
}
