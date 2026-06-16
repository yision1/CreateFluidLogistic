package com.yision.fluidlogistics.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.yision.fluidlogistics.config.EarlyFluidLogisticsConfig;

public class FluidLogisticsMixinPlugin implements IMixinConfigPlugin {

    private static final String JEI_RUNTIME_CLASS = "mezz.jei.api.runtime.IJeiRuntime";

    private static final Set<String> JEI_ONLY_MIXINS = Set.of(
        "com.yision.fluidlogistics.mixin.client.StockKeeperTransferHandlerMixin"
    );

    private static final Set<String> HAND_POINTER_SUPPORT_MIXINS = Set.of(
        "com.yision.fluidlogistics.mixin.accessor.ArmBlockEntityAccessor",
        "com.yision.fluidlogistics.mixin.accessor.FrogportChainConveyorOBBAccessor",
        "com.yision.fluidlogistics.mixin.accessor.FrogportChainConveyorShapeAccessor",
        "com.yision.fluidlogistics.mixin.logistics.PackagerBlockEntityMixin",
        "com.yision.fluidlogistics.mixin.logistics.PackagerBlockMixin"
    );

    private static final Set<String> ADVANCED_LOGISTICS_MIXINS = Set.of(

        "com.yision.fluidlogistics.mixin.accessor.StockTickerBlockEntityAccessor",
        "com.yision.fluidlogistics.mixin.item.PackageItemMixin",
        "com.yision.fluidlogistics.mixin.logistics.AttributeFilterItemStackMixin",
        "com.yision.fluidlogistics.mixin.logistics.FactoryPanelBehaviourLegacyLinkCleanupMixin",
        "com.yision.fluidlogistics.mixin.logistics.FactoryPanelBehaviourMixin",
        "com.yision.fluidlogistics.mixin.logistics.FactoryPanelRestockThresholdMixin",
        "com.yision.fluidlogistics.mixin.logistics.FactoryPanelBlockEntityMixin",
        "com.yision.fluidlogistics.mixin.logistics.InventorySummaryMixin",
        "com.yision.fluidlogistics.mixin.logistics.LogisticallyLinkedBehaviourLegacyLinkCleanupMixin",
        "com.yision.fluidlogistics.mixin.logistics.LogisticsManagerMixin",
        "com.yision.fluidlogistics.mixin.logistics.PackageEntityMixin",
        "com.yision.fluidlogistics.mixin.logistics.RequestPromiseQueueMixin",

        "com.yision.fluidlogistics.mixin.client.ChainConveyorRendererMixin",
        "com.yision.fluidlogistics.mixin.client.ChainConveyorVisualMixin",
        "com.yision.fluidlogistics.mixin.client.CraftableBigItemStackMixin",
        "com.yision.fluidlogistics.mixin.client.FactoryPanelBehaviourClientMixin",
        "com.yision.fluidlogistics.mixin.client.FactoryPanelScreenMixin",
        "com.yision.fluidlogistics.mixin.client.FactoryPanelSetItemScreenMixin",
        "com.yision.fluidlogistics.mixin.client.GoggleOverlayRendererMixin",
        "com.yision.fluidlogistics.mixin.client.PackageRendererMixin",
        "com.yision.fluidlogistics.mixin.client.PackageVisualMixin",
        "com.yision.fluidlogistics.mixin.client.RedstoneRequesterScreenMixin",
        "com.yision.fluidlogistics.mixin.client.StockKeeperRequestScreenMixin",
        "com.yision.fluidlogistics.mixin.client.StockKeeperTransferHandlerMixin"
    );

    private boolean jeiLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        jeiLoaded = isClassPresent(JEI_RUNTIME_CLASS);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (ADVANCED_LOGISTICS_MIXINS.contains(mixinClassName)
            && !EarlyFluidLogisticsConfig.advancedLogisticsNetworkEnabled()) {
            return false;
        }
        if (JEI_ONLY_MIXINS.contains(mixinClassName)) {
            return jeiLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, FluidLogisticsMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
