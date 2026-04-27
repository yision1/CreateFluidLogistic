package com.yision.fluidlogistics.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.compat.curios.CuriosCompat;
import com.yision.fluidlogistics.mixin.accessor.StockTickerBlockEntityAccessor;
import com.yision.fluidlogistics.portableticker.PortableStockTickerMenu;
import com.yision.fluidlogistics.registry.AllDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public class PortableStockTickerItem extends Item {

    private static final String FREQ_KEY = "Freq";

    public PortableStockTickerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack find(Inventory inventory) {
        ItemStack mainHand = inventory.player.getMainHandItem();
        if (mainHand.getItem() instanceof PortableStockTickerItem) {
            return mainHand;
        }

        ItemStack offHand = inventory.player.getOffhandItem();
        if (offHand.getItem() instanceof PortableStockTickerItem) {
            return offHand;
        }

        if (ModList.get().isLoaded("curios")) {
            ItemStack curiosStack = CuriosCompat.findPortableStockTicker(inventory.player);
            if (curiosStack.getItem() instanceof PortableStockTickerItem) {
                return curiosStack;
            }
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof PortableStockTickerItem) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return isTuned(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        if (!isTuned(stack)) {
            return;
        }

        CreateLang.translate("logistically_linked.tooltip")
                .style(ChatFormatting.GOLD)
                .addTo(tooltipComponents);

        CreateLang.translate("logistically_linked.tooltip_clear")
                .style(ChatFormatting.GRAY)
                .addTo(tooltipComponents);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (player == null) {
            return InteractionResult.FAIL;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        LogisticallyLinkedBehaviour link = BlockEntityBehaviour.get(level, pos, LogisticallyLinkedBehaviour.TYPE);
        if (link == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!link.mayInteractMessage(player)) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker) {
            saveCategoriesToStack(stack, ((StockTickerBlockEntityAccessor) ticker).fluidlogistics$getCategories());
        } else {
            saveCategoriesToStack(stack, List.of());
        }

        assignFrequency(stack, player, link.freqId);
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player,
            @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (!isTuned(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("item.fluidlogistics.portable_stock_ticker.not_linked"), true);
            }
            return InteractionResultHolder.success(stack);
        }

        if (!level.isClientSide) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new PortableStockTickerMenu(id, inv),
                    Component.translatable("item.fluidlogistics.portable_stock_ticker"));
            player.openMenu(provider);
        }
        return InteractionResultHolder.success(stack);
    }

    public boolean broadcastPackageRequest(ItemStack stack, LogisticallyLinkedBehaviour.RequestType type,
            PackageOrderWithCrafts order, String address, Player player) {
        UUID freq = networkFromStack(stack);
        if (freq == null) {
            return false;
        }
        boolean result = com.simibubi.create.content.logistics.packagerLink.LogisticsManager
                .broadcastPackageRequest(freq, type, order, null, address);
        if (result && player instanceof ServerPlayer) {
            saveAddressToStack(stack, address);
        }
        return result;
    }

    public static boolean isTuned(ItemStack stack) {
        return stack.has(AllDataComponents.PORTABLE_STOCK_TICKER_FREQ);
    }

    public static UUID networkFromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(AllDataComponents.PORTABLE_STOCK_TICKER_FREQ, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(FREQ_KEY)) {
            return null;
        }
        return tag.getUUID(FREQ_KEY);
    }

    public static void assignFrequency(ItemStack stack, Player player, UUID frequency) {
        CompoundTag tag = stack.getOrDefault(AllDataComponents.PORTABLE_STOCK_TICKER_FREQ, CustomData.EMPTY).copyTag();
        tag.putUUID(FREQ_KEY, frequency);
        stack.set(AllDataComponents.PORTABLE_STOCK_TICKER_FREQ, CustomData.of(tag));
        player.displayClientMessage(CreateLang.translateDirect("logistically_linked.tuned"), true);
    }

    public static void saveAddressToStack(ItemStack stack, String address) {
        if (address == null || address.isBlank()) {
            stack.remove(AllDataComponents.PORTABLE_STOCK_TICKER_ADDRESS);
            return;
        }
        stack.set(AllDataComponents.PORTABLE_STOCK_TICKER_ADDRESS, address);
    }

    public static String loadAddressFromStack(ItemStack stack) {
        return stack.getOrDefault(AllDataComponents.PORTABLE_STOCK_TICKER_ADDRESS, "");
    }

    public static void saveCategoriesToStack(ItemStack stack, List<ItemStack> categories) {
        stack.set(AllDataComponents.PORTABLE_STOCK_TICKER_CATEGORIES, List.copyOf(categories));
    }

    public static List<ItemStack> loadCategoriesFromStack(ItemStack stack) {
        List<ItemStack> categories = new ArrayList<>(stack.getOrDefault(AllDataComponents.PORTABLE_STOCK_TICKER_CATEGORIES, List.of()));
        categories.removeIf(itemStack -> !itemStack.isEmpty() && !(itemStack.getItem() instanceof FilterItem));
        return categories;
    }

    public static void saveHiddenCategoriesToStack(ItemStack stack, Map<UUID, List<Integer>> hiddenCategories) {
        stack.set(AllDataComponents.PORTABLE_STOCK_TICKER_HIDDEN_CATEGORIES, Map.copyOf(hiddenCategories));
    }

    public static Map<UUID, List<Integer>> loadHiddenCategoriesFromStack(ItemStack stack) {
        return new HashMap<>(stack.getOrDefault(AllDataComponents.PORTABLE_STOCK_TICKER_HIDDEN_CATEGORIES, Map.of()));
    }
}
