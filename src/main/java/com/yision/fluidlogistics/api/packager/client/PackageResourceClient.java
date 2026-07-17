package com.yision.fluidlogistics.api.packager.client;

import java.util.Optional;
import java.util.function.Supplier;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.yision.fluidlogistics.content.logistics.packageResource.client.PackageResourceClientRegistry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PackageResourceClient {
    private PackageResourceClient() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static void registerStockKeeperAmountRenderer(
            ResourceLocation resourceTypeId, StockKeeperAmountRenderer renderer) {
        PackageResourceClientRegistry.registerStockKeeperAmountRenderer(resourceTypeId, renderer);
    }

    public static boolean hasStockKeeperAmountRenderer(ItemStack carrierStack) {
        return PackageResourceClientRegistry.hasStockKeeperAmountRenderer(carrierStack);
    }

    public static boolean tryRenderStockKeeperAmount(
            GuiGraphics graphics, ItemStack carrierStack, int amount) {
        return PackageResourceClientRegistry.tryRenderStockKeeperAmount(graphics, carrierStack, amount);
    }

    public static void registerFactoryPanelPreviewRenderer(
            ResourceLocation resourceTypeId, FactoryPanelPreviewRenderer renderer) {
        PackageResourceClientRegistry.registerFactoryPanelPreviewRenderer(resourceTypeId, renderer);
    }

    public static boolean tryRenderFactoryPanelPreview(
            GuiGraphics graphics, ItemStack carrierStack, int x, int y) {
        return PackageResourceClientRegistry.tryRenderFactoryPanelPreview(
                graphics, carrierStack, x, y);
    }

    public static void registerRequestSelectorHint(
            Item selectorItem, Supplier<? extends Component> hint) {
        PackageResourceClientRegistry.registerRequestSelectorHint(selectorItem, hint);
    }

    public static Optional<Component> getRequestSelectorHint(ItemStack selectorStack) {
        return PackageResourceClientRegistry.getRequestSelectorHint(selectorStack);
    }

    public static boolean trySubmitRequestSelector(
            GhostItemMenu<?> menu, int slotIndex, ItemStack selectorStack) {
        return PackageResourceClientRegistry.trySubmitRequestSelector(
                menu, slotIndex, selectorStack);
    }

    public static boolean trySetFactoryPanelRequestSelector(
            FactoryPanelBehaviour behaviour, Player player, InteractionHand hand) {
        return PackageResourceClientRegistry.trySetFactoryPanelRequestSelector(behaviour, player, hand);
    }

    public static void submitGhostItem(GhostItemMenu<?> menu, int slotIndex, ItemStack stack) {
        PackageResourceClientRegistry.submitGhostItem(menu, slotIndex, stack);
    }
}
