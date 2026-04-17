package com.yision.fluidlogistics.portableticker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.client.JechSearchBridge;
import com.yision.fluidlogistics.client.FluidTooltipHelper;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.item.PortableStockTickerItem;
import com.yision.fluidlogistics.network.PortableStockTickerHiddenCategoriesPacket;
import com.yision.fluidlogistics.network.PortableStockTickerOrderRequestPacket;
import com.yision.fluidlogistics.network.PortableStockTickerSaveAddressPacket;
import com.yision.fluidlogistics.registry.AllDataComponents;
import com.yision.fluidlogistics.render.FluidSlotAmountRenderer;
import com.yision.fluidlogistics.render.FluidSlotRenderer;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;

import com.yision.fluidlogistics.util.IFluidCraftableBigItemStack;

public class PortableStockTickerScreen extends AbstractSimiContainerScreen<PortableStockTickerMenu> {

    private record CategoryEntry(int sourceIndex, String name, int y, boolean hidden) {
        private CategoryEntry withY(int newY) {
            return new CategoryEntry(sourceIndex, name, newY, hidden);
        }
    }

    private static final AllGuiTextures HEADER = AllGuiTextures.STOCK_KEEPER_REQUEST_HEADER;
    private static final AllGuiTextures BODY = AllGuiTextures.STOCK_KEEPER_REQUEST_BODY;
    private static final AllGuiTextures FOOTER = AllGuiTextures.STOCK_KEEPER_REQUEST_FOOTER;

    private final int cols = 9;
    private final int rowHeight = 20;
    private final int colWidth = 20;
    private final Couple<Integer> noneHovered = Couple.create(-1, -1);
    private final Inventory playerInventory;
    private final Set<Integer> hiddenCategories;

    private LerpedFloat itemScroll = LerpedFloat.linear().startWithValue(0);
    private EditBox searchBox;
    private AddressEditBox addressBox;
    private String lastSyncedAddress = "";

    private int itemsX;
    private int itemsY;
    private int orderY;
    private int windowWidth;
    private int windowHeight;
    private int lastSeenVersion = -1;
    private int emptyTicks;
    private int successTicks;

    private boolean scrollHandleActive;
    private boolean ignoreTextInput;
    public boolean refreshSearchNextTick;
    public boolean moveToTopNextTick;

    private InventorySummary stockSnapshot = new InventorySummary();
    private InventorySummary forcedEntries = new InventorySummary();
    private List<List<BigItemStack>> currentItemSource = List.of();
    private List<List<BigItemStack>> displayedItems = new ArrayList<>();
    private List<CategoryEntry> categories = new ArrayList<>();
    private List<BigItemStack> itemsToOrder = new ArrayList<>();
    private List<CraftableBigItemStack> recipesToOrder = new ArrayList<>();
    private List<Rect2i> extraAreas = List.of();
    private boolean canRequestCraftingPackage;

    public PortableStockTickerScreen(PortableStockTickerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.playerInventory = playerInventory;
        ItemStack tickerStack = menu.getTickerStack();
        this.hiddenCategories = new HashSet<>(
                PortableStockTickerItem.loadHiddenCategoriesFromStack(tickerStack)
                        .getOrDefault(menu.player.getUUID(), List.of()));
        menu.screenReference = this;
    }

    @Override
    protected void init() {
        int appropriateHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 10;
        appropriateHeight -= Mth.positiveModulo(appropriateHeight - HEADER.getHeight() - FOOTER.getHeight(),
                BODY.getHeight());
        appropriateHeight = Math.min(appropriateHeight,
                HEADER.getHeight() + FOOTER.getHeight() + BODY.getHeight() * 17);

        setWindowSize(windowWidth = 226, windowHeight = appropriateHeight);
        super.init();
        clearWidgets();

        int x = getGuiLeft();
        int y = getGuiTop();

        itemsX = x + (windowWidth - cols * colWidth) / 2 + 1;
        itemsY = y + 33;
        orderY = y + windowHeight - 72;

        MutableComponent searchLabel = CreateLang.translateDirect("gui.stock_keeper.search_items");
        searchBox = new EditBox(new NoShadowFontWrapper(font), x + 71, y + 22, 100, 9, searchLabel);
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0x4A2D31);
        addWidget(searchBox);

        ItemStack tickerStack = menu.getTickerStack();
        boolean initial = addressBox == null;
        String savedAddress = PortableStockTickerItem.loadAddressFromStack(tickerStack);
        if (savedAddress.isEmpty() && !initial) {
            savedAddress = addressBox.getValue();
        }

        addressBox = new AddressEditBox(this, new NoShadowFontWrapper(font), x + 27, y + windowHeight - 36, 92, 10,
                true, "@" + playerInventory.player.getName().getString());
        addressBox.setTextColor(0x714A40);
        addressBox.setValue(savedAddress);
        lastSyncedAddress = savedAddress;
        addRenderableWidget(addressBox);

        extraAreas = List.of(new Rect2i(0, y + windowHeight - 65, x, 65));

