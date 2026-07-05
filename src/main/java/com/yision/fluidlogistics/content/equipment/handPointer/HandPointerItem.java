package com.yision.fluidlogistics.content.equipment.handPointer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.config.Config;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class HandPointerItem extends Item {

    private static final String AUTHORIZED_NETWORKS_KEY = "AuthorizedNetworks";

    public HandPointerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasAuthorizedNetworks(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);

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
        if (!Config.isHandPointerEnabled()) {
            return InteractionResultHolder.pass(heldStack);
        }
        if (player.isShiftKeyDown() && hasAuthorizedNetworks(heldStack)) {
            if (level.isClientSide) {
                level.playSound(player, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.75f, 1.0f);
            } else {
                heldStack.removeTagKey(AUTHORIZED_NETWORKS_KEY);
                player.displayClientMessage(
                    CreateLang.translateDirect("fluidlogistics.hand_pointer.authorization_cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide);
        }
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
            ListTag list = new ListTag();
            for (UUID id : authorizedNetworks) {
                list.add(NbtUtils.createUUID(id));
            }
            stack.getOrCreateTag().put(AUTHORIZED_NETWORKS_KEY, list);
        }
        return added;
    }

    private static List<UUID> getStoredAuthorizedNetworks(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(AUTHORIZED_NETWORKS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag list = tag.getList(AUTHORIZED_NETWORKS_KEY, Tag.TAG_INT_ARRAY);
        List<UUID> result = new ArrayList<>(list.size());
        for (Tag element : list) {
            result.add(NbtUtils.loadUUID(element));
        }
        return result;
    }
}
