package com.yision.fluidlogistics.content.equipment.handPointer.client;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetSelectionHandler;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.client.MechanicalFluidGunWrenchTargetHandler;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperFrogportBlock;
import com.yision.fluidlogistics.content.logistics.copperFrogport.CopperFrogportBlockEntity;
import com.yision.fluidlogistics.mixin.accessor.ArmBlockEntityAccessor;

import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
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

import java.util.HashSet;
import java.util.Set;

public final class HandPointerWrenchHoverPreviewHandler {

    private static final String PACKAGE_PORT_TARGET_KEY = "HandPointerWrenchPackagePortTarget";
    private static final String ARM_POINT_KEY_PREFIX = "HandPointerWrenchArmPoint_";

    private static final Color VALID_TARGET_COLOR = new Color(0x9EDE73);
    private static final float POINT_LINE_WIDTH = 1 / 5f;
    private static final float SHAPE_LINE_WIDTH = 1 / 16f;

    private static final Set<String> activeArmPointKeys = new HashSet<>();

    private HandPointerWrenchHoverPreviewHandler() {
    }

    public static boolean render(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.hitResult == null) {
            clear();
            return false;
        }

        if (!(mc.hitResult instanceof BlockHitResult blockHit)
            || blockHit.getType() != HitResult.Type.BLOCK) {
            clear();
            return false;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);

        if (be instanceof PackagePortBlockEntity ppbe) {
            return renderPackagePort(mc, pos, ppbe);
        }
        if (be instanceof ArmBlockEntity arm) {
            renderArm(mc, pos, arm);
            return true;
        }
        if (be instanceof EjectorBlockEntity ejector) {
            return renderEjector(mc, pos, ejector);
        }
        if (be instanceof MechanicalFluidGunBlockEntity gun) {
            return renderMechanicalFluidGun(mc, pos, gun);
        }

        clear();
        return false;
    }

    public static void clear() {
        Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
        DepotSelectionHandler.clearHoveredTargetPreview();
        MechanicalFluidGunWrenchTargetHandler.clear();
        for (String key : activeArmPointKeys) {
            Outliner.getInstance().remove(key);
        }
        activeArmPointKeys.clear();
    }

    private static boolean renderPackagePort(Minecraft mc, BlockPos pos, PackagePortBlockEntity ppbe) {
        clearArmPointKeys();
        DepotSelectionHandler.clearHoveredTargetPreview();
        MechanicalFluidGunWrenchTargetHandler.clear();

        if (ppbe.target == null) {
            Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
            return false;
        }

        PackagePortTarget target = ppbe.target;
        Vec3 targetLocation = target.getExactTargetLocation(ppbe, mc.level, pos);
        if (targetLocation == null || targetLocation == Vec3.ZERO) {
            Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
            return false;
        }

        Outliner.getInstance()
            .chaseAABB(PACKAGE_PORT_TARGET_KEY, new AABB(targetLocation, targetLocation))
            .colored(VALID_TARGET_COLOR)
            .lineWidth(POINT_LINE_WIDTH)
            .disableLineNormals();

        Vec3 source = ppbe instanceof CopperFrogportBlockEntity
            ? CopperFrogportBlock.getConnectionSource(pos, ppbe.getBlockState())
            : Vec3.atBottomCenterOf(pos);
        PackagePortTargetSelectionHandler.animateConnection(mc, source, targetLocation, VALID_TARGET_COLOR);
        return true;
    }

    private static void renderArm(Minecraft mc, BlockPos armPos, ArmBlockEntity arm) {
        Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
        DepotSelectionHandler.clearHoveredTargetPreview();
        MechanicalFluidGunWrenchTargetHandler.clear();

        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;
        Level level = mc.level;

        Set<String> nextKeys = new HashSet<>();

        for (ArmInteractionPoint point : accessor.getInputs()) {
            drawArmPoint(level, armPos, point, nextKeys);
        }
        for (ArmInteractionPoint point : accessor.getOutputs()) {
            drawArmPoint(level, armPos, point, nextKeys);
        }

        for (String key : activeArmPointKeys) {
            if (!nextKeys.contains(key)) {
                Outliner.getInstance().remove(key);
            }
        }
        activeArmPointKeys.clear();
        activeArmPointKeys.addAll(nextKeys);
    }

    private static void drawArmPoint(Level level, BlockPos armPos, ArmInteractionPoint point, Set<String> nextKeys) {
        if (!point.isValid()) {
            return;
        }

        BlockPos pointPos = point.getPos();
        BlockState state = level.getBlockState(pointPos);
        VoxelShape shape = state.getShape(level, pointPos);
        if (shape.isEmpty()) {
            return;
        }

        String key = armPointKey(armPos, pointPos);
        nextKeys.add(key);
        Outliner.getInstance()
            .showAABB(key, shape.bounds().move(pointPos))
            .colored(point.getMode().getColor())
            .lineWidth(SHAPE_LINE_WIDTH);
    }

    private static boolean renderEjector(Minecraft mc, BlockPos pos, EjectorBlockEntity ejector) {
        clearArmPointKeys();
        Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
        MechanicalFluidGunWrenchTargetHandler.clear();

        BlockPos target = ejector.getTargetPosition();
        if (target == null || target.equals(pos)) {
            DepotSelectionHandler.clearHoveredTargetPreview();
            return false;
        }

        DepotSelectionHandler.renderHoveredTargetPreview(mc, pos, target);
        return true;
    }

    private static boolean renderMechanicalFluidGun(Minecraft mc, BlockPos pos, MechanicalFluidGunBlockEntity gun) {
        clearArmPointKeys();
        Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
        DepotSelectionHandler.clearHoveredTargetPreview();

        MechanicalFluidGunWrenchTargetHandler.renderTargets(mc.level, pos, gun);
        return true;
    }

    private static void clearArmPointKeys() {
        for (String key : activeArmPointKeys) {
            Outliner.getInstance().remove(key);
        }
        activeArmPointKeys.clear();
    }

    private static String armPointKey(BlockPos armPos, BlockPos pointPos) {
        return ARM_POINT_KEY_PREFIX + armPos.asLong() + "_" + pointPos.asLong();
    }

}
