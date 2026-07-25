package com.yision.fluidlogistics.content.logistics.fluidPackage;

import java.util.ArrayList;
import java.util.List;

public final class CompressedTankRules {

    private CompressedTankRules() {
    }

    public static boolean isStoredFluidAmountValid(int amount, int capacity) {
        return amount > 0 && amount <= capacity;
    }

    public static List<Integer> splitAmounts(int amount, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        List<Integer> chunks = new ArrayList<>();
        for (int remaining = amount; remaining > 0; remaining -= capacity) {
            chunks.add(Math.min(remaining, capacity));
        }
        return chunks;
    }
}
