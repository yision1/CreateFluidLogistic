package com.yision.fluidlogistics.registry;

import com.tterrag.registrate.builders.MenuBuilder.ForgeMenuFactory;
import com.tterrag.registrate.builders.MenuBuilder.ScreenFactory;
import com.tterrag.registrate.util.entry.MenuEntry;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.equipment.handPointer.filter.HandPointerFilterMenu;
import com.yision.fluidlogistics.content.equipment.handPointer.filter.HandPointerFilterScreen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class AllMenuTypes {

    public static final MenuEntry<HandPointerFilterMenu> HAND_POINTER_FILTER =
        register("hand_pointer_filter", HandPointerFilterMenu::new, () -> HandPointerFilterScreen::new);

    private static <C extends AbstractContainerMenu, S extends Screen & MenuAccess<C>> MenuEntry<C> register(
        String name, ForgeMenuFactory<C> factory, NonNullSupplier<ScreenFactory<C, S>> screenFactory) {
        return FluidLogistics.REGISTRATE.menu(name, factory, screenFactory).register();
    }

    public static void register() {
    }
}
