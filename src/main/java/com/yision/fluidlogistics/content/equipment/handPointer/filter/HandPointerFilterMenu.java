package com.yision.fluidlogistics.content.equipment.handPointer.filter;

import java.util.Optional;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.content.equipment.handPointer.filter.HandPointerFilterTargetResolver.ResolvedItemSlot;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class HandPointerFilterMenu extends GhostItemMenu<HandPointerFilterTarget> {

    public HandPointerFilterMenu(MenuType<?> type, int id, Inventory inv, HandPointerFilterTarget contentHolder) {
        super(type, id, inv, contentHolder);
    }

    public HandPointerFilterMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public static HandPointerFilterMenu create(int id, Inventory inv, HandPointerFilterTarget target) {
        return new HandPointerFilterMenu(com.yision.fluidlogistics.registry.AllMenuTypes.HAND_POINTER_FILTER.get(), id, inv, target);
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(1);
    }

    @Override
    protected void initAndReadInventory(HandPointerFilterTarget target) {
        super.initAndReadInventory(target);
        readCurrentFilter(target).ifPresent(stack -> ghostInventory.setStackInSlot(0, stack));
    }

    @Override
    protected boolean allowRepeats() {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected HandPointerFilterTarget createOnClient(RegistryFriendlyByteBuf extraData) {
        return HandPointerFilterTarget.decodeClient(extraData);
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(13, 112);
        addSlot(new SlotItemHandler(ghostInventory, 0, 74, 28));
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickTypeIn, Player player) {
        if (tryHandleConsumingFilterClick(slotId, clickTypeIn, player)) {
            return;
        }

        super.clicked(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (tryHandleConsumingFilterQuickMove(player, index)) {
            return ItemStack.EMPTY;
        }

        return super.quickMoveStack(player, index);
    }

    @Override
    protected void saveData(HandPointerFilterTarget target) {
        if (target == null) {
            return;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return;
        }

        applyFilter(level, player, target, ghostInventory.getStackInSlot(0));
    }

    private void applyFilter(Level level, Player player, HandPointerFilterTarget target, ItemStack toApply) {
        Direction side = target.side();
        BlockHitResult hitResult = new BlockHitResult(target.hitLocation(), side, target.pos(), false);
        Optional<ResolvedItemSlot> resolved = HandPointerFilterTargetResolver.resolveSlot(level, player, target.pos(), hitResult);
        if (resolved.isEmpty()) {
            deny(player, level);
            return;
        }

        ResolvedItemSlot slot = resolved.get();

        ItemStack oldFilter = slot.getStack(side);
        ItemStack newFilter = toApply.copy();
        if (!newFilter.isEmpty()) {
            newFilter.setCount(1);
        }

        if (slot.consumesFilterItems()
            && newFilter.getItem() instanceof FilterItem
            && !sameStack(oldFilter, newFilter)) {
            deny(player, level);
            return;
        }

        if (!slot.setStack(side, newFilter)) {
            deny(player, level);
            return;
        }

        if (slot.consumesFilterItems()
            && oldFilter.getItem() instanceof FilterItem
            && !sameStack(oldFilter, newFilter)
            && !player.isCreative()) {
            player.getInventory().placeItemBackInInventory(oldFilter);
        }

        accept(level, target);
    }

    private Optional<ItemStack> readCurrentFilter(HandPointerFilterTarget target) {
        if (target == null || player == null) {
            return Optional.empty();
        }

        Level level = player.level();
        BlockHitResult hitResult = new BlockHitResult(target.hitLocation(), target.side(), target.pos(), false);
        return HandPointerFilterTargetResolver.resolveSlot(level, player, target.pos(), hitResult)
            .map(slot -> {
                ItemStack currentFilter = slot.getStack(target.side());
                if (!currentFilter.isEmpty()) {
                    currentFilter.setCount(1);
                }
                return currentFilter;
            });
    }

    private Optional<ResolvedItemSlot> resolveCurrentSlot() {
        if (contentHolder == null || player == null) {
            return Optional.empty();
        }

        BlockHitResult hitResult =
            new BlockHitResult(contentHolder.hitLocation(), contentHolder.side(), contentHolder.pos(), false);
        return HandPointerFilterTargetResolver.resolveSlot(player.level(), player, contentHolder.pos(), hitResult);
    }

    private boolean tryHandleConsumingFilterClick(int slotId, ClickType clickType, Player player) {
        int slotIndex = slotId - 36;
        if (slotIndex != 0) {
            return false;
        }

        if (clickType == ClickType.THROW || clickType == ClickType.CLONE) {
            return false;
        }

        if (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE) {
            return true;
        }

        Optional<ResolvedItemSlot> resolved = resolveCurrentSlot();
        if (resolved.isEmpty() || !resolved.get().consumesFilterItems()) {
            return false;
        }

        ItemStack carried = getCarried();
        ItemStack current = ghostInventory.getStackInSlot(slotIndex);
        if (!(carried.getItem() instanceof FilterItem) && !(current.getItem() instanceof FilterItem)) {
            return false;
        }

        if (player.level().isClientSide) {
            return true;
        }

        ItemStack newStack = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
        if (newStack.getItem() instanceof FilterItem && sameStack(resolved.get().getStack(contentHolder.side()), newStack)) {
            return true;
        }

        if (!applyImmediateFilterSlotChange(player, resolved.get(), newStack)) {
            return true;
        }

        if (carried.getItem() instanceof FilterItem && !player.isCreative()) {
            carried.shrink(1);
            if (carried.isEmpty()) {
                setCarried(ItemStack.EMPTY);
            }
        }

        return true;
    }

    private boolean tryHandleConsumingFilterQuickMove(Player player, int index) {
        Optional<ResolvedItemSlot> resolved = resolveCurrentSlot();
        if (resolved.isEmpty() || !resolved.get().consumesFilterItems()) {
            return false;
        }

        if (index < 36) {
            Slot clickedSlot = getSlot(index);
            if (!clickedSlot.hasItem() || !(clickedSlot.getItem().getItem() instanceof FilterItem)) {
                return false;
            }

            if (player.level().isClientSide) {
                return true;
            }

            ItemStack stack = clickedSlot.getItem();
            if (sameStack(resolved.get().getStack(contentHolder.side()), stack)) {
                return true;
            }

            if (!applyImmediateFilterSlotChange(player, resolved.get(), stack.copyWithCount(1))) {
                return true;
            }

            if (!player.isCreative()) {
                stack.shrink(1);
                clickedSlot.setChanged();
            }

            return true;
        }

        if (index == 36 && ghostInventory.getStackInSlot(0).getItem() instanceof FilterItem) {
            if (!player.level().isClientSide) {
                applyImmediateFilterSlotChange(player, resolved.get(), ItemStack.EMPTY);
            }
            return true;
        }

        return false;
    }

    private boolean applyImmediateFilterSlotChange(Player player, ResolvedItemSlot slot, ItemStack newStack) {
        ItemStack normalized = newStack.copy();
        if (!normalized.isEmpty()) {
            normalized.setCount(1);
        }

        Direction side = contentHolder.side();
        ItemStack oldStack = slot.getStack(side);
        if (!slot.setStack(side, normalized)) {
            deny(player, player.level());
            return false;
        }

        if (oldStack.getItem() instanceof FilterItem
            && !sameStack(oldStack, normalized)
            && !player.isCreative()) {
            player.getInventory().placeItemBackInInventory(oldStack);
        }

        ghostInventory.setStackInSlot(0, normalized);
        getSlot(36).setChanged();
        broadcastChanges();
        accept(player.level(), contentHolder);
        return true;
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameComponents(first, second);
    }

    private static void deny(Player player, Level level) {
        player.displayClientMessage(CreateLang.translateDirect("logistics.filter.invalid_item"), true);
        AllSoundEvents.DENY.playOnServer(level, player.blockPosition(), 1, 1);
    }

    private static void accept(Level level, HandPointerFilterTarget target) {
        level.playSound(null, target.pos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25f, .1f);
    }
}
