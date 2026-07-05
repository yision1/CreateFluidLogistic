package com.yision.fluidlogistics.content.logistics.fluidPackage;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.FluidPackageItemRenderer;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

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
