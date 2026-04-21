package com.yision.fluidlogistics.client.handpointer;

import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.content.logistics.depot.EjectorPlacementPacket;
import com.simibubi.create.content.logistics.depot.EntityLauncher;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class DepotSelectionHandler {
    private static final String EJECTOR_HIGHLIGHT = "HandPointerEjectorHighlight";
    private static final String EJECTOR_TARGET = "HandPointerEjectorTarget";
    private static final String EJECTOR_INVALID_TARGET = "HandPointerEjectorInvalidTarget";
    private static final int SELECTED_COLOR = 0xDDC166;
    private static final int VALID_COLOR = 0x9EF173;
    private static final int INVALID_COLOR = 0xFF6171;

    private static BlockPos selectedEjectorPos;
    private static BlockPos ejectorTargetPos;
    private static EntityLauncher launcher;

    private DepotSelectionHandler() {
    }

    public static void enterMode(Level level, BlockPos pos) {
        selectedEjectorPos = pos.immutable();
        launcher = null;

        if (level.getBlockEntity(pos) instanceof EjectorBlockEntity ejector) {
            BlockPos currentTarget = ejector.getTargetPosition();
            ejectorTargetPos = currentTarget.equals(pos) ? null : currentTarget.immutable();
        } else {
            ejectorTargetPos = null;
        }
    }

    public static boolean hasSelection() {
        return selectedEjectorPos != null;
    }

    public static boolean isSelectedEjector(BlockPos pos) {
        return selectedEjectorPos != null && selectedEjectorPos.equals(pos);
    }

    public static BlockPos getTargetPos() {
        return ejectorTargetPos;
    }

    public static void setTarget(BlockPos pos) {
        ejectorTargetPos = pos == null ? null : pos.immutable();
        launcher = null;
    }

    public static void clearSelection() {
        selectedEjectorPos = null;
        ejectorTargetPos = null;
        launcher = null;
        clearHoverPreview();
        Outliner.getInstance().remove(EJECTOR_HIGHLIGHT);
    }

    public static void clearHoverPreview() {
        Outliner.getInstance().remove(EJECTOR_TARGET);
        Outliner.getInstance().remove(EJECTOR_INVALID_TARGET);
    }

    public record EjectorTargetValidation(boolean valid, @Nullable Direction facing, int horizontalDistance,
                                           int verticalDistance) {
    }

    public static EjectorTargetValidation validateTarget(BlockPos ejectorPos, BlockPos targetPos) {
        if (ejectorPos == null || targetPos == null || ejectorPos.equals(targetPos)) {
            return new EjectorTargetValidation(false, null, 0, 0);
        }

        int xDiff = targetPos.getX() - ejectorPos.getX();
        int zDiff = targetPos.getZ() - ejectorPos.getZ();
        int yDiff = targetPos.getY() - ejectorPos.getY();
        int max = AllConfigs.server().kinetics.maxEjectorDistance.get();

        if ((xDiff == 0 && zDiff == 0) || (xDiff != 0 && zDiff != 0)) {
            return new EjectorTargetValidation(false, null, 0, yDiff);
        }

        int horizontalDistance = Math.max(Math.abs(xDiff), Math.abs(zDiff));
        if (horizontalDistance > max) {
            return new EjectorTargetValidation(false, null, horizontalDistance, yDiff);
        }

        Direction facing = xDiff == 0
            ? Direction.get(zDiff < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE, Direction.Axis.Z)
            : Direction.get(xDiff < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE, Direction.Axis.X);
        return new EjectorTargetValidation(true, facing, horizontalDistance, yDiff);
    }

    public static boolean submit() {
        if (selectedEjectorPos == null || ejectorTargetPos == null) {
            return false;
        }

        EjectorTargetValidation validation = validateTarget(selectedEjectorPos, ejectorTargetPos);
        if (!validation.valid() || validation.facing() == null) {
            return false;
        }

        CatnipServices.NETWORK.sendToServer(new EjectorPlacementPacket(
            validation.horizontalDistance(),
            validation.verticalDistance(),
            selectedEjectorPos,
            validation.facing()
        ));
        return true;
    }

    public static void renderSelection(Minecraft mc) {
        if (mc.level == null || selectedEjectorPos == null) {
            clearHoverPreview();
            Outliner.getInstance().remove(EJECTOR_HIGHLIGHT);
            return;
        }

        renderOutline(mc.level, selectedEjectorPos, EJECTOR_HIGHLIGHT, SELECTED_COLOR);

        if (ejectorTargetPos == null) {
            clearHoverPreview();
            return;
        }

        EjectorTargetValidation validation = validateTarget(selectedEjectorPos, ejectorTargetPos);
        renderOutline(
            mc.level,
            ejectorTargetPos,
            validation.valid() ? EJECTOR_TARGET : EJECTOR_INVALID_TARGET,
            validation.valid() ? VALID_COLOR : INVALID_COLOR
        );
        if (validation.valid()) {
            Outliner.getInstance().remove(EJECTOR_INVALID_TARGET);
        } else {
            Outliner.getInstance().remove(EJECTOR_TARGET);
        }

        drawArc(mc, validation);
    }

    private static void renderOutline(Level level, BlockPos pos, String slot, int color) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            Outliner.getInstance().remove(slot);
            return;
        }
        Outliner.getInstance().showAABB(slot, shape.bounds().move(pos)).colored(color).lineWidth(0.0625F);
    }

    private static void drawArc(Minecraft mc, EjectorTargetValidation validation) {
        if (selectedEjectorPos == null || ejectorTargetPos == null || mc.level == null) {
            return;
        }

        int xDiff = ejectorTargetPos.getX() - selectedEjectorPos.getX();
        int zDiff = ejectorTargetPos.getZ() - selectedEjectorPos.getZ();
        int yDiff = ejectorTargetPos.getY() - selectedEjectorPos.getY();

        int validX = Math.abs(zDiff) > Math.abs(xDiff) ? 0 : xDiff;
        int validZ = Math.abs(zDiff) < Math.abs(xDiff) ? 0 : zDiff;
        BlockPos validPos = ejectorTargetPos.offset(validX, yDiff, validZ);
        EjectorTargetValidation renderValidation = validateTarget(validPos, ejectorTargetPos);
        if (!renderValidation.valid() || renderValidation.facing() == null) {
            return;
        }

        int hDist = Math.max(1, Math.abs(validX + validZ));
        if (launcher == null || launcher.getHorizontalDistance() != hDist
            || launcher.getVerticalDistance() != yDiff) {
            launcher = new EntityLauncher(hDist, yDiff);
        }

        double totalFlyingTicks = launcher.getTotalFlyingTicks() + 3.0;
        int segments = (int) (totalFlyingTicks / 3) + 1;
        double tickOffset = totalFlyingTicks / segments;
        int color = validation.valid() ? VALID_COLOR : INVALID_COLOR;
        Vector3f colorVec = new Color(color).asVectorF();
        DustParticleOptions dust = new DustParticleOptions(colorVec, 1.0F);
        ClientLevel world = mc.level;

        for (int i = 0; i < segments; i++) {
            double ticks = (AnimationTickHolder.getRenderTime() / 3.0F) % tickOffset + i * tickOffset;
            Vec3 vec = launcher.getGlobalPos(ticks, renderValidation.facing(), selectedEjectorPos)
                .add(xDiff - validX, 0, zDiff - validZ);
            world.addParticle(dust, vec.x, vec.y, vec.z, 0, 0, 0);
        }
    }
}
