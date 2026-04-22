package com.yision.fluidlogistics.util;

import java.math.BigDecimal;
import java.util.Locale;

import com.simibubi.create.content.logistics.BigItemStack;

public final class FluidAmountHelper {

    public static final int MB_PER_BUCKET = 1000;
    public static final int MB_PER_TENTH_BUCKET = 100;
    public static final int MB_PER_KILOBUCKET = MB_PER_BUCKET * 1000;

    private FluidAmountHelper() {}

    public static String format(int amount) {
        if (amount >= BigItemStack.INF) {
            return "\u221e";
        }
        if (amount >= MB_PER_KILOBUCKET) {
            return formatCompact(amount, MB_PER_KILOBUCKET, "KB");
        }
        if (amount >= 100) {
            return formatCompact(amount, MB_PER_BUCKET, "B");
        }
        return amount + "mB";
    }

    public static String formatPrecise(int amount) {
        if (amount >= BigItemStack.INF) {
            return "\u221e";
        }
        if (amount < MB_PER_BUCKET) {
            return amount + "mB";
        }
        if (amount >= MB_PER_KILOBUCKET) {
            return BigDecimal.valueOf(amount, 6)
                .stripTrailingZeros()
                .toPlainString() + "KB";
        }
        return BigDecimal.valueOf(amount, 3)
            .stripTrailingZeros()
            .toPlainString() + "B";
    }

    public static String formatDetailed(int amount) {
        if (amount >= BigItemStack.INF) {
            return "\u221e";
        }
        if (amount >= MB_PER_KILOBUCKET) {
            int kilobuckets = amount / MB_PER_KILOBUCKET;
            int remainder = amount % MB_PER_KILOBUCKET;
            if (remainder == 0) {
                return kilobuckets + " KB (" + amount + " mB)";
            }
            return String.format(Locale.ROOT, "%.1f KB (%d mB)", amount / (double) MB_PER_KILOBUCKET, amount);
        }
        if (amount >= MB_PER_BUCKET) {
            int buckets = amount / MB_PER_BUCKET;
            int remainder = amount % MB_PER_BUCKET;
            if (remainder == 0) {
                return buckets + " B (" + amount + " mB)";
            }
            return String.format(Locale.ROOT, "%.1f B (%d mB)", amount / (double) MB_PER_BUCKET, amount);
        }
        return amount + " mB";
    }

    public static int parseAmount(String input) throws NumberFormatException {
        input = input.trim().toLowerCase(Locale.ROOT);

        if (input.endsWith("kb")) {
            String numPart = input.substring(0, input.length() - 2);
            return (int) (Double.parseDouble(numPart) * MB_PER_KILOBUCKET);
        } else if (input.endsWith("b") && !input.endsWith("mb")) {
            String numPart = input.substring(0, input.length() - 1);
            return (int) (Double.parseDouble(numPart) * MB_PER_BUCKET);
        } else if (input.endsWith("mb")) {
            String numPart = input.substring(0, input.length() - 2);
            return (int) Double.parseDouble(numPart);
        } else {
            return (int) Double.parseDouble(input);
        }
    }

    public static int adjustFactoryGaugeAmount(int currentAmount, boolean forward, boolean shift, boolean control,
            int minAmount, int maxAmount) {
        int delta;

        if (control) {
            delta = 1;
        } else if (shift) {
            delta = 100;
        } else {
            delta = 1000;
        }

        int newAmount = currentAmount + (forward ? delta : -delta);

        if (forward) {
            if (currentAmount < MB_PER_TENTH_BUCKET && newAmount >= MB_PER_TENTH_BUCKET && newAmount < MB_PER_BUCKET) {
                newAmount = MB_PER_TENTH_BUCKET;
            } else if (currentAmount < MB_PER_BUCKET && newAmount >= MB_PER_BUCKET) {
                newAmount = MB_PER_BUCKET;
            }
        } else if (currentAmount >= MB_PER_BUCKET && newAmount < MB_PER_BUCKET && newAmount >= MB_PER_TENTH_BUCKET) {
            newAmount = MB_PER_TENTH_BUCKET;
        }

        return Math.clamp(newAmount, minAmount, maxAmount);
    }

    private static String formatCompact(int amount, int unitSize, String suffix) {
        if (amount >= unitSize && amount % unitSize == 0) {
            return (amount / unitSize) + suffix;
        }
        double value = Math.floor(amount / (unitSize / 10.0)) / 10.0;
        return String.format(Locale.ROOT, "%.1f%s", value, suffix);
    }
}
