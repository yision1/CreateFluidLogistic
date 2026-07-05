package com.yision.fluidlogistics.content.logistics.fluidPackage;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.CompressedTankItemRenderer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.common.util.LazyOptional;

public class CompressedTankItem extends Item {

    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_VIRTUAL = "Virtual";
    private static final String TAG_IDENTITY = "Identity";

    public CompressedTankItem(Properties properties) {
        super(properties);
    }

    public static FluidStack getFluid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_FLUID)) {
            return FluidStack.EMPTY;
        }
        return FluidStack.loadFluidStackFromNBT(tag.getCompound(TAG_FLUID));
    }

    public static void setFluid(ItemStack stack, FluidStack fluid) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_FLUID, fluid.writeToNBT(new CompoundTag()));
        tag.putBoolean(TAG_VIRTUAL, false);
        ensureIdentity(stack);
    }

    public static void setFluidVirtual(ItemStack stack, FluidStack fluid) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_FLUID, fluid.writeToNBT(new CompoundTag()));
        tag.putBoolean(TAG_VIRTUAL, true);
        tag.remove(TAG_IDENTITY);
    }

    public static boolean isVirtual(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_VIRTUAL);
    }

    public static void ensureIdentity(ItemStack stack) {
        if (!(stack.getItem() instanceof CompressedTankItem) || isVirtual(stack)) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID(TAG_IDENTITY)) {
            tag.putUUID(TAG_IDENTITY, UUID.randomUUID());
        }
    }

    public static int getCapacity() {
        return Config.getFluidPerPackage();
    }

    public static boolean isEmpty(ItemStack stack) {
        return getFluid(stack).isEmpty();
    }

    public static void clearFluid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_FLUID);
            tag.remove(TAG_VIRTUAL);
        }
        ensureIdentity(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId,
        boolean isSelected) {
        ensureIdentity(stack);
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        if (isVirtual(stack)) {
            return fluid.getDisplayName();
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
        FluidStack fluid = getFluid(stack);
        if (isVirtual(stack)) {
            return;
        }

        int capacity = getCapacity();
        if (!fluid.isEmpty()) {
            tooltipComponents.add(Component.literal(fluid.getAmount() + " / " + capacity + " mB")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltipComponents.add(Component.literal("0 / " + capacity + " mB")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public String getCreatorModId(ItemStack stack) {
        if (isVirtual(stack)) {
            FluidStack fluid = getFluid(stack);
            if (!fluid.isEmpty()) {
                ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
                if (fluidId != null) {
                    return fluidId.getNamespace();
                }
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
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ICapabilityProvider() {
            private final LazyOptional<IFluidHandlerItem> fluidHandler =
                LazyOptional.of(() -> new CompressedTankFluidHandler(stack));

            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                return cap == ForgeCapabilities.FLUID_HANDLER_ITEM ? fluidHandler.cast() : LazyOptional.empty();
            }
        };
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();
        Player player = context.getPlayer();
        ItemStack heldStack = context.getItemInHand();
        
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockState blockState = level.getBlockState(pos);
        if (!(blockState.getBlock() instanceof FluidTankBlock)) {
            return InteractionResult.PASS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        IFluidHandler fluidHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null);
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

        if (level.isClientSide) {
            return InteractionResultHolder.success(heldStack);
        }

        boolean transferred = false;

        for (InteractionHand hand : InteractionHand.values()) {
            if (hand == usedHand) continue;

            ItemStack otherStack = player.getItemInHand(hand);
            if (otherStack.isEmpty()) continue;

            IFluidHandler fluidCap = otherStack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
            if (fluidCap != null) {
                int filled = fluidCap.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0) {
                    fluid.shrink(filled);
                    transferred = true;

                    if (fluid.isEmpty()) {
                        clearFluid(heldStack);
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
