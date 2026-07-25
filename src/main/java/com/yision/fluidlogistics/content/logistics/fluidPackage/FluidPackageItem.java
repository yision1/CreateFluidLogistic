package com.yision.fluidlogistics.content.logistics.fluidPackage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.FluidPackageItemRenderer;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.ItemStackHandler;

public class FluidPackageItem extends PackageItem {

    public static final PackageStyle FLUID_STYLE = new PackageStyle("fluid", 12, 12, 23f, true);
    public static final PackageStyle FLUID_EXPOSED_STYLE = new PackageStyle("fluid_exposed", 12, 12, 23f, true);
    public static final PackageStyle FLUID_OXIDIZED_STYLE = new PackageStyle("fluid_oxidized", 12, 12, 23f, true);
    public static final PackageStyle FLUID_WEATHERED_STYLE = new PackageStyle("fluid_weathered", 12, 12, 23f, true);

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
        if (PackageResources.isBootstrapped() && PackageResources.blocksManualOpen(box)) {
            return InteractionResultHolder.pass(box);
        }
        return super.open(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag tooltipFlag) {
        String address = PackageItem.getAddress(stack);
        if (!address.isBlank()) {
            tooltip.add(Component.literal("→ " + address).withStyle(ChatFormatting.GOLD));
        }
        if (!stack.hasTag() || !stack.getTag().contains("Items")) {
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
                    .append(fluid.getDisplayName())
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
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ICapabilityProvider() {
            private final LazyOptional<IFluidHandlerItem> fluidHandler =
                LazyOptional.of(() -> new FluidPackageFluidHandler(stack));

            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                return cap == ForgeCapabilities.FLUID_HANDLER_ITEM ? fluidHandler.cast() : LazyOptional.empty();
            }
        };
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
