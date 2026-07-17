package com.yision.fluidlogistics.api.packager;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

@ApiStatus.Experimental
public interface PackageResourceDisplay {
    record FactoryPanelUnit(String label, int amountPerStep, int displayedPerStep) {
        public FactoryPanelUnit(String label, int amountPerStep) {
            this(label, amountPerStep, 1);
        }

        public FactoryPanelUnit {
            Objects.requireNonNull(label, "label");
            if (amountPerStep <= 0 || displayedPerStep <= 0) {
                throw new IllegalArgumentException("factory panel step values must be positive");
            }
        }
    }

    record FactoryPanelRestockPolicy(
            boolean configurableThreshold,
            boolean configurableAdditionalStock,
            boolean configurablePromiseLimit,
            int maxSettingAmount,
            int maxRequestPerBatch) {
        private static final FactoryPanelRestockPolicy STANDARD = new FactoryPanelRestockPolicy(
                false, false, false, BigItemStack.INF, BigItemStack.INF);

        public static FactoryPanelRestockPolicy standard() {
            return STANDARD;
        }

        public FactoryPanelRestockPolicy {
            if (maxSettingAmount <= 0 || maxRequestPerBatch <= 0) {
                throw new IllegalArgumentException("factory panel restock limits must be positive");
            }
        }

        public boolean hasConfigurableSettings() {
            return configurableThreshold || configurableAdditionalStock || configurablePromiseLimit;
        }

        public int clampThreshold(int amount) {
            return configurableThreshold ? Math.clamp(amount, 0, maxSettingAmount) : 0;
        }

        public int clampAdditionalStock(int amount) {
            return configurableAdditionalStock ? Math.clamp(amount, 0, maxSettingAmount) : 0;
        }

        public int clampPromiseLimit(int amount) {
            return configurablePromiseLimit ? Math.clamp(amount, -1, maxSettingAmount) : -1;
        }

        public int effectiveThreshold(int configuredThreshold) {
            return configurableThreshold ? Math.max(1, clampThreshold(configuredThreshold)) : 1;
        }

        public int restockDemand(int targetAmount, int remainingAdditionalStock) {
            long demand = Math.max(0, targetAmount);
            if (configurableAdditionalStock) {
                demand += Math.max(0, remainingAdditionalStock);
            }
            return (int) Math.min(BigItemStack.INF, demand);
        }

        public int remainingPromiseCapacity(int configuredLimit, int promised) {
            if (!configurablePromiseLimit || configuredLimit < 0) {
                return BigItemStack.INF;
            }
            return Math.max(0, clampPromiseLimit(configuredLimit) - Math.max(0, promised));
        }
    }

    enum Format {
        COMPACT,
        PRECISE,
        DETAILED
    }

    enum Interaction {
        GENERIC,
        STOCK_KEEPER,
        STOCK_KEEPER_INVENTORY,
        STOCK_KEEPER_ORDER,
        REDSTONE_REQUESTER,
        FACTORY_PANEL
    }

    enum TooltipContext {
        STOCK_KEEPER_INVENTORY,
        STOCK_KEEPER_ORDER,
        STOCK_KEEPER_CRAFTABLE
    }

    record Adjustment(
            int currentAmount,
            boolean forward,
            boolean shift,
            boolean control,
            int minAmount,
            int maxAmount,
            int steps,
            Interaction interaction) {
        public Adjustment {
            Objects.requireNonNull(interaction, "interaction");
            if (minAmount > maxAmount) {
                throw new IllegalArgumentException("minAmount must not exceed maxAmount");
            }
            steps = Math.max(0, steps);
        }
    }

    String baseUnit();

    String format(ItemStack normalizedKey, int amount, Format format);

    default Component name(ItemStack normalizedKey) {
        return icon(normalizedKey).getHoverName().copy();
    }

    default ItemStack icon(ItemStack normalizedKey) {
        Objects.requireNonNull(normalizedKey, "normalizedKey");
        return normalizedKey.copyWithCount(1);
    }

    default List<Component> tooltip(ItemStack normalizedKey, int amount, boolean advanced) {
        return List.of(
                name(normalizedKey),
                Component.literal(format(normalizedKey, amount, Format.PRECISE))
                        .withStyle(ChatFormatting.GRAY));
    }

    default List<Component> tooltip(ItemStack normalizedKey, boolean advanced) {
        return List.of(name(normalizedKey));
    }

    default boolean showsAmountInTooltip(TooltipContext context) {
        return false;
    }

    default List<Component> tooltip(
            ItemStack normalizedKey, int amount, boolean advanced, TooltipContext context) {
        Objects.requireNonNull(context, "context");
        return showsAmountInTooltip(context)
                ? tooltip(normalizedKey, amount, advanced)
                : tooltip(normalizedKey, advanced);
    }

    default List<FactoryPanelUnit> factoryPanelUnits(ItemStack normalizedKey) {
        return List.of(new FactoryPanelUnit(baseUnit(), 1));
    }

    default int factoryPanelMaxValue(ItemStack normalizedKey) {
        return 100;
    }

    default int factoryPanelMilestoneInterval(ItemStack normalizedKey) {
        return 10;
    }

    default FactoryPanelRestockPolicy factoryPanelRestockPolicy(ItemStack normalizedKey) {
        return FactoryPanelRestockPolicy.standard();
    }

    default int factoryPanelAmount(ItemStack normalizedKey, int row, int value) {
        int maximum = Math.max(0, factoryPanelMaxValue(normalizedKey));
        int boundedValue = Math.max(0, Math.min(maximum, value));
        long amount = (long) boundedValue * factoryPanelUnit(normalizedKey, row).amountPerStep();
        return (int) Math.min(Integer.MAX_VALUE, amount);
    }

    default int factoryPanelDisplayedValue(ItemStack normalizedKey, int row, int value) {
        int maximum = Math.max(0, factoryPanelMaxValue(normalizedKey));
        int boundedValue = Math.max(0, Math.min(maximum, value));
        long displayed = (long) boundedValue * factoryPanelUnit(normalizedKey, row).displayedPerStep();
        return (int) Math.min(Integer.MAX_VALUE, displayed);
    }

    default int factoryPanelRow(ItemStack normalizedKey, int amount) {
        List<FactoryPanelUnit> units = factoryPanelUnits(normalizedKey);
        if (units.isEmpty()) {
            throw new IllegalStateException("factory panel units must not be empty");
        }
        int boundedAmount = Math.max(0, amount);
        int row = 0;
        for (int index = 1; index < units.size(); index++) {
            if (boundedAmount < units.get(index).amountPerStep()) {
                break;
            }
            row = index;
        }
        return row;
    }

    default int factoryPanelValue(ItemStack normalizedKey, int row, int amount) {
        int value = Math.max(0, amount) / factoryPanelUnit(normalizedKey, row).amountPerStep();
        return Math.min(Math.max(0, factoryPanelMaxValue(normalizedKey)), value);
    }

    int adjust(ItemStack normalizedKey, Adjustment adjustment);

    private FactoryPanelUnit factoryPanelUnit(ItemStack normalizedKey, int row) {
        List<FactoryPanelUnit> units = factoryPanelUnits(normalizedKey);
        if (units.isEmpty()) {
            throw new IllegalStateException("factory panel units must not be empty");
        }
        return units.get(Math.max(0, Math.min(units.size() - 1, row)));
    }
}