        if (initial) {
            PortableStockTickerClientStorage.manualUpdate();
            playUiSound(SoundEvents.WOOD_HIT, 0.5f, 1.5f);
            playUiSound(SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        addressBox.tick();

        if (!Objects.equals(addressBox.getValue(), lastSyncedAddress)) {
            lastSyncedAddress = addressBox.getValue();
            CatnipServices.NETWORK.sendToServer(new PortableStockTickerSaveAddressPacket(lastSyncedAddress));
        }

        PortableStockTickerClientStorage.tick();

        if (!forcedEntries.isEmpty()) {
            for (BigItemStack stack : List.copyOf(forcedEntries.getStacks())) {
                int limitedAmount = -stack.count - 1;
                if (stockSnapshot.getCountOf(stack.stack) <= limitedAmount) {
                    forcedEntries.erase(stack.stack);
                }
            }
        }

        boolean allEmpty = displayedItems.stream().allMatch(List::isEmpty);
        emptyTicks = allEmpty ? emptyTicks + 1 : 0;
        successTicks = successTicks > 0 && itemsToOrder.isEmpty() ? successTicks + 1 : 0;

        if (PortableStockTickerClientStorage.getVersion() != lastSeenVersion) {
            lastSeenVersion = PortableStockTickerClientStorage.getVersion();
            rebuildStockSnapshot(PortableStockTickerClientStorage.getStacks());
            refreshSearchResults(false);
            revalidateOrders();
        }

        if (refreshSearchNextTick) {
            refreshSearchNextTick = false;
            refreshSearchResults(moveToTopNextTick);
        }

        itemScroll.tickChaser();
        if (Math.abs(itemScroll.getValue() - itemScroll.getChaseTarget()) < 1 / 16f) {
            itemScroll.setValue(itemScroll.getChaseTarget());
        }

        if (menu.getTickerStack().isEmpty()) {
            menu.player.closeContainer();
        }
    }

    private void rebuildStockSnapshot(List<BigItemStack> stacks) {
        List<BigItemStack> sorted = BigItemStack.duplicateWrappers(stacks);
        sorted.sort(BigItemStack.comparator());

        stockSnapshot = new InventorySummary();
        for (BigItemStack stack : sorted) {
            stockSnapshot.add(stack);
        }

        List<ItemStack> filterStacks = PortableStockTickerItem.loadCategoriesFromStack(menu.getTickerStack());
        List<List<BigItemStack>> categorized = new ArrayList<>();
        for (int i = 0; i < filterStacks.size(); i++) {
            categorized.add(new ArrayList<>());
        }
        List<BigItemStack> unsorted = new ArrayList<>();
        categorized.add(unsorted);

        for (BigItemStack bigStack : sorted) {
            boolean matched = false;
            for (int i = 0; i < filterStacks.size(); i++) {
                ItemStack filter = filterStacks.get(i);
                if (!filter.isEmpty() && com.simibubi.create.content.logistics.filter.FilterItemStack.of(filter)
                        .test(playerInventory.player.level(), bigStack.stack)) {
                    categorized.get(i).add(bigStack);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                unsorted.add(bigStack);
            }
        }

        currentItemSource = categorized;
    }

    private void refreshSearchResults(boolean scrollBackUp) {
        if (scrollBackUp) {
            itemScroll.startWithValue(0);
        }

        displayedItems = new ArrayList<>();
        categories = new ArrayList<>();
        if (currentItemSource.isEmpty()) {
            clampScrollBar();
            return;
        }

        List<ItemStack> filterStacks = PortableStockTickerItem.loadCategoriesFromStack(menu.getTickerStack());
        for (int i = 0; i < filterStacks.size(); i++) {
            ItemStack filter = filterStacks.get(i);
            categories.add(new CategoryEntry(i, filter.isEmpty() ? "" : filter.getHoverName().getString(), 0,
                    hiddenCategories.contains(i)));
            displayedItems.add(new ArrayList<>());
        }
        categories.add(new CategoryEntry(-1, CreateLang.translate("gui.stock_keeper.unsorted_category").string(), 0,
                hiddenCategories.contains(-1)));
        displayedItems.add(new ArrayList<>());

        String search = searchBox.getValue();
        boolean modSearch = search.startsWith("@");
        boolean tagSearch = search.startsWith("#");
        if (modSearch || tagSearch) {
            search = search.substring(1);
        }
        search = search.toLowerCase(Locale.ROOT);

        int categoryY = 0;
        boolean anyItemsInCategory = false;
        for (int categoryIndex = 0; categoryIndex < currentItemSource.size(); categoryIndex++) {
            List<BigItemStack> source = currentItemSource.get(categoryIndex);
            List<BigItemStack> filtered = displayedItems.get(categoryIndex);
            for (BigItemStack entry : source) {
                if (search.isBlank() || matchesSearch(entry.stack, search, modSearch, tagSearch)) {
                    filtered.add(entry);
                }
            }

            CategoryEntry category = categories.get(categoryIndex).withY(categoryY);
            categories.set(categoryIndex, category);
            if (filtered.isEmpty()) {
                continue;
            }
            if (categoryIndex < currentItemSource.size() - 1) {
                anyItemsInCategory = true;
            }

            categoryY += rowHeight;
            if (!category.hidden()) {
                categoryY += Mth.ceil(filtered.size() / (float) cols) * rowHeight;
            }
        }

        if (!anyItemsInCategory) {
            categories = List.of();
        }

        clampScrollBar();
        updateCraftableAmounts();
    }

    private boolean matchesSearch(ItemStack stack, String search, boolean modSearch, boolean tagSearch) {
        if (modSearch) {
            return JechSearchBridge.containsIgnoreCase(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace(),
                    search);
        }
        if (tagSearch) {
            return stack.getTags()
                    .anyMatch(key -> JechSearchBridge.containsIgnoreCase(key.location().toString(), search));
        }
        if (stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(stack);
            if (!fluid.isEmpty()
                    && JechSearchBridge.containsIgnoreCase(fluid.getHoverName().getString(), search)) {
                return true;
            }
        }
        return JechSearchBridge.containsIgnoreCase(stack.getHoverName().getString(), search)
                || JechSearchBridge.containsIgnoreCase(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),
                        search);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        if (minecraft != null && minecraft.screen != this) {
            return;
        }

        PoseStack ms = graphics.pose();
        float currentScroll = itemScroll.getValue(partialTicks);
        Couple<Integer> hoveredSlot = getHoveredSlot(mouseX, mouseY);

        int x = getGuiLeft();
        int y = getGuiTop();

        HEADER.render(graphics, x - 15, y);
        y += HEADER.getHeight();
        for (int i = 0; i < (windowHeight - HEADER.getHeight() - FOOTER.getHeight()) / BODY.getHeight(); i++) {
            BODY.render(graphics, x - 15, y);
            y += BODY.getHeight();
        }
        FOOTER.render(graphics, x - 15, y);
        y = getGuiTop();

        if (addressBox.getValue().isBlank() && !addressBox.isFocused()) {
            graphics.drawString(font,
                    CreateLang.translate("gui.stock_keeper.package_adress").style(ChatFormatting.ITALIC).component(),
                    addressBox.getX(), addressBox.getY(), 0xff_CDBCA8, false);
        }

        ms.pushPose();
        ms.translate(x - 50, y + windowHeight - 70, -100);
        ms.scale(3.5f, 3.5f, 3.5f);
        ItemStack tickerStack = getTickerDisplayStack();
        if (!tickerStack.isEmpty()) {
            GuiGameElement.of(tickerStack).render(graphics);
        }
        ms.popPose();

        for (int index = 0; index < cols; index++) {
            if (itemsToOrder.size() <= index) {
                break;
            }
            boolean hovered = hoveredSlot.getFirst() == -1 && hoveredSlot.getSecond() == index;
            ms.pushPose();
            ms.translate(itemsX + index * colWidth, orderY, 0);
            renderItemEntry(graphics, itemsToOrder.get(index), hovered, true);
            ms.popPose();
        }

        if (!recipesToOrder.isEmpty()) {
            int jeiX = x + (windowWidth - colWidth * recipesToOrder.size()) / 2 + 1;
            int jeiY = orderY - 31;
            ms.pushPose();
            ms.translate(jeiX, jeiY, 200);
            int xOffset = -3;
            AllGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_LEFT.render(graphics, xOffset, -3);
            xOffset += 10;
            for (int i = 0; i <= (recipesToOrder.size() - 1) * 5; i++) {
                AllGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_MIDDLE.render(graphics, xOffset, -3);
                xOffset += 4;
            }
            AllGuiTextures.STOCK_KEEPER_REQUEST_BLUEPRINT_RIGHT.render(graphics, xOffset, -3);

            for (int index = 0; index < recipesToOrder.size(); index++) {
                boolean hovered = hoveredSlot.getFirst() == -2 && hoveredSlot.getSecond() == index;
                ms.pushPose();
                ms.translate(index * colWidth, 0, 0);
                renderItemEntry(graphics, recipesToOrder.get(index), hovered, true);
                ms.popPose();
            }
            ms.popPose();
        }

        if (itemsToOrder.size() > 9) {
            graphics.drawString(font, Component.literal("[+" + (itemsToOrder.size() - 9) + "]"), x + windowWidth - 40,
                    orderY + 21, 0xF8F8EC);
        }

        boolean justSent = itemsToOrder.isEmpty() && successTicks > 0;
        if (isConfirmHovered(mouseX, mouseY) && !justSent) {
            AllGuiTextures.STOCK_KEEPER_REQUEST_SEND_HOVER.render(graphics, x + windowWidth - 81,
                    y + windowHeight - 41);
        }

        MutableComponent headerTitle = CreateLang.translate("gui.stock_keeper.title").component();
        graphics.drawString(font, headerTitle, x + windowWidth / 2 - font.width(headerTitle) / 2, y + 4, 0x714A40,
                false);
        MutableComponent sendLabel = CreateLang.translate("gui.stock_keeper.send").component();

        if (justSent) {
            float alpha = Mth.clamp((successTicks + partialTicks - 5f) / 5f, 0f, 1f);
            ms.pushPose();
            ms.translate(alpha * alpha * 50, 0, 0);
            if (successTicks < 10) {
                graphics.drawString(font, sendLabel, x + windowWidth - 42 - font.width(sendLabel) / 2,
                        y + windowHeight - 35, new Color(0x252525).setAlpha(1 - alpha * alpha).getRGB(), false);
            }
            ms.popPose();
        } else {
            graphics.drawString(font, sendLabel, x + windowWidth - 42 - font.width(sendLabel) / 2,
                    y + windowHeight - 35, 0x252525, false);
        }

        if (justSent) {
            Component sentMsg = CreateLang.translateDirect("gui.stock_keeper.request_sent");
            float alpha = Mth.clamp((successTicks + partialTicks - 10f) / 5f, 0f, 1f);
            int msgX = x + windowWidth / 2 - (font.width(sentMsg) + 10) / 2;
            int msgY = orderY + 5;
            if (alpha > 0) {
                int color = new Color(0x8C5D4B).setAlpha(alpha).getRGB();
                int width = font.width(sentMsg) + 14;
                AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_L.render(graphics, msgX - 8, msgY - 4);
                UIRenderHelper.drawStretched(graphics, msgX, msgY - 4, width, 16, 0,
                        AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_M);
                AllGuiTextures.STOCK_KEEPER_REQUEST_BANNER_R.render(graphics, msgX + font.width(sentMsg) + 10,
                        msgY - 4);
                graphics.drawString(font, sentMsg, msgX + 5, msgY, color, false);
            }
        }

        int itemWindowX = x + 21;
        int itemWindowX2 = itemWindowX + 184;
        int itemWindowY = y + 17;
        int itemWindowY2 = y + windowHeight - 80;

        graphics.enableScissor(itemWindowX - 5, itemWindowY, itemWindowX2 + 10, itemWindowY2);
        ms.pushPose();
        ms.translate(0, -currentScroll * rowHeight, 0);

        for (int sliceY = -2; sliceY < getMaxScroll() * rowHeight + windowHeight - 72;
                sliceY += AllGuiTextures.STOCK_KEEPER_REQUEST_BG.getHeight()) {
            if (sliceY - currentScroll * rowHeight < -20) {
                continue;
            }
            if (sliceY - currentScroll * rowHeight > windowHeight - 72) {
                continue;
            }
            AllGuiTextures.STOCK_KEEPER_REQUEST_BG.render(graphics, x + 22, y + sliceY + 18);
        }

        AllGuiTextures.STOCK_KEEPER_REQUEST_SEARCH.render(graphics, x + 42, searchBox.getY() - 5);
        searchBox.render(graphics, mouseX, mouseY, partialTicks);
        if (searchBox.getValue().isBlank() && !searchBox.isFocused()) {
            graphics.drawString(font, searchBox.getMessage(),
                    x + windowWidth / 2 - font.width(searchBox.getMessage()) / 2, searchBox.getY(), 0xff4A2D31,
                    false);
        }

        boolean allEmpty = displayedItems.stream().allMatch(List::isEmpty);
        if (allEmpty) {
            Component msg = getTroubleshootingMessage();
            float alpha = Mth.clamp((emptyTicks - 10f) / 5f, 0f, 1f);
            if (alpha > 0) {
                List<FormattedCharSequence> split = font.split(msg, 160);
                for (int i = 0; i < split.size(); i++) {
                    FormattedCharSequence line = split.get(i);
                    int lineWidth = font.width(line);
                    graphics.drawString(font, line, x + windowWidth / 2 - lineWidth / 2 + 1,
                            itemsY + 20 + 1 + i * (font.lineHeight + 1),
                            new Color(0x4A2D31).setAlpha(alpha).getRGB(), false);
                    graphics.drawString(font, line, x + windowWidth / 2 - lineWidth / 2,
                            itemsY + 20 + i * (font.lineHeight + 1),
                            new Color(0xF8F8EC).setAlpha(alpha).getRGB(), false);
                }
            }
        }

        for (int categoryIndex = 0; categoryIndex < displayedItems.size(); categoryIndex++) {
            List<BigItemStack> category = displayedItems.get(categoryIndex);
            if (category.isEmpty()) {
                continue;
            }

            CategoryEntry categoryEntry = categories.isEmpty() ? null : categories.get(categoryIndex);
            int categoryY = categories.isEmpty() ? 0 : categoryEntry.y();

            if (!categories.isEmpty()) {
                (categoryEntry.hidden() ? AllGuiTextures.STOCK_KEEPER_CATEGORY_HIDDEN
                        : AllGuiTextures.STOCK_KEEPER_CATEGORY_SHOWN).render(graphics, itemsX, itemsY + categoryY + 6);
                graphics.drawString(font, categoryEntry.name(), itemsX + 10, itemsY + categoryY + 8, 0x4A2D31, false);
                graphics.drawString(font, categoryEntry.name(), itemsX + 9, itemsY + categoryY + 7, 0xF8F8EC, false);
                if (categoryEntry.hidden()) {
                    continue;
                }
            }

            for (int index = 0; index < category.size(); index++) {
                int slotY = itemsY + categoryY + (categories.isEmpty() ? 4 : rowHeight) + (index / cols) * rowHeight;
                float cullY = slotY - currentScroll * rowHeight;
                if (cullY < y) {
                    continue;
                }
                if (cullY > y + windowHeight - 72) {
                    break;
                }

                boolean hovered = hoveredSlot.getFirst() == categoryIndex && hoveredSlot.getSecond() == index;
                ms.pushPose();
                ms.translate(itemsX + (index % cols) * colWidth, slotY, 0);
                renderItemEntry(graphics, category.get(index), hovered, false);
                ms.popPose();
            }
        }
        ms.popPose();
        graphics.disableScissor();

        int windowH = windowHeight - 92;
        int totalH = getMaxScroll() * rowHeight + windowH;
        int barSize = Math.max(5, Mth.floor((float) windowH / totalH * (windowH - 2)));
        if (barSize < windowH - 2) {
            int barX = itemsX + cols * colWidth;
            int barY = y + 15;
            ms.pushPose();
            ms.translate(0, (currentScroll * rowHeight) / totalH * (windowH - 2), 0);
            AllGuiTextures pad = AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_PAD;
            graphics.blit(pad.location, barX, barY, pad.getWidth(), barSize, pad.getStartX(), pad.getStartY(),
                    pad.getWidth(), pad.getHeight(), 256, 256);
            AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_TOP.render(graphics, barX, barY);
            if (barSize > 16) {
                AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_MID.render(graphics, barX, barY + barSize / 2 - 4);
            }
            AllGuiTextures.STOCK_KEEPER_REQUEST_SCROLL_BOT.render(graphics, barX, barY + barSize - 5);
            ms.popPose();
        }
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
        if (!itemScroll.settled()) {
            return;
        }

        Couple<Integer> hoveredSlot = getHoveredSlot(mouseX, mouseY);
        if (hoveredSlot == noneHovered) {
            return;
        }

        boolean recipeHovered = hoveredSlot.getFirst() == -2;
        BigItemStack entry = getEntryAt(hoveredSlot);

        ItemStack stack = entry.stack;
        if (recipeHovered) {
            ArrayList<Component> lines = stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)
                    ? new ArrayList<>(FluidTooltipHelper.getTooltipLines(CompressedTankItem.getFluid(stack)))
                    : new ArrayList<>(stack.getTooltipLines(TooltipContext.of(minecraft.level), minecraft.player,
                            TooltipFlag.NORMAL));
            if (!lines.isEmpty()) {
                lines.set(0, CreateLang.translateDirect("gui.stock_keeper.craft", lines.getFirst().copy()));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }

        if (stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(stack);
            if (!fluid.isEmpty()) {
                FluidTooltipHelper.renderTooltip(graphics, font, fluid, mouseX, mouseY);
                return;
            }
        }

        graphics.renderTooltip(font, stack, mouseX, mouseY);
    }

    private void renderItemEntry(GuiGraphics graphics, BigItemStack entry, boolean isStackHovered,
            boolean isRenderingOrders) {
        int customCount = entry.count;
        if (!isRenderingOrders) {
            BigItemStack order = getOrderForItem(entry.stack);
            if (entry.count < BigItemStack.INF) {
                int forcedCount = forcedEntries.getCountOf(entry.stack);
                if (forcedCount != 0) {
                    customCount = Math.min(customCount, -forcedCount - 1);
                }
                if (order != null) {
                    customCount -= order.count;
                }
                customCount = Math.max(0, customCount);
            }
            AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, 0, 0);
        }

        PoseStack ms = graphics.pose();
        ms.pushPose();
        float hoverScale = isStackHovered ? 1.075f : 1f;
        ms.translate((colWidth - 18) / 2.0, (rowHeight - 18) / 2.0, 0);
        ms.translate(9, 9, 0);
        ms.scale(hoverScale, hoverScale, hoverScale);
        ms.translate(-9, -9, 0);

        ItemStack stack = entry.stack;
        if (stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(stack);
            if (!fluid.isEmpty() && (customCount != 0 || isRenderingOrders)) {
                FluidSlotRenderer.renderFluidSlot(graphics, 0, 0, fluid);
            }
        } else if (customCount != 0 || isRenderingOrders) {
            GuiGameElement.of(stack).render(graphics);
        }
        ms.popPose();

        ms.pushPose();
        ms.translate(0, 0, 190);
        if (!(stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack))
                && (customCount != 0 || isRenderingOrders)) {
            graphics.renderItemDecorations(font, stack, 1, 1, "");
        }
        ms.translate(0, 0, 10);

