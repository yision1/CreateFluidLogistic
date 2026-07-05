package com.yision.fluidlogistics.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class FluidLogisticsMixinPlugin implements IMixinConfigPlugin {

    private static final String JEI_RUNTIME_CLASS = "mezz.jei.api.runtime.IJeiRuntime";
    private static final Set<String> JEI_ONLY_MIXINS = Set.of(
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
