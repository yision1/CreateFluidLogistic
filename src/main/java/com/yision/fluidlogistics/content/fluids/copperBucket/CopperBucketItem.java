package com.yision.fluidlogistics.content.fluids.copperBucket;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

public class CopperBucketItem extends Item {

    public static final int CAPACITY = 2 * FluidType.BUCKET_VOLUME;

    public CopperBucketItem(Properties properties) {
        super(properties);
    }

    public static FluidStack getFluid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(FluidHandlerItemStack.FLUID_NBT_KEY)) {
            return FluidStack.EMPTY;
        }
        return FluidStack.loadFluidStackFromNBT(tag.getCompound(FluidHandlerItemStack.FLUID_NBT_KEY));
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidHandlerItemStack(stack, CAPACITY);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        IFluidHandler blockHandler = FluidUtil.getFluidHandler(level, pos, context.getClickedFace())
                .orElse(null);
        IFluidHandlerItem itemHandler = context.getItemInHand()
                .getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                .orElse(null);
        if (blockHandler == null || itemHandler == null) {
            return InteractionResult.PASS;
        }

        boolean bucketIsEmpty = itemHandler.getFluidInTank(0).isEmpty();
        boolean execute = !level.isClientSide();
        FluidStack transferred = bucketIsEmpty
                ? FluidUtil.tryFluidTransfer(itemHandler, blockHandler, CAPACITY, execute)
                : FluidUtil.tryFluidTransfer(blockHandler, itemHandler, CAPACITY, execute);
        return transferred.isEmpty()
                ? InteractionResult.PASS
                : InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);

        FluidStack fluid = getFluid(stack);
        if (fluid.isEmpty()) {
            return;
        }

        tooltipComponents.add(fluid.getDisplayName().copy().withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal(fluid.getAmount() + " / " + CAPACITY + " mB")
                .withStyle(ChatFormatting.GRAY));
    }
}
