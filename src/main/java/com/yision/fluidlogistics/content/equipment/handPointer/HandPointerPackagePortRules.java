package com.yision.fluidlogistics.content.equipment.handPointer;

public final class HandPointerPackagePortRules {

    private HandPointerPackagePortRules() {
    }

    public static boolean isWithinRange(double targetX, double targetY, double targetZ,
                                        int portX, int portY, int portZ, double maxRange) {
        double dx = targetX - (portX + 0.5);
        double dy = targetY - portY;
        double dz = targetZ - (portZ + 0.5);
        return dx * dx + dy * dy + dz * dz <= maxRange * maxRange;
    }

    public static boolean isCreateFrogportReachable(double targetX, double targetY, double targetZ,
                                                     int frogportX, int frogportY, int frogportZ,
                                                     double maxRange) {
        return targetY >= frogportY && isWithinRange(
            targetX, targetY, targetZ, frogportX, frogportY, frogportZ, maxRange);
    }
}
