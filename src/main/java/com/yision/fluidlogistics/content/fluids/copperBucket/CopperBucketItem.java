package com.yision.fluidlogistics.content.fluids.copperBucket;

import java.util.List;

import com.yision.fluidlogistics.registry.AllDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public class CopperBucketItem extends Item {

    public static final int CAPACITY = 2 * FluidType.BUCKET_VOLUME;

    public CopperBucketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        IFluidHandler blockHandler = level.getCapability(
                Capabilities.FluidHandler.BLOCK, pos, context.getClickedFace());
        IFluidHandlerItem itemHandler = context.getItemInHand()
                .getCapability(Capabilities.FluidHandler.ITEM);
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
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        FluidStack fluid = stack
                .getOrDefault(AllDataComponents.COPPER_BUCKET_CONTENT, SimpleFluidContent.EMPTY)
                .copy();
        if (fluid.isEmpty()) {
            return;
        }

        tooltipComponents.add(fluid.getHoverName().copy().withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.literal(fluid.getAmount() + " / " + CAPACITY + " mB")
                .withStyle(ChatFormatting.GRAY));
    }
}
