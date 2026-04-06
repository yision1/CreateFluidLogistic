package com.yision.fluidlogistics.util;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.yision.fluidlogistics.api.IFluidPackager;
import com.yision.fluidlogistics.item.CompressedTankItem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FluidGaugeHelper {

    public static final String RESTOCK_THRESHOLD_KEY = "FluidRestockThreshold";
    public static final String PROMISE_LIMIT_KEY = "FluidPromiseLimit";
    public static final String ADDITIONAL_STOCK_KEY = "FluidAdditionalStock";
    public static final String REMAINING_ADDITIONAL_STOCK_KEY = "FluidRemainingAdditionalStock";

    public static final int DEFAULT_RESTOCK_THRESHOLD = 0;
    public static final int DEFAULT_PROMISE_LIMIT = -1;
    public static final int DEFAULT_ADDITIONAL_STOCK = 0;
    public static final int MAX_FLUID_AMOUNT = 100 * FluidAmountHelper.MB_PER_BUCKET;

    private FluidGaugeHelper() {
    }

    public static boolean isVirtualFluidFilter(ItemStack stack) {
        return stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack);
    }

    public static boolean isVirtualFluidFilter(FactoryPanelBehaviour behaviour) {
        return isVirtualFluidFilter(behaviour.getFilter());
    }

    public static boolean isVirtualFluidRestocker(FactoryPanelBehaviour behaviour) {
        return behaviour.panelBE().restocker && isVirtualFluidFilter(behaviour);
    }

    public static int clampRestockThreshold(int threshold) {
        return Math.clamp(threshold, DEFAULT_RESTOCK_THRESHOLD, MAX_FLUID_AMOUNT);
    }

    public static int clampPromiseLimit(int limit) {
        return Math.clamp(limit, DEFAULT_PROMISE_LIMIT, MAX_FLUID_AMOUNT);
    }

    public static int clampAdditionalStock(int amount) {
        return Math.clamp(amount, DEFAULT_ADDITIONAL_STOCK, MAX_FLUID_AMOUNT);
    }

    public static int clampRemainingAdditionalStock(int amount) {
        return Math.clamp(amount, DEFAULT_ADDITIONAL_STOCK, MAX_FLUID_AMOUNT);
    }

    public static int getEffectiveRestockThreshold(@Nullable IFluidRestockThreshold thresholdData) {
        if (thresholdData == null) {
            return 1;
        }
        return Math.max(1, thresholdData.fluidlogistics$getRestockThreshold());
    }

    public static int getAdditionalDemand(FactoryPanelBehaviour behaviour) {
        if (!(behaviour instanceof IFluidAdditionalStock additionalStockData)) {
            return 0;
        }

        int additionalDemand = additionalStockData.fluidlogistics$getRemainingAdditionalStock();
        if (additionalDemand <= 0 && !behaviour.satisfied && additionalStockData.fluidlogistics$hasAdditionalStock()) {
            additionalDemand = additionalStockData.fluidlogistics$getAdditionalStock();
        }
        return additionalDemand;
    }

    public static int getRestockDemand(FactoryPanelBehaviour behaviour) {
        return behaviour.getAmount() + getAdditionalDemand(behaviour);
    }

    @Nullable
    public static IFluidPackager getFluidPackager(FactoryPanelBlockEntity panelBE) {
        Level level = panelBE.getLevel();
        if (level == null) {
            return null;
        }

        BlockState state = panelBE.getBlockState();
        if (!AllBlocks.FACTORY_GAUGE.has(state)) {
            return null;
        }

        BlockPos packagerPos = panelBE.getBlockPos()
            .relative(FactoryPanelBlock.connectedDirection(state).getOpposite());
        if (!level.isLoaded(packagerPos)) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(packagerPos);
        if (blockEntity instanceof IFluidPackager fluidPackager) {
            return fluidPackager;
        }
        return null;
    }

    public static <T> void applyPanelSetting(ServerPlayer player, FactoryPanelPosition panelPosition, Class<T> type,
        Consumer<T> updater) {
        FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), panelPosition);
        if (behaviour == null) {
            return;
        }

        if (!Create.LOGISTICS.mayInteract(behaviour.network, player) || !type.isInstance(behaviour)) {
            return;
        }

        updater.accept(type.cast(behaviour));
        behaviour.resetTimerSlightly();
        behaviour.panelBE().notifyUpdate();
    }
}
