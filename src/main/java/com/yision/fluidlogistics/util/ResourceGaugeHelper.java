package com.yision.fluidlogistics.util;

import java.util.function.BiPredicate;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.yision.fluidlogistics.api.packager.PackageResourceDisplay;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.content.logistics.packageResource.ResourceRestockSettings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ResourceGaugeHelper {
    public static final String RESTOCK_THRESHOLD_KEY = "FluidRestockThreshold";
    public static final String PROMISE_LIMIT_KEY = "FluidPromiseLimit";
    public static final String ADDITIONAL_STOCK_KEY = "FluidAdditionalStock";
    public static final String REMAINING_ADDITIONAL_STOCK_KEY = "FluidRemainingAdditionalStock";

    public static final int DEFAULT_RESTOCK_THRESHOLD = 0;
    public static final int DEFAULT_PROMISE_LIMIT = -1;
    public static final int DEFAULT_ADDITIONAL_STOCK = 0;

    private ResourceGaugeHelper() {
    }

    public static PackageResourceDisplay.FactoryPanelRestockPolicy policy(FactoryPanelBehaviour behaviour) {
        ItemStack filter = behaviour.getFilter();
        return PackageResources.displayOf(filter)
                .map(display -> display.factoryPanelRestockPolicy(filter))
                .orElseGet(PackageResourceDisplay.FactoryPanelRestockPolicy::standard);
    }

    public static boolean hasConfigurableSettings(FactoryPanelBehaviour behaviour) {
        return policy(behaviour).hasConfigurableSettings();
    }

    public static boolean isResourceRestocker(FactoryPanelBehaviour behaviour) {
        return behaviour.panelBE().restocker
                && PackageResources.displayOf(behaviour.getFilter()).isPresent();
    }

    public static void applyPanelSetting(
            ServerPlayer player,
            FactoryPanelPosition panelPosition,
            BiPredicate<PackageResourceDisplay.FactoryPanelRestockPolicy, ResourceRestockSettings> updater) {
        FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), panelPosition);
        if (behaviour == null || !Create.LOGISTICS.mayInteract(behaviour.network, player)
                || !(behaviour instanceof ResourceRestockSettings settings)) {
            return;
        }
        if (!updater.test(policy(behaviour), settings)) {
            return;
        }
        behaviour.resetTimerSlightly();
        behaviour.panelBE().notifyUpdate();
    }
}
