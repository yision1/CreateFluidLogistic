package com.yision.fluidlogistics.content.logistics.copperFrogport;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

record CopperFrogportAnimationState(
    Direction attachedDirection,
    Quaternionf orientation,
    float yaw,
    float headPitch,
    float tonguePitch,
    float tongueLength,
    float packageScale,
    float packageDistance,
    Vec3 targetDiff,
    Vec3 exactTarget,
    boolean animating
) {

    private static final double PACKAGE_HANG_OFFSET = 9 / 16d;
    private static final double PACKAGE_MOUTH_OFFSET = 3 / 16d;

    static CopperFrogportAnimationState create(CopperFrogportBlockEntity blockEntity, float partialTicks) {
        Direction attachedDirection = CopperFrogportBlock.getAttachedDirection(blockEntity.getBlockState());
        Quaternionf orientation = orientation(attachedDirection);
        Vec3 exactTarget = getExactTarget(blockEntity);
        boolean hasTarget = !Vec3.ZERO.equals(exactTarget);
        boolean animating = blockEntity.isAnimationInProgress();
        boolean depositing = blockEntity.currentlyDepositing;

        float yaw = getLocalYaw(blockEntity, orientation, exactTarget, hasTarget);
        float headPitch = 80;
        float tonguePitch = 0;
        float tongueLength = 0;
        float headPitchModifier = 1;
        Vec3 targetDiff = Vec3.ZERO;

        if (hasTarget) {
            Vec3 worldDiff = exactTarget.subtract(Vec3.atCenterOf(blockEntity.getBlockPos()));
            if (!animating || !depositing) {
                worldDiff = worldDiff.subtract(0, 0.75, 0);
            }
            targetDiff = toLocal(worldDiff, orientation);
            tonguePitch = (float) Mth.atan2(
                targetDiff.y,
                targetDiff.multiply(1, 0, 1).length() + 3 / 16f
            ) * Mth.RAD_TO_DEG;
            tongueLength = Math.max((float) targetDiff.length(), 1);
            headPitch = Mth.clamp(tonguePitch * 2, 60, 100);
        }

        float packageScale = 0;
        float packageDistance = 0;
        if (animating) {
            float progress = blockEntity.animationProgress.getValue(partialTicks);
            packageScale = 1;
            if (depositing) {
                double modifier = Math.max(0, 1 - Math.pow((progress - 0.25) * 4 - 1, 4));
                packageDistance =
                    (float) Math.max(tongueLength * Math.min(1, (progress - 0.25) * 3),
                        tongueLength * modifier);
                tongueLength *= Math.max(0, 1 - Math.pow((progress * 1.25 - 0.25) * 4 - 1, 4));
                headPitchModifier = (float) Math.max(0, 1 - Math.pow((progress * 1.25) * 2 - 1, 4));
                packageScale = 0.25f + progress * 3 / 4;
            } else {
                tongueLength *= Math.pow(Math.max(0, 1 - progress * 1.25), 5);
                headPitchModifier =
                    1 - (float) Math.min(1, Math.max(0, (Math.pow(progress * 1.5, 2) - 0.5) * 2));
                packageScale = (float) Math.max(0.5, 1 - progress * 1.25);
                packageDistance = tongueLength;
            }
        } else {
            tongueLength = 0;
            float anticipation = blockEntity.anticipationProgress.getValue(partialTicks);
            headPitchModifier = anticipation > 0
                ? (float) Math.max(0, 1 - Math.pow((anticipation * 1.25) * 2 - 1, 4))
                : 0;
        }

        float manualOpen = blockEntity.manualOpenAnimationProgress.getValue(partialTicks);
        headPitch = Math.max(headPitch * headPitchModifier, manualOpen * 60);
        tongueLength = Math.max(tongueLength, manualOpen * 0.25f);

        return new CopperFrogportAnimationState(
            attachedDirection,
            orientation,
            yaw,
            headPitch,
            tonguePitch,
            tongueLength,
            packageScale,
            packageDistance,
            targetDiff,
            exactTarget,
            animating
        );
    }

    Vec3 packageOffset(CopperFrogportBlockEntity blockEntity) {
        Vec3 startOffset = Vec3.atLowerCornerOf(attachedDirection.getNormal())
            .scale(PACKAGE_HANG_OFFSET);
        if (Vec3.ZERO.equals(exactTarget)) {
            return startOffset;
        }

        Vec3 endOffset = exactTarget
            .subtract(Vec3.atCenterOf(blockEntity.getBlockPos()))
            .subtract(0, PACKAGE_HANG_OFFSET, 0);
        double fullDistance = Math.max(targetDiff.length(), 1);
        double progress = Mth.clamp(packageDistance / fullDistance, 0, 1);
        if (!blockEntity.currentlyDepositing) {
            Vec3 mouthOffset = Vec3.atLowerCornerOf(attachedDirection.getNormal())
                .scale(-PACKAGE_MOUTH_OFFSET);
            return mouthOffset.lerp(endOffset, progress);
        }

        return startOffset.lerp(endOffset, progress);
    }

    private static Vec3 getExactTarget(CopperFrogportBlockEntity blockEntity) {
        if (blockEntity.target == null) {
            return Vec3.ZERO;
        }
        return blockEntity.target.getExactTargetLocation(
            blockEntity,
            blockEntity.getLevel(),
            blockEntity.getBlockPos()
        );
    }

    private static float getLocalYaw(CopperFrogportBlockEntity blockEntity, Quaternionf orientation,
                                     Vec3 exactTarget, boolean hasTarget) {
        if (!hasTarget) {
            double angle = blockEntity.passiveYaw * Mth.DEG_TO_RAD;
            Vec3 localFacing = toLocal(
                new Vec3(Mth.sin((float) angle), 0, Mth.cos((float) angle)),
                orientation
            );
            return (float) (Mth.atan2(localFacing.x, localFacing.z) * Mth.RAD_TO_DEG);
        }

        Vec3 diff = toLocal(
            exactTarget.subtract(Vec3.atCenterOf(blockEntity.getBlockPos())),
            orientation
        );
        return (float) (Mth.atan2(diff.x, diff.z) * Mth.RAD_TO_DEG) + 180;
    }

    private static Vec3 toLocal(Vec3 vector, Quaternionf orientation) {
        Vector3f transformed = new Quaternionf(orientation)
            .conjugate()
            .transform(new Vector3f((float) vector.x, (float) vector.y, (float) vector.z));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    private static Quaternionf orientation(Direction attachedDirection) {
        return new Quaternionf().rotationTo(
            0, -1, 0,
            attachedDirection.getStepX(),
            attachedDirection.getStepY(),
            attachedDirection.getStepZ()
        );
    }
}
