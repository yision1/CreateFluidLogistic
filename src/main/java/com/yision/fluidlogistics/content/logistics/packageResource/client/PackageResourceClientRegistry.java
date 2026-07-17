package com.yision.fluidlogistics.content.logistics.packageResource.client;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.client.FactoryPanelPreviewRenderer;
import com.yision.fluidlogistics.api.packager.client.StockKeeperAmountRenderer;
import com.yision.fluidlogistics.network.factoryPanel.FactoryPanelSetRequestSelectorPacket;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
@ApiStatus.Internal
public final class PackageResourceClientRegistry {
    private static final Map<ResourceLocation, StockKeeperAmountRenderer> STOCK_KEEPER_RENDERERS =
            new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, FactoryPanelPreviewRenderer> FACTORY_PANEL_RENDERERS =
            new ConcurrentHashMap<>();
    private static final Map<Item, Supplier<? extends Component>> REQUEST_SELECTOR_HINTS =
            new ConcurrentHashMap<>();

    private PackageResourceClientRegistry() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static void registerStockKeeperAmountRenderer(
            ResourceLocation resourceTypeId, StockKeeperAmountRenderer renderer) {
        Objects.requireNonNull(resourceTypeId, "resourceTypeId");
        Objects.requireNonNull(renderer, "renderer");
        if (STOCK_KEEPER_RENDERERS.putIfAbsent(resourceTypeId, renderer) != null) {
            throw new IllegalStateException(
                    "duplicate stock keeper amount renderer for package resource type: " + resourceTypeId);
        }
    }

    public static boolean hasStockKeeperAmountRenderer(ItemStack carrierStack) {
        return stockKeeperRendererFor(carrierStack) != null;
    }

    public static boolean tryRenderStockKeeperAmount(
            GuiGraphics graphics, ItemStack carrierStack, int amount) {
        Objects.requireNonNull(graphics, "graphics");
        if (amount <= 0) {
            return false;
        }
        StockKeeperAmountRenderer renderer = stockKeeperRendererFor(carrierStack);
        if (renderer == null) {
            return false;
        }
        renderer.render(graphics, amount);
        return true;
    }

    @Nullable
    public static StockKeeperAmountRenderer stockKeeperRendererFor(ItemStack carrierStack) {
        if (!PackageResources.isBootstrapped() || carrierStack == null || carrierStack.isEmpty()) {
            return null;
        }
        return PackageResources.findType(carrierStack)
                .map(type -> STOCK_KEEPER_RENDERERS.get(type.id()))
                .orElse(null);
    }

    public static void registerFactoryPanelPreviewRenderer(
            ResourceLocation resourceTypeId, FactoryPanelPreviewRenderer renderer) {
        Objects.requireNonNull(resourceTypeId, "resourceTypeId");
        Objects.requireNonNull(renderer, "renderer");
        if (FACTORY_PANEL_RENDERERS.putIfAbsent(resourceTypeId, renderer) != null) {
            throw new IllegalStateException(
                    "duplicate factory panel preview renderer for package resource type: " + resourceTypeId);
        }
    }

    public static boolean tryRenderFactoryPanelPreview(
            GuiGraphics graphics, ItemStack carrierStack, int x, int y) {
        Objects.requireNonNull(graphics, "graphics");
        FactoryPanelPreviewRenderer renderer = factoryPanelRendererFor(carrierStack);
        if (renderer == null) {
            return false;
        }
        renderer.render(graphics, carrierStack, x, y);
        return true;
    }

    private static FactoryPanelPreviewRenderer factoryPanelRendererFor(ItemStack carrierStack) {
        if (!PackageResources.isBootstrapped() || carrierStack == null || carrierStack.isEmpty()) {
            return null;
        }
        return PackageResources.findType(carrierStack)
                .map(type -> FACTORY_PANEL_RENDERERS.get(type.id()))
                .orElse(null);
    }

    public static void registerRequestSelectorHint(
            Item selectorItem, Supplier<? extends Component> hint) {
        Objects.requireNonNull(selectorItem, "selectorItem");
        Objects.requireNonNull(hint, "hint");
        if (selectorItem == Items.AIR) {
            throw new IllegalArgumentException("request selector hint item cannot be air");
        }
        if (REQUEST_SELECTOR_HINTS.putIfAbsent(selectorItem, hint) != null) {
            throw new IllegalStateException("duplicate request selector hint item: " + selectorItem);
        }
    }

    public static Optional<Component> getRequestSelectorHint(ItemStack selectorStack) {
        if (selectorStack == null || selectorStack.isEmpty()) {
            return Optional.empty();
        }
        Supplier<? extends Component> hint = REQUEST_SELECTOR_HINTS.get(selectorStack.getItem());
        if (hint == null || !PackageResources.isBootstrapped()
                || PackageResources.resolveRequestKey(selectorStack).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Objects.requireNonNull(
                hint.get(), "request selector hint for " + selectorStack.getItem()).copy());
    }

    static boolean shouldResolveRequestSelector(boolean altDown) {
        return altDown;
    }

    public static boolean trySubmitRequestSelector(
            GhostItemMenu<?> menu, int slotIndex, ItemStack selectorStack) {
        if (!shouldResolveRequestSelector(Screen.hasAltDown())) {
            return false;
        }
        var resolved = PackageResources.resolveRequestKey(selectorStack);
        if (resolved.isEmpty()) {
            return false;
        }
        submitGhostItem(menu, slotIndex, resolved.orElseThrow());
        return true;
    }

    public static boolean trySetFactoryPanelRequestSelector(
            FactoryPanelBehaviour behaviour, Player player, InteractionHand hand) {
        Objects.requireNonNull(behaviour, "behaviour");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "hand");
        if (!shouldResolveRequestSelector(Screen.hasAltDown())) {
            return false;
        }
        if (PackageResources.resolveRequestKey(player.getItemInHand(hand)).isEmpty()) {
            return false;
        }
        CatnipServices.NETWORK.sendToServer(
                new FactoryPanelSetRequestSelectorPacket(behaviour.getPanelPosition(), hand));
        return true;
    }

    public static void submitGhostItem(GhostItemMenu<?> menu, int slotIndex, ItemStack stack) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(stack, "stack");
        ItemStack submitted = stack.copyWithCount(1);
        menu.ghostInventory.setStackInSlot(slotIndex, submitted);
        CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(submitted, slotIndex));
    }
}
