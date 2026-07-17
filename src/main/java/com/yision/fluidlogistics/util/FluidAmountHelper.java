package com.yision.fluidlogistics.util;

import java.math.BigDecimal;
import java.util.Locale;

import com.simibubi.create.content.logistics.BigItemStack;

public final class FluidAmountHelper {

    public static final int MB_PER_BUCKET = 1000;
    public static final int MB_PER_TENTH_BUCKET = 100;
    public static final int MB_PER_KILOBUCKET = MB_PER_BUCKET * 1000;
    public static final int DEFAULT_FLUID_REQUEST_AMOUNT = 1;
    public static final String INACTIVE_AMOUNT_LABEL = "---";

    public static final int STOCK_KEEPER_FLUID_STEP = MB_PER_BUCKET;
    public static final int STOCK_KEEPER_FLUID_CTRL_STEP = 10 * MB_PER_BUCKET;
    public static final int STOCK_KEEPER_FLUID_SHIFT_STEP = 20 * MB_PER_BUCKET;

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

    public static String formatStockKeeper(int amount) {
        return format(amount);
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

    public static int adjustFluidRequestAmount(int currentAmount, boolean forward, boolean shift, boolean control,
            int minAmount, int maxAmount, int steps) {
        int delta = getFluidRequestStep(shift, control) * (forward? 1:-1);
        return adjustByDelta(currentAmount, delta, minAmount, maxAmount,steps);
    }

    public static int adjustFluidRequestAmount(int currentAmount, boolean forward, boolean shift, boolean control,
            int minAmount, int maxAmount) {
        return adjustFluidRequestAmount(currentAmount, forward, shift, control, minAmount, maxAmount,1);
    }

    public static int getStockKeeperFluidRequestStep(boolean shift, boolean control) {
        if (shift) {
            return STOCK_KEEPER_FLUID_SHIFT_STEP;
        }
        if (control) {
            return STOCK_KEEPER_FLUID_CTRL_STEP;
        }
        return STOCK_KEEPER_FLUID_STEP;
    }

    public static int adjustStockKeeperFluidRequestAmount(int currentAmount, boolean forward, boolean shift,
            boolean control, int minAmount, int maxAmount, int steps) {
        int delta = getStockKeeperFluidRequestStep(shift, control) * (forward ? 1 : -1);
        return adjustByDelta(currentAmount, delta, minAmount, maxAmount, steps);
    }

    private static int adjustByDelta(int currentAmount, int delta, int minAmount, int maxAmount, int steps) {
        int safeSteps = Math.max(0, steps);
        long newAmount;

        if (currentAmount == 1 && delta>1){
            newAmount = (long) delta * safeSteps;
        } else {
            newAmount = currentAmount + (long) delta * safeSteps;
        }

        return (int) Math.clamp(newAmount, (long) minAmount, (long) maxAmount);
    }

    private static int getFluidRequestStep(boolean shift, boolean control) {
        if (control) {
            return 1;
        }
        if (shift) {
            return MB_PER_TENTH_BUCKET;
        }
        return MB_PER_BUCKET;
    }

    public static String formatOptionalCompact(int amount, boolean zeroIsInactive) {
        if (amount < 0 || zeroIsInactive && amount == 0) {
            return INACTIVE_AMOUNT_LABEL;
        }
        return format(amount);
    }

    public static String formatOptionalPreciseMultiplier(int amount, boolean zeroIsInactive) {
        if (amount < 0 || zeroIsInactive && amount == 0) {
            return INACTIVE_AMOUNT_LABEL;
        }
        return "x" + formatPrecise(amount);
    }

    public static String formatFactoryGaugeValueSetting(int row, int value) {
        if (value == 0) {
            return null;
        }

        if (row == 1) {
            return Math.clamp(value, 1, 100) + "B";
        }

        return Math.max(0, value) * 10 + "mB";
    }

    public static int toFactoryGaugeAmount(int row, int value) {
        if (value <= 0) {
            return 0;
        }

        if (row == 1) {
            return Math.clamp(value, 1, 100) * MB_PER_BUCKET;
        }

        return Math.max(0, value) * 10;
    }

    public static int toFactoryGaugeValueSetting(int amount) {
        if (amount >= MB_PER_BUCKET) {
            return Math.clamp(amount / MB_PER_BUCKET, 1, 100);
        }

        return Math.max(0, amount) / 10;
    }

    private static String formatCompact(int amount, int unitSize, String suffix) {
        if (amount >= unitSize && amount % unitSize == 0) {
            return (amount / unitSize) + suffix;
        }
        if (amount / unitSize <= 10) {
            double value = Math.floor(amount / (unitSize / 10.0)) / 10.0;
            return String.format(Locale.ROOT, "%.1f%s", value, suffix);

        }
        return String.format(Locale.ROOT, "%.0f%s", Math.floor(amount / (float) unitSize), suffix);
    }
}
