package com.yision.fluidlogistics.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.registry.AllDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class HandPointerItem extends Item {

    public HandPointerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasAuthorizedNetworks(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        int authorizedCount = getAuthorizedNetworks(stack).size();
        if (authorizedCount <= 0) {
            return;
        }

        CreateLang.translate("item.fluidlogistics.hand_pointer.authorized_networks", authorizedCount)
            .style(ChatFormatting.GOLD)
            .addTo(tooltipComponents);
        CreateLang.translate("item.fluidlogistics.hand_pointer.authorized_networks_clear")
            .style(ChatFormatting.GRAY)
            .addTo(tooltipComponents);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack heldStack = player.getItemInHand(usedHand);
        return InteractionResultHolder.success(heldStack);
    }

    public static boolean hasAuthorizedNetworks(ItemStack stack) {
        return !getStoredAuthorizedNetworks(stack).isEmpty();
    }

    public static List<UUID> getAuthorizedNetworks(ItemStack stack) {
        return new ArrayList<>(getStoredAuthorizedNetworks(stack));
    }

    public static boolean isAuthorizedFor(ItemStack stack, UUID networkId) {
        return networkId != null
            && stack.getItem() instanceof HandPointerItem
            && getStoredAuthorizedNetworks(stack).contains(networkId);
    }

    public static boolean authorizeNetwork(ItemStack stack, UUID networkId) {
        if (!(stack.getItem() instanceof HandPointerItem) || networkId == null) {
            return false;
        }

        Set<UUID> authorizedNetworks = new LinkedHashSet<>(getAuthorizedNetworks(stack));
        boolean added = authorizedNetworks.add(networkId);
        if (added) {
            stack.set(AllDataComponents.HAND_POINTER_AUTHORIZED_NETWORKS, List.copyOf(authorizedNetworks));
        }
        return added;
    }

    private static List<UUID> getStoredAuthorizedNetworks(ItemStack stack) {
        return stack.getOrDefault(AllDataComponents.HAND_POINTER_AUTHORIZED_NETWORKS, List.of());
    }
}
