package com.yision.fluidlogistics.client.event;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemMenu;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterMenu;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import com.yision.fluidlogistics.client.RedstoneRequesterAmountsAccess;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class FluidSlotClickHandler {

    private FluidSlotClickHandler() {
    }

    private static Screen swallowReleaseScreen;
    private static int swallowReleaseButton = -1;

    @SubscribeEvent
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        int button = event.getButton();
        if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) {
            return;
        }
        if (!Screen.hasAltDown()) {
            return;
        }
        if (handle(event.getScreen())) {
            swallowReleaseScreen = event.getScreen();
            swallowReleaseButton = button;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (swallowReleaseButton == -1) {
            return;
        }
        boolean matches = event.getScreen() == swallowReleaseScreen && event.getButton() == swallowReleaseButton;
        swallowReleaseScreen = null;
        swallowReleaseButton = -1;
        if (matches) {
            event.setCanceled(true);
        }
    }

    private static boolean handle(Screen screen) {
        if (screen instanceof RedstoneRequesterScreen requesterScreen) {
            Slot slot = requesterScreen.getSlotUnderMouse();
            return slot instanceof SlotItemHandler && handleRedstoneRequester(requesterScreen, slot);
        }
        if (screen instanceof FactoryPanelSetItemScreen panelScreen) {
            Slot slot = panelScreen.getSlotUnderMouse();
            return slot instanceof SlotItemHandler && handleFactoryPanel(panelScreen, slot);
        }
        return false;
    }

    private static boolean handleRedstoneRequester(RedstoneRequesterScreen screen, Slot slot) {
        RedstoneRequesterMenu menu = screen.getMenu();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || !GenericItemEmptying.canItemBeEmptied(menu.contentHolder.getLevel(), carried)) {
            return false;
        }

        Pair<FluidStack, ItemStack> emptyResult =
            GenericItemEmptying.emptyItem(menu.contentHolder.getLevel(), carried, true);
        if (emptyResult.getFirst().isEmpty()) {
            return false;
        }

        int slotIndex = slot.getSlotIndex();
        submit(menu, slotIndex, createFluidKey(emptyResult.getFirst()));
        ((RedstoneRequesterAmountsAccess) screen).fluidlogistics$getAmounts()
            .set(slotIndex, FluidAmountHelper.DEFAULT_FLUID_REQUEST_AMOUNT);
        return true;
    }

    private static boolean handleFactoryPanel(FactoryPanelSetItemScreen screen, Slot slot) {
        FactoryPanelSetItemMenu menu = screen.getMenu();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }

        FactoryPanelBehaviour behaviour = menu.contentHolder;
        if (behaviour == null || !GenericItemEmptying.canItemBeEmptied(behaviour.getWorld(), carried)) {
            return false;
        }

        Pair<FluidStack, ItemStack> emptyResult = GenericItemEmptying.emptyItem(behaviour.getWorld(), carried, true);
        if (emptyResult.getFirst().isEmpty()) {
            return false;
        }

        submit(menu, slot.getSlotIndex(), createFluidKey(emptyResult.getFirst()));
        return true;
    }

    private static ItemStack createFluidKey(FluidStack fluid) {
        ItemStack fluidTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(fluidTank, fluid.copyWithAmount(1));
        return fluidTank;
    }

    private static void submit(RedstoneRequesterMenu menu, int slotIndex, ItemStack stack) {
        menu.ghostInventory.setStackInSlot(slotIndex, stack);
        CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(stack, slotIndex));
    }

    private static void submit(FactoryPanelSetItemMenu menu, int slotIndex, ItemStack stack) {
        menu.ghostInventory.setStackInSlot(slotIndex, stack);
        CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(stack, slotIndex));
    }
}
