package com.yision.fluidlogistics.content.logistics.fluidPackage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.FluidPackageItemRenderer;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class FluidPackageItem extends PackageItem {

    public static final PackageStyle FLUID_STYLE =
        new PackageStyle("fluid", 12, 12, 23f, true);

    public static final PackageStyle FLUID_EXPOSED_STYLE =
        new PackageStyle("fluid_exposed", 12, 12, 23f, true);

    public static final PackageStyle FLUID_OXIDIZED_STYLE =
        new PackageStyle("fluid_oxidized", 12, 12, 23f, true);

    public static final PackageStyle FLUID_WEATHERED_STYLE =
        new PackageStyle("fluid_weathered", 12, 12, 23f, true);

    public FluidPackageItem(Properties properties) {
        this(properties, FLUID_STYLE);
    }

    public FluidPackageItem(Properties properties, PackageStyle style) {
        super(properties, style);
        PackageStyles.ALL_BOXES.remove(this);
        PackageStyles.RARE_BOXES.remove(this);
        PackageStyles.STANDARD_BOXES.remove(this);
    }

    public static boolean isFluidPackage(ItemStack stack) {
        return stack.getItem() instanceof FluidPackageItem;
    }

    @Override
    public InteractionResultHolder<ItemStack> open(Level level, Player player, InteractionHand hand) {
        ItemStack box = player.getItemInHand(hand);
        if (FluidPackageContentHelper.containsFluidStack(box)) {
            return InteractionResultHolder.pass(box);
        }
        return super.open(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext tooltipContext,
            List<Component> tooltip, TooltipFlag tooltipFlag) {
        if (stack.has(AllDataComponents.PACKAGE_ADDRESS)) {
            tooltip.add(Component.literal("→ " + stack.get(AllDataComponents.PACKAGE_ADDRESS))
                .withStyle(ChatFormatting.GOLD));
        }
        if (!stack.has(AllDataComponents.PACKAGE_CONTENTS)) {
            return;
        }

        ItemStackHandler contents = PackageItem.getContents(stack);
        List<Component> contentLines = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack content = contents.getStackInSlot(i);
            if (content.isEmpty() || content.getItem() instanceof SpawnEggItem) {
                continue;
            }
            Component line;
            if (CompressedTankItem.isFluidStack(content)) {
                FluidStack fluid = CompressedTankItem.getFluid(content);
                int amount = fluid.getAmount() * content.getCount();
                line = Component.literal("")
                    .append(fluid.getHoverName())
                    .append(" " + FluidAmountHelper.format(amount))
                    .withStyle(ChatFormatting.GRAY);
            } else {
                line = content.getHoverName().copy()
                    .append(" x")
                    .append(String.valueOf(content.getCount()))
                    .withStyle(ChatFormatting.GRAY);
            }
            if (contentLines.size() < 3) {
                contentLines.add(line);
            } else {
                skipped++;
            }
        }
        tooltip.addAll(contentLines);
        if (skipped > 0) {
            tooltip.add(Component.translatable("container.shulkerBox.more", skipped)
                .withStyle(ChatFormatting.ITALIC));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        FluidStack toPlace = FluidPackageContentHelper.peekDrainOneBucket(stack);
        if (toPlace.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!FluidPackagePlacementHelper.tryPlaceOneBucket(context, toPlace)) {
            return InteractionResult.PASS;
        }

        FluidPackageContentHelper.drainOneBucket(stack, false);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    @Override
    public String getDescriptionId() {
        return "item." + FluidLogistics.MODID + ".fluid_package";
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new FluidPackageItemRenderer()));
    }
}
