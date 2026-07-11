package com.yision.fluidlogistics.content.logistics.copperFrogport;

import net.minecraft.core.Direction;

public final class CopperFrogportRules {

    private CopperFrogportRules() {
    }

    public static boolean isChainHeightValid(Direction attachedDirection, double targetY, double frogportY) {
        return switch (attachedDirection) {
            case UP -> targetY <= frogportY;
            case DOWN -> targetY >= frogportY;
            default -> true;
        };
    }

    public static Direction inventorySide(Direction requestedSide, Direction attachedDirection) {
        return requestedSide == Direction.DOWN ? attachedDirection : requestedSide;
    }
}
