package com.yision.fluidlogistics.content.equipment.handPointer.client;

import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.api.handpointer.crafter.HandPointerCrafterAdapters;
import com.yision.fluidlogistics.content.equipment.handPointer.MechanicalCrafterConnectionPlanner;
import com.yision.fluidlogistics.content.equipment.handPointer.MechanicalCrafterConnectionPlanner.Plan;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerCrafterConnectionPacket;

import org.jetbrains.annotations.Nullable;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class MechanicalCrafterSelectionHandler {

    private static final String REGION_OUTLINE = "HandPointerCrafterRegion";
    private static final int VALID_COLOR = 0x9EDE73;
    private static final int INVALID_COLOR = 0xFF7171;
    private static final float LINE_WIDTH = 1 / 16F;

    private static BlockPos origin;
    private static int statusUpdateCounter;

    private MechanicalCrafterSelectionHandler() {
    }

    public static void enterMode(BlockPos pos) {
        origin = pos.immutable();
        statusUpdateCounter = 0;
    }

    public static boolean isCrafter(Level level, BlockPos pos) {
        return level != null && HandPointerCrafterAdapters.find(level, pos).isPresent();
    }

    @Nullable
    public static Plan clickTerminal(Level level, BlockPos pos) {
        if (origin == null) {
            return null;
        }

        Plan plan = MechanicalCrafterConnectionPlanner.inspect(level, origin, pos);
        if (!plan.valid()) {
            return null;
        }
        if (!MechanicalCrafterConnectionPlanner.isWithinSelectionRange(
            plan.geometry().origin(), plan.geometry().terminal())) {
            return null;
        }

        HandPointerCrafterConnectionPacket.send(plan);
        return plan;
    }

    public static void renderSelection(Minecraft minecraft) {
        Level level = minecraft.level;
        if (level == null || origin == null) {
            clearOutlines();
            return;
        }

        BlockPos target = hoveredCrafter(minecraft, level);
        if (target == null) {
            statusUpdateCounter = 0;
            Outliner.getInstance().remove(REGION_OUTLINE);
            return;
        }

        Plan plan = MechanicalCrafterConnectionPlanner.inspect(level, origin, target);
        boolean valid = plan.valid() && MechanicalCrafterConnectionPlanner.isWithinSelectionRange(
            plan.geometry().origin(), plan.geometry().terminal());
        int color = valid ? VALID_COLOR : INVALID_COLOR;
        BlockPos min = plan.geometry().min();
        BlockPos max = plan.geometry().max();
        AABB region = new AABB(
            min.getX(), min.getY(), min.getZ(),
            max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        Outliner.getInstance()
            .showAABB(REGION_OUTLINE, region)
            .colored(color)
            .lineWidth(LINE_WIDTH);
        updateHoverStatus(minecraft, valid, plan.willConnect());
    }

    public static void clearSelection() {
        origin = null;
        statusUpdateCounter = 0;
        clearOutlines();
    }

    public static void clearHoverPreview() {
        Outliner.getInstance().remove(REGION_OUTLINE);
    }

    private static BlockPos hoveredCrafter(Minecraft minecraft, Level level) {
        if (!(minecraft.hitResult instanceof BlockHitResult hitResult)
            || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hitResult.getBlockPos();
        return isCrafter(level, pos) ? pos : null;
    }

    private static void updateHoverStatus(Minecraft minecraft, boolean valid, boolean willConnect) {
        if (minecraft.player == null) {
            return;
        }

        statusUpdateCounter++;
        if (statusUpdateCounter < 5) {
            return;
        }
        statusUpdateCounter = 0;

        String key = !valid
            ? "fluidlogistics.hand_pointer.crafter.cannot_connect"
            : willConnect
                ? "fluidlogistics.hand_pointer.crafter.can_connect"
                : "fluidlogistics.hand_pointer.crafter.can_disconnect";
        CreateLang.builder()
            .translate(key)
            .color(valid ? VALID_COLOR : INVALID_COLOR)
            .sendStatus(minecraft.player);
    }

    private static void clearOutlines() {
        Outliner.getInstance().remove(REGION_OUTLINE);
    }
}
