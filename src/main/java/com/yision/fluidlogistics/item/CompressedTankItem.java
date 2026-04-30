package com.yision.fluidlogistics.item;

import java.util.List;
import java.util.function.Consumer;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.datacomponent.FluidTankContent;
import com.yision.fluidlogistics.render.CompressedTankItemRenderer;
import com.yision.fluidlogistics.registry.AllDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class CompressedTankItem extends Item {

    public CompressedTankItem(Properties properties) {
        super(properties);
    }

    public static FluidStack getFluid(ItemStack stack) {
        FluidTankContent content = stack.get(AllDataComponents.FLUID_TANK_CONTENT);
        return content != null ? content.fluid() : FluidStack.EMPTY;
    }

    public static void setFluid(ItemStack stack, FluidStack fluid) {
        stack.set(AllDataComponents.FLUID_TANK_CONTENT, new FluidTankContent(fluid.copy(), false));
    }

    public static void setFluidVirtual(ItemStack stack, FluidStack fluid) {
        stack.set(AllDataComponents.FLUID_TANK_CONTENT, new FluidTankContent(fluid.copy(), true));
    }

    public static boolean isVirtual(ItemStack stack) {
        FluidTankContent content = stack.get(AllDataComponents.FLUID_TANK_CONTENT);
        return content != null && content.virtual();
    }

    public static int getCapacity() {
        return Config.getCompressedTankCapacity();
    }

    public static boolean isEmpty(ItemStack stack) {
        return getFluid(stack).isEmpty();
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        if (isVirtual(stack) && !fluid.isEmpty()) {
            return fluid.getHoverName();
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        FluidStack fluid = getFluid(stack);
        if (isVirtual(stack)) {
            return;
        }

        int capacity = getCapacity();
        if (!fluid.isEmpty()) {
            tooltipComponents.add(Component.literal(fluid.getAmount() + " / " + capacity + " mB")
                    .withStyle(ChatFormatting.GRAY));
            return;
        } else {
            tooltipComponents.add(Component.literal("0 / " + capacity + " mB")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new CompressedTankItemRenderer()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();
        Player player = context.getPlayer();
        ItemStack heldStack = context.getItemInHand();
        
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockState blockState = level.getBlockState(pos);
        if (!(blockState.getBlock() instanceof FluidTankBlock)) {
            return InteractionResult.PASS;
        }

        IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (fluidHandler == null) {
            return InteractionResult.PASS;
        }

        FluidStack heldFluid = getFluid(heldStack);

        if (!heldFluid.isEmpty()) {
            int filled = fluidHandler.fill(heldFluid, FluidAction.EXECUTE);
            if (filled > 0) {
                heldStack.shrink(1);
                if (player != null && heldStack.isEmpty()) {
                    player.setItemInHand(context.getHand(), ItemStack.EMPTY);
                }
                
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
                return InteractionResult.SUCCESS;
            }
        } else {
            heldStack.shrink(1);
            if (player != null && heldStack.isEmpty()) {
                player.setItemInHand(context.getHand(), ItemStack.EMPTY);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack heldStack = player.getItemInHand(usedHand);
        FluidStack fluid = getFluid(heldStack);

        if (fluid.isEmpty()) {
            return InteractionResultHolder.pass(heldStack);
        }

        if (level.isClientSide()) {
            return InteractionResultHolder.success(heldStack);
        }

        boolean transferred = false;

        for (InteractionHand hand : InteractionHand.values()) {
            if (hand == usedHand) continue;

            ItemStack otherStack = player.getItemInHand(hand);
            if (otherStack.isEmpty()) continue;

            var fluidCap = otherStack.getCapability(Capabilities.FluidHandler.ITEM);
            if (fluidCap != null) {
                int filled = fluidCap.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0) {
                    fluid.shrink(filled);
                    transferred = true;

                    if (fluid.isEmpty()) {
                        heldStack.remove(AllDataComponents.FLUID_TANK_CONTENT);
                    } else {
                        setFluid(heldStack, fluid);
                    }
                    break;
                }
            }
        }

        if (transferred) {
            return InteractionResultHolder.success(heldStack);
        }

        return InteractionResultHolder.pass(heldStack);
    }
}
