package com.yision.fluidlogistics.mixin.client;

import java.util.Collection;
import java.util.Set;

import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.registry.CreativeTabSectionRegistry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

@Mixin(CreativeModeTab.class)
public class CreativeModeTabBannerPaddingMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Inject(method = "buildContents", at = @At("RETURN"))
    private void fluidLogistics$reserveBannerRow(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        if ((Object) this != FluidLogistics.FLUID_LOGISTICS_CREATIVE_TAB.get()) {
            return;
        }

        displayItems = CreativeTabSectionRegistry.instance()
                .rebuildDisplayItems(displayItems, displayItemsSearchTab);
    }
}
