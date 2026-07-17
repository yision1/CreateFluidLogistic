package com.yision.fluidlogistics.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public interface CreativeModeItemPickerMenuAccessor {
    @Invoker("getRowIndexForScroll")
    int fluidLogistics$getRowIndexForScroll(float scrollOffset);
}
