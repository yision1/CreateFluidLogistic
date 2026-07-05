package com.yision.fluidlogistics.content.equipment.handPointer.client;

import java.util.HashSet;
import java.util.Set;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.client.MechanicalFluidGunWrenchTargetHandler;
import com.yision.fluidlogistics.mixin.accessor.ArmBlockEntityAccessor;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    private static boolean renderPackagePort(Minecraft mc, BlockPos pos, PackagePortBlockEntity ppbe) {
        clearArmPointKeys();
        DepotSelectionHandler.clearHoveredTargetPreview();
        MechanicalFluidGunWrenchTargetHandler.clear();

        PackagePortTarget target = ppbe.target;
        if (target == null) {
            Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
            return false;
        }

        Vec3 targetLocation = target.getExactTargetLocation(ppbe, mc.level, pos);
        if (targetLocation == null || targetLocation == Vec3.ZERO) {
            Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
            return false;
        }

        Outliner.getInstance()
            .chaseAABB(PACKAGE_PORT_TARGET_KEY, new AABB(targetLocation, targetLocation))
            .colored(VALID_TARGET_COLOR.getRGB())
            .lineWidth(POINT_LINE_WIDTH)
            .disableLineNormals();

        animateConnection(mc, Vec3.atBottomCenterOf(pos), targetLocation, VALID_TARGET_COLOR);
        return true;
    }

    private static void renderArm(Minecraft mc, BlockPos pos, ArmBlockEntity arm) {
        Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
        DepotSelectionHandler.clearHoveredTargetPreview();
        MechanicalFluidGunWrenchTargetHandler.clear();

        ArmBlockEntityAccessor accessor = (ArmBlockEntityAccessor) arm;
        Level level = mc.level;

        Set<String> currentKeys = new HashSet<>();
        renderArmPoints(level, pos, accessor.getInputs(), currentKeys);
        renderArmPoints(level, pos, accessor.getOutputs(), currentKeys);

        for (String key : activeArmPointKeys) {
            if (!currentKeys.contains(key)) {
                Outliner.getInstance().remove(key);
            }
        }
        activeArmPointKeys.clear();
        activeArmPointKeys.addAll(currentKeys);
    }

    private static void renderArmPoints(Level level, BlockPos armPos, java.util.List<ArmInteractionPoint> points,
                                        Set<String> currentKeys) {
        for (ArmInteractionPoint point : points) {
            if (!point.isValid()) {
                continue;
            }

            BlockPos pointPos = point.getPos();
            BlockState state = level.getBlockState(pointPos);
            VoxelShape shape = state.getShape(level, pointPos);
            if (shape.isEmpty()) {
                continue;
            }

            String key = ARM_POINT_KEY_PREFIX + armPos.asLong() + "_" + pointPos.asLong();
            currentKeys.add(key);
            Outliner.getInstance()
                .showAABB(key, shape.bounds().move(pointPos))
                .colored(point.getMode().getColor())
                .lineWidth(SHAPE_LINE_WIDTH);
        }
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

    private static void animateConnection(Minecraft mc, Vec3 source, Vec3 target, Color color) {
        DustParticleOptions data = new DustParticleOptions(color.asVectorF(), 1.0F);
        ClientLevel world = mc.level;
        double totalFlyingTicks = 10;
        int segments = (((int) totalFlyingTicks) / 3) + 1;
        double tickOffset = totalFlyingTicks / segments;

        for (int i = 0; i < segments; i++) {
            double ticks = ((AnimationTickHolder.getRenderTime() / 3.0F) % tickOffset) + i * tickOffset;
            Vec3 vec = source.lerp(target, ticks / totalFlyingTicks);
            world.addParticle(data, vec.x, vec.y, vec.z, 0, 0, 0);
        }
    }

    public static void clear() {
        Outliner.getInstance().remove(PACKAGE_PORT_TARGET_KEY);
        DepotSelectionHandler.clearHoveredTargetPreview();
        MechanicalFluidGunWrenchTargetHandler.clear();
        clearArmPointKeys();
    }
}