        if (stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)) {
            if (customCount > 0 || isRenderingOrders) {
                FluidSlotAmountRenderer.renderInStockKeeper(graphics, customCount);
            }
            ms.popPose();
            return;
        }

        if (customCount > 1 || entry.count >= BigItemStack.INF) {
            drawItemCount(graphics, customCount);
        }
        ms.popPose();
    }

    private void drawItemCount(GuiGraphics graphics, int count) {
        String text = count >= 1_000_000 ? (count / 1_000_000) + "m"
                : count >= 10_000 ? (count / 1000) + "k"
                : count >= 1000 ? ((count * 10) / 1000) / 10f + "k"
                : count >= 100 ? Integer.toString(count)
                : " " + count;

        if (count >= BigItemStack.INF) {
            text = "+";
        }
        if (text.isBlank()) {
            return;
        }

        int x = (int) Math.floor(-text.length() * 2.5);
        for (char c : text.toCharArray()) {
            int index = c - '0';
            int xOffset = index * 6;
            int spriteWidth = AllGuiTextures.NUMBERS.getWidth();

            switch (c) {
                case ' ' -> {
                    x += 4;
                    continue;
                }
                case '.' -> {
                    spriteWidth = 3;
                    xOffset = 60;
                }
                case 'k' -> xOffset = 64;
                case 'm' -> {
                    spriteWidth = 7;
                    xOffset = 70;
                }
                case '+' -> {
                    spriteWidth = 9;
                    xOffset = 84;
                }
                default -> {
                }
            }

            RenderSystem.enableBlend();
            graphics.blit(AllGuiTextures.NUMBERS.location, 14 + x, 10, 0,
                    AllGuiTextures.NUMBERS.getStartX() + xOffset, AllGuiTextures.NUMBERS.getStartY(),
                    spriteWidth, AllGuiTextures.NUMBERS.getHeight(), 256, 256);
            x += spriteWidth - 1;
        }
    }

    private void revalidateOrders() {
        Set<BigItemStack> invalid = new HashSet<>(itemsToOrder);
        for (BigItemStack entry : itemsToOrder) {
            entry.count = Math.min(stockSnapshot.getCountOf(entry.stack), entry.count);
            if (entry.count > 0) {
                invalid.remove(entry);
            }
        }
        itemsToOrder.removeAll(invalid);
        updateCraftableAmounts();
    }

    @Nullable
    private BigItemStack getOrderForItem(ItemStack stack) {
        for (BigItemStack entry : itemsToOrder) {
            if (ItemStack.isSameItemSameComponents(stack, entry.stack)) {
                return entry;
            }
        }
        return null;
    }

    private Couple<Integer> getHoveredSlot(int x, int y) {
        x += 1;
        if (x < itemsX || x >= itemsX + cols * colWidth) {
            return noneHovered;
        }

        if (y >= orderY && y < orderY + rowHeight) {
            int col = (x - itemsX) / colWidth;
            if (col >= 0 && col < itemsToOrder.size()) {
                return Couple.create(-1, col);
            }
            return noneHovered;
        }

        if (y >= orderY - 31 && y < orderY - 31 + rowHeight) {
            int jeiX = getGuiLeft() + (windowWidth - colWidth * recipesToOrder.size()) / 2 + 1;
            int col = Mth.floorDiv(x - jeiX, colWidth);
            if (col >= 0 && col < recipesToOrder.size()) {
                return Couple.create(-2, col);
            }
        }

        if (y < getGuiTop() + 16 || y > getGuiTop() + windowHeight - 80 || !itemScroll.settled()) {
            return noneHovered;
        }

        int localY = y - itemsY;
        for (int categoryIndex = 0; categoryIndex < displayedItems.size(); categoryIndex++) {
            CategoryEntry entry = categories.isEmpty() ? new CategoryEntry(0, "", 0, false) : categories.get(categoryIndex);
            if (entry.hidden()) {
                continue;
            }

            int row = Mth.floor((localY - (categories.isEmpty() ? 4 : rowHeight) - entry.y()) / (float) rowHeight
                    + itemScroll.getChaseTarget());
            int col = (x - itemsX) / colWidth;
            int slot = row * cols + col;
            if (slot < 0) {
                return noneHovered;
            }
            if (slot < displayedItems.get(categoryIndex).size()) {
                return Couple.create(categoryIndex, slot);
            }
        }

        return noneHovered;
    }

    public Optional<Pair<Object, Rect2i>> getHoveredIngredient(int mouseX, int mouseY) {
        Couple<Integer> hoveredSlot = getHoveredSlot(mouseX, mouseY);
        if (hoveredSlot == noneHovered) {
            return Optional.empty();
        }

        int index = hoveredSlot.getSecond();
        boolean recipeHovered = hoveredSlot.getFirst() == -2;
        boolean orderHovered = hoveredSlot.getFirst() == -1;

        int x;
        int y;
        BigItemStack entry;
        if (recipeHovered) {
            int jeiX = getGuiLeft() + (windowWidth - colWidth * recipesToOrder.size()) / 2 + 1;
            x = jeiX + index * colWidth;
            y = orderY - 31;
            entry = recipesToOrder.get(index);
        } else if (orderHovered) {
            x = itemsX + index * colWidth;
            y = orderY;
            entry = itemsToOrder.get(index);
        } else {
            int categoryIndex = hoveredSlot.getFirst();
            int categoryY = categories.isEmpty() ? 0 : categories.get(categoryIndex).y();
            x = itemsX + (index % cols) * colWidth;
            y = itemsY + categoryY + (categories.isEmpty() ? 4 : rowHeight) + (index / cols) * rowHeight;
            entry = displayedItems.get(categoryIndex).get(index);
        }

        Object ingredient = entry.stack.copy();
        if (entry.stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(entry.stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(entry.stack);
            if (!fluid.isEmpty()) {
                ingredient = fluid.copy();
            }
        }

        return Optional.of(Pair.of(ingredient, new Rect2i(x, y, 18, 18)));
    }

    private BigItemStack getEntryAt(Couple<Integer> hoveredSlot) {
        int slot = hoveredSlot.getSecond();
        return hoveredSlot.getFirst() == -2 ? recipesToOrder.get(slot)
                : hoveredSlot.getFirst() == -1 ? itemsToOrder.get(slot)
                : displayedItems.get(hoveredSlot.getFirst()).get(slot);
    }

    private boolean isConfirmHovered(int mouseX, int mouseY) {
        int confirmX = getGuiLeft() + 143;
        int confirmY = getGuiTop() + windowHeight - 39;
        return mouseX >= confirmX && mouseX < confirmX + 78 && mouseY >= confirmY && mouseY < confirmY + 18;
    }

    private Component getTroubleshootingMessage() {
        if (currentItemSource.isEmpty() && lastSeenVersion < 0) {
            return CreateLang.translate("gui.stock_keeper.checking_stocks").component();
        }
        if (stockSnapshot.isEmpty()) {
            return CreateLang.translate("gui.stock_keeper.inventories_empty").component();
        }
        return CreateLang.translate("gui.stock_keeper.no_search_results").component();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean lmb = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean rmb = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;

        if (rmb && searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setValue("");
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
            searchBox.setFocused(true);
            return true;
        }

        if (addressBox.isFocused()) {
            boolean result = addressBox.mouseClicked(mouseX, mouseY, button);
            if (addressBox.isHovered() || result) {
                return result;
            }
            addressBox.setFocused(false);
        }
        if (searchBox.isFocused()) {
            if (searchBox.isHovered()) {
                return searchBox.mouseClicked(mouseX, mouseY, button);
            }
            searchBox.setFocused(false);
        }

        int barX = itemsX + cols * colWidth - 1;
        if (getMaxScroll() > 0 && lmb && mouseX > barX && mouseX <= barX + 8 && mouseY > getGuiTop() + 15
                && mouseY < getGuiTop() + windowHeight - 82) {
            scrollHandleActive = true;
            if (minecraft.isWindowActive()) {
                GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), 208897, GLFW.GLFW_CURSOR_HIDDEN);
            }
            return true;
        }

        Couple<Integer> hoveredSlot = getHoveredSlot((int) mouseX, (int) mouseY);

        if (lmb && isConfirmHovered((int) mouseX, (int) mouseY)) {
            sendIt();
            playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
            return true;
        }

        int localY = (int) (mouseY - itemsY);
        if (itemScroll.settled() && lmb && !categories.isEmpty() && mouseX >= itemsX
                && mouseX <= itemsX + cols * colWidth) {
            for (int categoryIndex = 0; categoryIndex < categories.size(); categoryIndex++) {
                CategoryEntry category = categories.get(categoryIndex);
                if (Mth.floor((localY - category.y()) / (float) rowHeight + itemScroll.getChaseTarget()) != 0) {
                    continue;
                }
                if (displayedItems.get(categoryIndex).isEmpty()) {
                    continue;
                }

                int index = category.sourceIndex();
                if (!category.hidden()) {
                    hiddenCategories.add(index);
                    playUiSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1f, 1.5f);
                } else {
                    hiddenCategories.remove(index);
                    playUiSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1f, 0.675f);
                }

                refreshSearchNextTick = true;
                moveToTopNextTick = false;
                return true;
            }
        }

        if (hoveredSlot == noneHovered || (!lmb && !rmb)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        boolean orderClicked = hoveredSlot.getFirst() == -1;
        boolean recipeClicked = hoveredSlot.getFirst() == -2;
        BigItemStack entry = getEntryAt(hoveredSlot);

        if (recipeClicked && entry instanceof CraftableBigItemStack craftableEntry) {
            handleRecipeInteraction(craftableEntry, rmb ? -1 : 1,
                    isCustomFluidCraftable(craftableEntry)
                            ? getRecipeStepAmount((IFluidCraftableBigItemStack) craftableEntry)
                            : hasShiftDown() ? craftableEntry.stack.getMaxStackSize() : hasControlDown() ? 10 : 1);
            return true;
        }

        changeDirectOrder(entry, orderClicked, (rmb || orderClicked ? -1 : 1)
                * getTransferAmount(entry.stack, hasShiftDown(), hasControlDown()), entry.count, true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollHandleActive) {
            scrollHandleActive = false;
            if (minecraft.isWindowActive()) {
                GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), 208897, GLFW.GLFW_CURSOR_NORMAL);
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (addressBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }

        Couple<Integer> hoveredSlot = getHoveredSlot((int) mouseX, (int) mouseY);
        boolean noHover = hoveredSlot == noneHovered;
        if (noHover || hoveredSlot.getFirst() >= 0 && !hasShiftDown() && getMaxScroll() != 0) {
            int direction = (int) (Math.ceil(Math.abs(scrollY)) * -Math.signum(scrollY));
            float newTarget = Mth.clamp(Math.round(itemScroll.getChaseTarget() + direction), 0, getMaxScroll());
            itemScroll.chase(newTarget, 0.5, Chaser.EXP);
            return true;
        }

        boolean orderClicked = hoveredSlot.getFirst() == -1;
        boolean recipeClicked = hoveredSlot.getFirst() == -2;
        BigItemStack entry = getEntryAt(hoveredSlot);
        boolean remove = scrollY < 0;

        if (recipeClicked && entry instanceof CraftableBigItemStack craftableEntry) {
            handleRecipeInteraction(craftableEntry, remove ? -1 : 1,
                    Mth.ceil(Math.abs(scrollY)) * (isCustomFluidCraftable(craftableEntry)
                            ? getRecipeStepAmount((IFluidCraftableBigItemStack) craftableEntry)
                            : hasControlDown() ? 10 : 1));
            return true;
        }

        changeDirectOrder(entry, orderClicked,
                (remove ? -1 : 1) * Mth.ceil(Math.abs(scrollY)) * getTransferAmount(entry.stack, false, hasControlDown()),
                stockSnapshot.getCountOf(entry.stack), false);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !scrollHandleActive) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        Window window = minecraft.getWindow();
        double scaleX = window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double scaleY = window.getGuiScaledHeight() / (double) window.getScreenHeight();

        int windowH = windowHeight - 92;
        int totalH = getMaxScroll() * rowHeight + windowH;
        int barSize = Math.max(5, Mth.floor((float) windowH / totalH * (windowH - 2)));
        if (barSize >= windowH - 2) {
            return true;
        }

        int barX = itemsX + cols * colWidth;
        double target = (mouseY - getGuiTop() - 15 - barSize / 2.0) * totalH / (windowH - 2) / rowHeight;
        itemScroll.chase(Mth.clamp(target, 0, getMaxScroll()), 0.8, Chaser.EXP);

        if (minecraft.isWindowActive()) {
            double forceX = (barX + 2) / scaleX;
            double forceY = Mth.clamp(mouseY, getGuiTop() + 15 + barSize / 2,
                    getGuiTop() + 15 + windowH - barSize / 2) / scaleY;
            GLFW.glfwSetCursorPos(window.getWindow(), forceX, forceY);
        }

        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (ignoreTextInput) {
            return false;
        }
        if (addressBox.isFocused() && addressBox.charTyped(codePoint, modifiers)) {
            return true;
        }

        String previous = searchBox.getValue();
        if (!searchBox.charTyped(codePoint, modifiers)) {
            return false;
        }
        if (!Objects.equals(previous, searchBox.getValue())) {
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ignoreTextInput = false;
        if (!addressBox.isFocused() && !searchBox.isFocused() && minecraft.options.keyChat.matches(keyCode, scanCode)) {
            ignoreTextInput = true;
            searchBox.setFocused(true);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER && searchBox.isFocused()) {
            searchBox.setFocused(false);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER && hasShiftDown()) {
            sendIt();
            return true;
        }

        if (addressBox.isFocused() && addressBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        String previous = searchBox.getValue();
        if (!searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return searchBox.isFocused() && searchBox.isVisible() && keyCode != GLFW.GLFW_KEY_ESCAPE
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!Objects.equals(previous, searchBox.getValue())) {
            refreshSearchNextTick = true;
            moveToTopNextTick = true;
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        ignoreTextInput = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        if (!Objects.equals(addressBox.getValue(), lastSyncedAddress)) {
            CatnipServices.NETWORK.sendToServer(new PortableStockTickerSaveAddressPacket(addressBox.getValue()));
        }
        CatnipServices.NETWORK.sendToServer(
                new PortableStockTickerHiddenCategoriesPacket(new ArrayList<>(hiddenCategories)));
        super.removed();
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return extraAreas;
    }

    private void sendIt() {
        revalidateOrders();
        if (itemsToOrder.isEmpty()) {
            return;
        }

        forcedEntries = new InventorySummary();
        for (BigItemStack toOrder : itemsToOrder) {
            int countOf = stockSnapshot.getCountOf(toOrder.stack);
            if (countOf >= BigItemStack.INF) {
                continue;
            }
            forcedEntries.add(toOrder.stack.copy(), -1 - Math.max(0, countOf - toOrder.count));
        }

        PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(itemsToOrder);
        if (canRequestCraftingPackage && !recipesToOrder.isEmpty()) {
            List<PackageOrderWithCrafts.CraftingEntry> craftList = new ArrayList<>();
            for (CraftableBigItemStack craftableEntry : recipesToOrder) {
                if (!(craftableEntry.recipe instanceof CraftingRecipe recipe)) {
                    continue;
                }

                int outputCount = craftableEntry.getOutputCount(menu.player.level());
                if (outputCount <= 0) {
                    continue;
                }

                int craftedCount = 0;
                int targetCount = craftableEntry.count / outputCount;
                List<BigItemStack> mutableOrder = BigItemStack.duplicateWrappers(itemsToOrder);

                while (craftedCount < targetCount) {
                    PackageOrder pattern = new PackageOrder(
                            FactoryPanelScreen.convertRecipeToPackageOrderContext(recipe, mutableOrder, true));
                    int maxCrafts = targetCount - craftedCount;
                    int availableCrafts = 0;
                    boolean itemsExhausted = false;

                    outer:
                    while (availableCrafts < maxCrafts && !itemsExhausted) {
                        List<BigItemStack> previousSnapshot = BigItemStack.duplicateWrappers(mutableOrder);
                        itemsExhausted = true;

                        for (BigItemStack patternStack : pattern.stacks()) {
                            if (patternStack.stack.isEmpty()) {
                                continue;
                            }

                            boolean matched = false;
                            for (BigItemStack ordered : mutableOrder) {
                                if (!ItemStack.isSameItemSameComponents(ordered.stack, patternStack.stack)
                                        || ordered.count == 0) {
                                    continue;
                                }
                                ordered.count -= 1;
                                itemsExhausted = false;
                                matched = true;
                                break;
                            }

                            if (!matched) {
                                mutableOrder = previousSnapshot;
                                break outer;
                            }
                        }

                        availableCrafts++;
                    }

                    if (availableCrafts == 0) {
                        break;
                    }

                    craftList.add(new PackageOrderWithCrafts.CraftingEntry(pattern, availableCrafts));
                    craftedCount += availableCrafts;
                }
            }

            if (!craftList.isEmpty()) {
                order = new PackageOrderWithCrafts(order.orderedStacks(), craftList);
            }
        }

        CatnipServices.NETWORK.sendToServer(new PortableStockTickerOrderRequestPacket(order, addressBox.getValue()));
        itemsToOrder = new ArrayList<>();
        recipesToOrder = new ArrayList<>();
        successTicks = 1;
        PortableStockTickerClientStorage.manualUpdate();
    }

    public InventorySummary stockSnapshot() {
        return stockSnapshot;
    }

    public List<BigItemStack> itemsToOrder() {
        return itemsToOrder;
    }

    public List<CraftableBigItemStack> recipesToOrder() {
        return recipesToOrder;
    }

    public boolean hasRecipeEntry(Recipe<?> recipe) {
        for (CraftableBigItemStack entry : recipesToOrder) {
            if (entry.recipe == recipe) {
                return true;
            }
        }
        return false;
    }

    public void clearSearchAndRefresh() {
        searchBox.setValue("");
        refreshSearchNextTick = true;
        moveToTopNextTick = true;
    }

    private void handleRecipeInteraction(CraftableBigItemStack craftableEntry, int direction, int amount) {
        if (direction < 0 && craftableEntry.count == 0) {
            recipesToOrder.remove(craftableEntry);
            updateCraftableAmounts();
            return;
        }

        requestCraftable(craftableEntry, direction * amount);
    }

    private void changeDirectOrder(BigItemStack entry, boolean orderClicked, int delta, int maxAvailable,
            boolean playFeedback) {
        boolean removing = delta < 0;
        BigItemStack existingOrder = orderClicked ? entry : getOrderForItem(entry.stack);
        if (existingOrder == null) {
            if (itemsToOrder.size() >= cols || removing) {
                return;
            }
            itemsToOrder.add(existingOrder = new BigItemStack(entry.stack.copyWithCount(1), 0));
            if (playFeedback) {
                playUiSound(SoundEvents.WOOL_STEP, 0.75f, 1.2f);
                playUiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 0.8f);
            }
        }

        int previous = existingOrder.count;
        if (removing) {
            existingOrder.count = previous + delta;
            if (existingOrder.count <= 0) {
                itemsToOrder.remove(existingOrder);
                if (playFeedback) {
                    playUiSound(SoundEvents.WOOL_STEP, 0.75f, 1.8f);
                    playUiSound(SoundEvents.BAMBOO_WOOD_STEP, 0.75f, 1.8f);
                }
            }
            updateCraftableAmounts();
            return;
        }

        existingOrder.count = previous + Math.min(delta, maxAvailable - previous);
        updateCraftableAmounts();
    }

    public void requestCraftable(CraftableBigItemStack craftableEntry, int requestedDifference) {
        if (hasCustomRecipeData(craftableEntry)) {
            handleCustomFluidCraftableRequest(craftableEntry, requestedDifference);
            return;
        }

        boolean takeOrdersAway = requestedDifference < 0;
        if (takeOrdersAway) {
            requestedDifference = Math.max(-craftableEntry.count, requestedDifference);
        }
        if (requestedDifference == 0) {
            return;
        }

        InventorySummary availableItems = stockSnapshot;
        Function<ItemStack, Integer> countModifier = stack -> {
            BigItemStack ordered = getOrderForItem(stack);
            return ordered == null ? 0 : -ordered.count;
        };

        if (takeOrdersAway) {
            availableItems = new InventorySummary();
            for (BigItemStack ordered : itemsToOrder) {
                availableItems.add(ordered.stack, ordered.count);
            }
            countModifier = stack -> 0;
        }

        Pair<Integer, List<List<BigItemStack>>> craftingResult = maxCraftable(craftableEntry, availableItems,
                countModifier, takeOrdersAway ? -1 : 9 - itemsToOrder.size());
        int outputCount = craftableEntry.getOutputCount(menu.player.level());
        int adjustedAmount = Mth.ceil(Math.abs(requestedDifference) / (float) outputCount) * outputCount;
        int maxCraftable = Math.min(adjustedAmount, craftingResult.getFirst());
        if (maxCraftable == 0) {
            return;
        }

        craftableEntry.count += takeOrdersAway ? -maxCraftable : maxCraftable;

        for (List<BigItemStack> ingredients : craftingResult.getSecond()) {
            int remaining = maxCraftable / outputCount;
            for (BigItemStack ingredient : ingredients) {
                if (remaining <= 0) {
                    break;
                }

                int toTransfer = Math.min(remaining, ingredient.count);
                BigItemStack order = getOrderForItem(ingredient.stack);
                if (takeOrdersAway) {
                    if (order != null) {
                        order.count -= toTransfer;
                        if (order.count == 0) {
                            itemsToOrder.remove(order);
                        }
                    }
                } else {
                    if (order == null) {
                        order = new BigItemStack(ingredient.stack.copyWithCount(1), 0);
                        itemsToOrder.add(order);
                    }
                    order.count += toTransfer;
                }

                remaining -= ingredient.count;
            }
        }

        updateCraftableAmounts();
    }

    private void updateCraftableAmounts() {
        InventorySummary usedItems = new InventorySummary();
        InventorySummary availableItems = new InventorySummary();
        for (BigItemStack ordered : itemsToOrder) {
            availableItems.add(ordered.stack, ordered.count);
        }

        boolean hasCustomEntries = false;
        for (CraftableBigItemStack craftableEntry : recipesToOrder) {
            if (craftableEntry instanceof IFluidCraftableBigItemStack fluidData
                    && fluidData.fluidlogistics$hasCustomRecipeData()) {
                hasCustomEntries = true;
                int outputCount = fluidData.fluidlogistics$getCustomOutputCount();
                if (outputCount <= 0) {
                    craftableEntry.count = 0;
                    continue;
                }

                int maxSets = getCustomCraftableSets(availableItems, usedItems,
                        fluidData.fluidlogistics$getCustomRequirements());
                craftableEntry.count = Math.min(craftableEntry.count, maxSets * outputCount);

                int committedSets = craftableEntry.count / outputCount;
                for (BigItemStack requirement : fluidData.fluidlogistics$getCustomRequirements()) {
                    usedItems.add(requirement.stack, requirement.count * committedSets);
                }
                continue;
            }

            Pair<Integer, List<List<BigItemStack>>> craftingResult = maxCraftable(craftableEntry, availableItems,
                    stack -> -usedItems.getCountOf(stack), -1);
            int maxCraftable = craftingResult.getFirst();
            int outputCount = craftableEntry.getOutputCount(menu.player.level());
            craftableEntry.count = Math.min(craftableEntry.count, maxCraftable);

            for (List<BigItemStack> ingredients : craftingResult.getSecond()) {
                int remaining = craftableEntry.count / outputCount;
                for (BigItemStack ingredient : ingredients) {
                    if (remaining <= 0) {
                        break;
                    }
                    usedItems.add(ingredient.stack, Math.min(remaining, ingredient.count));
                    remaining -= ingredient.count;
                }
            }
        }

        recipesToOrder.removeIf(entry -> entry.count <= 0);
        canRequestCraftingPackage = !hasCustomEntries;
        if (!canRequestCraftingPackage) {
            return;
        }

        for (BigItemStack ordered : itemsToOrder) {
            if (usedItems.getCountOf(ordered.stack) != ordered.count) {
                canRequestCraftingPackage = false;
                return;
            }
        }
    }

    private Pair<Integer, List<List<BigItemStack>>> maxCraftable(CraftableBigItemStack craftableEntry,
            InventorySummary summary, Function<ItemStack, Integer> countModifier, int newTypeLimit) {
        List<Ingredient> ingredients = craftableEntry.getIngredients();
        List<List<BigItemStack>> validEntriesByIngredient = new ArrayList<>();
        List<BigItemStack> alreadyCreated = new ArrayList<>();

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }

            List<BigItemStack> valid = new ArrayList<>();
            for (List<BigItemStack> list : summary.getItemMap().values()) {
                entries:
                for (BigItemStack entry : list) {
                    if (!ingredient.test(entry.stack)) {
                        continue;
                    }
                    for (BigItemStack visitedStack : alreadyCreated) {
                        if (!ItemStack.isSameItemSameComponents(visitedStack.stack, entry.stack)) {
                            continue;
                        }
                        valid.add(visitedStack);
                        continue entries;
                    }

                    BigItemStack available = new BigItemStack(entry.stack,
                            summary.getCountOf(entry.stack) + countModifier.apply(entry.stack));
                    if (available.count > 0) {
                        valid.add(available);
                        alreadyCreated.add(available);
                    }
                }
            }

            if (valid.isEmpty()) {
                return Pair.of(0, List.of());
            }

            Collections.sort(valid,
                    (first, second) -> -Integer.compare(summary.getCountOf(first.stack), summary.getCountOf(second.stack)));
            validEntriesByIngredient.add(valid);
        }

        if (newTypeLimit != -1) {
            int toRemove = (int) validEntriesByIngredient.stream()
                    .flatMap(List::stream)
                    .filter(entry -> getOrderForItem(entry.stack) == null)
                    .distinct()
                    .count() - newTypeLimit;
            for (int i = 0; i < toRemove; i++) {
                removeLeastEssentialItemStack(validEntriesByIngredient);
            }
        }

        validEntriesByIngredient = resolveIngredientAmounts(validEntriesByIngredient);

        int minCount = Integer.MAX_VALUE;
        for (List<BigItemStack> list : validEntriesByIngredient) {
            int sum = 0;
            for (BigItemStack entry : list) {
                sum += entry.count;
            }
            minCount = Math.min(sum, minCount);
        }

        if (minCount == 0) {
            return Pair.of(0, List.of());
        }

        return Pair.of(minCount * craftableEntry.getOutputCount(menu.player.level()), validEntriesByIngredient);
    }

    private void removeLeastEssentialItemStack(List<List<BigItemStack>> validIngredients) {
        List<BigItemStack> longest = null;
        int most = 0;
        for (List<BigItemStack> list : validIngredients) {
            int count = (int) list.stream().filter(entry -> getOrderForItem(entry.stack) == null).count();
            if (longest != null && count <= most) {
                continue;
            }
            longest = list;
            most = count;
        }

        if (longest == null || longest.isEmpty()) {
            return;
        }

        BigItemStack chosen = null;
        for (int i = 0; i < longest.size(); i++) {
            BigItemStack entry = longest.get(longest.size() - 1 - i);
            if (getOrderForItem(entry.stack) != null) {
                continue;
            }
            chosen = entry;
            break;
        }

        for (List<BigItemStack> list : validIngredients) {
            list.remove(chosen);
        }
    }

    private List<List<BigItemStack>> resolveIngredientAmounts(List<List<BigItemStack>> validIngredients) {
        List<List<BigItemStack>> resolvedIngredients = new ArrayList<>();
        for (int i = 0; i < validIngredients.size(); i++) {
            resolvedIngredients.add(new ArrayList<>());
        }

        boolean everythingTaken = false;
        while (!everythingTaken) {
            everythingTaken = true;
            ingredients:
            for (int i = 0; i < validIngredients.size(); i++) {
                List<BigItemStack> list = validIngredients.get(i);
                List<BigItemStack> resolvedList = resolvedIngredients.get(i);
                for (BigItemStack bigItemStack : list) {
                    if (bigItemStack.count == 0) {
                        continue;
                    }

                    bigItemStack.count -= 1;
                    everythingTaken = false;
                    for (BigItemStack resolved : resolvedList) {
                        if (resolved.stack == bigItemStack.stack) {
                            resolved.count++;
                            continue ingredients;
                        }
                    }

                    resolvedList.add(new BigItemStack(bigItemStack.stack, 1));
                    continue ingredients;
                }
            }
        }

        return resolvedIngredients;
    }

    private boolean hasCustomRecipeData(CraftableBigItemStack craftableEntry) {
        return craftableEntry instanceof IFluidCraftableBigItemStack fluidData
                && fluidData.fluidlogistics$hasCustomRecipeData();
    }

    private boolean isCustomFluidCraftable(CraftableBigItemStack craftableEntry) {
        return hasCustomRecipeData(craftableEntry)
                && craftableEntry.stack.getItem() instanceof CompressedTankItem
                && CompressedTankItem.isVirtual(craftableEntry.stack);
    }

    private int getRecipeStepAmount(IFluidCraftableBigItemStack fluidData) {
        int outputCount = Math.max(1, fluidData.fluidlogistics$getCustomOutputCount());
        if (hasShiftDown()) {
            return Math.max(outputCount, fluidData.fluidlogistics$getCustomTransferLimit());
        }
        if (hasControlDown()) {
            return outputCount * 10;
        }
        return outputCount;
    }

    private void handleCustomFluidCraftableRequest(CraftableBigItemStack craftableEntry, int requestedDifference) {
        IFluidCraftableBigItemStack fluidData = (IFluidCraftableBigItemStack) craftableEntry;
        int outputCount = fluidData.fluidlogistics$getCustomOutputCount();
        if (outputCount <= 0) {
            return;
        }

        boolean takeOrdersAway = requestedDifference < 0;
        if (takeOrdersAway) {
            requestedDifference = Math.max(-craftableEntry.count, requestedDifference);
        }
        if (requestedDifference == 0) {
            return;
        }

        int requestedSets = Mth.ceil(Math.abs(requestedDifference) / (float) outputCount);
        int applicableSets;
        if (takeOrdersAway) {
            applicableSets = Math.min(requestedSets, craftableEntry.count / outputCount);
        } else {
            if (!canFitCustomRecipe(itemsToOrder, fluidData.fluidlogistics$getCustomRequirements())) {
                return;
            }
            applicableSets = getCustomCraftableSets(stockSnapshot, itemsToOrder,
                    fluidData.fluidlogistics$getCustomRequirements());
            applicableSets = Math.min(requestedSets, applicableSets);
        }

        if (applicableSets <= 0) {
            return;
        }

        int amountDelta = applicableSets * outputCount;
        craftableEntry.count += takeOrdersAway ? -amountDelta : amountDelta;

        for (BigItemStack requirement : fluidData.fluidlogistics$getCustomRequirements()) {
            int delta = requirement.count * applicableSets;
            BigItemStack existingOrder = getOrderForItem(requirement.stack);
            if (takeOrdersAway) {
                if (existingOrder == null) {
                    continue;
                }
                existingOrder.count -= delta;
                if (existingOrder.count <= 0) {
                    itemsToOrder.remove(existingOrder);
                }
            } else {
                if (existingOrder == null) {
                    existingOrder = new BigItemStack(requirement.stack.copyWithCount(1), 0);
                    itemsToOrder.add(existingOrder);
                }
                existingOrder.count += delta;
            }
        }

        if (craftableEntry.count <= 0) {
            recipesToOrder.remove(craftableEntry);
        }

        updateCraftableAmounts();
    }

    private static int getCustomCraftableSets(InventorySummary availableItems, List<BigItemStack> existingOrders,
            List<BigItemStack> requirements) {
        int craftableSets = Integer.MAX_VALUE;
        for (BigItemStack requirement : requirements) {
            int orderedCount = getMatchingCount(existingOrders, requirement.stack);
            int available = availableItems.getCountOf(requirement.stack) - orderedCount;
            craftableSets = Math.min(craftableSets, available / requirement.count);
        }
        return craftableSets == Integer.MAX_VALUE ? 0 : Math.max(0, craftableSets);
    }

    private static int getCustomCraftableSets(InventorySummary availableItems, InventorySummary usedItems,
            List<BigItemStack> requirements) {
        int craftableSets = Integer.MAX_VALUE;
        for (BigItemStack requirement : requirements) {
            int available = availableItems.getCountOf(requirement.stack) - usedItems.getCountOf(requirement.stack);
            craftableSets = Math.min(craftableSets, available / requirement.count);
        }
        return craftableSets == Integer.MAX_VALUE ? 0 : Math.max(0, craftableSets);
    }

    private static boolean canFitCustomRecipe(List<BigItemStack> existingOrders, List<BigItemStack> requirements) {
        int totalTypes = existingOrders.size();
        List<ItemStack> newTypes = new ArrayList<>();

        for (BigItemStack requirement : requirements) {
            if (hasMatchingStack(existingOrders, requirement.stack) || hasMatchingStack(newTypes, requirement.stack)) {
                continue;
            }
            newTypes.add(requirement.stack);
            totalTypes++;
            if (totalTypes > 9) {
                return false;
            }
        }

        return true;
    }

    private static int getMatchingCount(List<BigItemStack> stacks, ItemStack target) {
        int total = 0;
        for (BigItemStack entry : stacks) {
            if (ItemStack.isSameItemSameComponents(entry.stack, target)) {
                total += entry.count;
            }
        }
        return total;
    }

    private static boolean hasMatchingStack(List<?> stacks, ItemStack target) {
        for (Object entry : stacks) {
            ItemStack stack = entry instanceof BigItemStack bigItemStack ? bigItemStack.stack : (ItemStack) entry;
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                return true;
            }
        }
        return false;
    }

    private int getMaxScroll() {
        int visibleHeight = windowHeight - 84;
        int totalRows = 2;
        for (int i = 0; i < displayedItems.size(); i++) {
            List<BigItemStack> list = displayedItems.get(i);
            if (list.isEmpty()) {
                continue;
            }
            totalRows++;
            if (categories.size() > i && categories.get(i).hidden()) {
                continue;
            }
            totalRows += Mth.ceil(list.size() / (float) cols);
        }
        return (int) Math.max(0, (totalRows * rowHeight - visibleHeight + 50) / rowHeight);
    }

    private void clampScrollBar() {
        float prevTarget = itemScroll.getChaseTarget();
        float newTarget = Mth.clamp(prevTarget, 0, getMaxScroll());
        if (prevTarget != newTarget) {
            itemScroll.startWithValue(newTarget);
        }
    }

    private int getTransferAmount(ItemStack stack, boolean shift, boolean control) {
        if (stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)) {
            if (shift) {
                return 50000;
            }
            if (control) {
                return 10000;
            }
            return 1000;
        }

        if (shift) {
            return stack.getMaxStackSize();
        }
        if (control) {
            return 10;
        }
        return 1;
    }

    private ItemStack getTickerDisplayStack() {
        ItemStack tickerStack = menu.getTickerStack();
        if (tickerStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack displayStack = tickerStack.copy();
        displayStack.remove(AllDataComponents.PORTABLE_STOCK_TICKER_FREQ);
        return displayStack;
    }

}
