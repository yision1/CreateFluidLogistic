package com.yision.fluidlogistics.util;

import java.math.BigDecimal;

import com.simibubi.create.content.logistics.BigItemStack;

public final class FluidAmountHelper {

    public static final int MB_PER_BUCKET = 1000;

    private FluidAmountHelper() {}

    public static String format(int amount) {
        if (amount >= BigItemStack.INF) {
            return "+";
        }
        if (amount >= 100) {
            if (amount >= 1000 && amount % 1000 == 0) {
                return (amount / 1000) + "B";
            }
            double buckets = Math.floor(amount / 100.0) / 10.0;
            return String.format("%.1fB", buckets);
        }
        return amount + "mB";
    }

    public static String formatPrecise(int amount) {
        if (amount >= BigItemStack.INF) {
            return "+";
        }
        if (amount < MB_PER_BUCKET) {
            return amount + "mB";
        }
        return BigDecimal.valueOf(amount, 3)
            .stripTrailingZeros()
            .toPlainString() + "B";
    }

    public static String formatDetailed(int amount) {
        if (amount >= BigItemStack.INF) {
            return "∞ mB";
        }
        if (amount >= 1000) {
            int buckets = amount / 1000;
            int remainder = amount % 1000;
            if (remainder == 0) {
                return buckets + " B (" + amount + " mB)";
            }
            return String.format("%.1f B (%d mB)", amount / 1000.0, amount);
        }
        return amount + " mB";
    }

    public static int parseAmount(String input) throws NumberFormatException {
        input = input.trim().toLowerCase();

        if (input.endsWith("b") && !input.endsWith("mb")) {
            String numPart = input.substring(0, input.length() - 1);
            return (int) (Double.parseDouble(numPart) * MB_PER_BUCKET);
        } else if (input.endsWith("mb")) {
            String numPart = input.substring(0, input.length() - 2);
            return (int) Double.parseDouble(numPart);
        } else {
            return (int) Double.parseDouble(input);
        }
    }
}
