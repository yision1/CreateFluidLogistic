package com.yision.fluidlogistics.content.logistics.fluidPackage;

import java.util.List;
import java.util.function.Consumer;

import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.datacomponent.FluidTankContent;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.CompressedTankItemRenderer;
import com.yision.fluidlogistics.registry.AllDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class CompressedTankItem extends Item {

    public CompressedTankItem(Properties properties) {
        super(properties);
    }

    public static FluidStack getFluid(ItemStack stack) {
        FluidTankContent content = stack.get(AllDataComponents.FLUID_TANK_CONTENT);
        return content != null ? content.fluid() : FluidStack.EMPTY;
    }

    public static void setFluid(ItemStack stack, FluidStack fluid) {
        if (!fluid.isEmpty() && fluid.getAmount() > getCapacity()) {
            throw new IllegalArgumentException("compressed tank capacity is " + getCapacity() + " mB");
        }
        stack.set(AllDataComponents.FLUID_TANK_CONTENT, new FluidTankContent(fluid.copy()));
    }

    public static boolean isFluidStack(ItemStack stack) {
        return stack.getItem() instanceof CompressedTankItem && !getFluid(stack).isEmpty();
    }

    public static boolean matchesFluid(ItemStack stack, FluidStack fluid) {
        return !fluid.isEmpty() && isFluidStack(stack)
                && FluidStack.isSameFluidSameComponents(getFluid(stack), fluid);
    }

    public static int getCapacity() {
        return Config.getFluidPerPackage();
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        if (!fluid.isEmpty()) {
            return fluid.getHoverName();
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        FluidStack fluid = getFluid(stack);
        if (fluid.isEmpty()) {
            return;
        }

        int capacity = getCapacity();
        tooltipComponents.add(Component.literal(fluid.getAmount() + " / " + capacity + " mB")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public String getCreatorModId(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        if (!fluid.isEmpty()) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            if (fluidId != null) {
                return fluidId.getNamespace();
            }
        }
        return FluidLogistics.MODID;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new CompressedTankItemRenderer()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack heldStack = player.getItemInHand(usedHand);

        FluidStack fluid = getFluid(heldStack);

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
