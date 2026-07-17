package com.yision.fluidlogistics.handler;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;

@EventBusSubscriber(modid = FluidLogistics.MODID)
public final class CompressedTankPackageHandler {

    private CompressedTankPackageHandler() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().isShiftKeyDown() || !shouldBlockOpen(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.PASS);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown() || !shouldBlockOpen(event.getItemStack())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.PASS);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack box = event.getItemStack();
        if (!(box.getItem() instanceof PackageItem) || box.getItem() instanceof FluidPackageItem
                || !box.has(AllDataComponents.PACKAGE_CONTENTS)) {
            return;
        }

        ItemStackHandler contents = PackageItem.getContents(box);
        List<Component> fluidLines = new ArrayList<>();
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack tank = contents.getStackInSlot(i);
            if (!CompressedTankItem.isFluidStack(tank)) {
                continue;
            }
            String originalLine = tank.getHoverName().getString() + " x" + tank.getCount();
            event.getToolTip().removeIf(line -> line.getString().equals(originalLine));
            FluidStack fluid = CompressedTankItem.getFluid(tank);
            fluidLines.add(Component.literal("")
                .append(fluid.getHoverName())
                .append(" " + FluidAmountHelper.format(fluid.getAmount() * tank.getCount()))
                .withStyle(ChatFormatting.GRAY));
        }
        Component itemIdLine = Component.literal(BuiltInRegistries.ITEM.getKey(box.getItem()).toString())
            .withStyle(ChatFormatting.DARK_GRAY);
        int itemIdIndex = event.getToolTip().indexOf(itemIdLine);
        int insertionIndex = event.getFlags().isAdvanced() && itemIdIndex >= 0
            ? itemIdIndex
            : event.getToolTip().size();
        event.getToolTip().addAll(insertionIndex, fluidLines);
    }

    private static boolean shouldBlockOpen(ItemStack box) {
        return !(box.getItem() instanceof FluidPackageItem)
                && PackageResources.isBootstrapped()
                && PackageResources.blocksManualOpen(box);
    }
}
