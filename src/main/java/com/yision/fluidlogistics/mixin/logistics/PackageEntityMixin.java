package com.yision.fluidlogistics.mixin.logistics;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.FluidFormatter;
import com.yision.fluidlogistics.compat.CompatMods;
import com.yision.fluidlogistics.compat.createenchantmentindustry.CreateEnchantmentIndustryCompat;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;

import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;

@Mixin(PackageEntity.class)
public abstract class PackageEntityMixin implements IHaveGoggleInformation {

    @Shadow
    public ItemStack box;

    @Unique
    private boolean fluidlogistics$selectedDroppedFluid;

    @Inject(
        method = "onInsideBlock",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/box/PackageEntity;destroy(Lnet/minecraft/world/damagesource/DamageSource;)V"
        ),
        cancellable = true
    )
    private void fluidlogistics$onWaterDestroy(BlockState state, CallbackInfo ci) {
        if (!box.isEmpty() && box.getItem() instanceof FluidPackageItem) {
            ci.cancel();
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void fluidlogistics$resetDroppedFluidSelection(ServerLevel level, DamageSource damageSource,
            CallbackInfo ci) {
        fluidlogistics$selectedDroppedFluid = false;
    }

    @WrapOperation(
        method = "dropAllDeathLoot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean fluidlogistics$dropFluidAwarePackageTank(ServerLevel level, Entity entity,
            Operation<Boolean> original) {
        if (!(entity instanceof ItemEntity itemEntity)) {
            return original.call(level, entity);
        }

        ItemStack itemStack = itemEntity.getItem();
        if (!CompressedTankItem.isFluidStack(itemStack)) {
            return original.call(level, entity);
        }

        PackageEntity packageEntity = (PackageEntity) (Object) this;
        if (CompatMods.createEnchantmentIndustryLoaded()
            && CreateEnchantmentIndustryCompat.tryDropExperienceFromTank(level, packageEntity.position(), itemStack)) {
            return true;
        }

        if (!fluidlogistics$selectedDroppedFluid) {
            fluidlogistics$selectedDroppedFluid = fluidlogistics$tryPlaceFluidFromTank(level, itemStack);
        }
        return true;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (box.isEmpty() || !(box.getItem() instanceof FluidPackageItem))
            return false;

        List<FluidStack> fluids = getContainedFluids(box);
        if (fluids.isEmpty())
            return false;

        CreateLang.translate("gui.goggles.fluid_container")
            .forGoggles(tooltip);

        int capacity = Config.getFluidPerPackage();

        for (FluidStack fluid : fluids) {
            CreateLang.fluidName(fluid)
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

            CreateLang.builder()
                .add(formatFluidAmount(fluid.getAmount()))
                .text(ChatFormatting.GRAY, " / ")
                .add(formatFluidAmount(capacity))
                .forGoggles(tooltip, 1);
        }

        return true;
    }

    private Component formatFluidAmount(int amountMb) {
        Couple<MutableComponent> components = FluidFormatter.asComponents(amountMb, true);
        return CreateLang.builder()
            .add(components.getFirst()
                .withStyle(ChatFormatting.GOLD))
            .text(" ")
            .add(components.getSecond()
                .withStyle(ChatFormatting.GOLD))
            .component();
    }

    private List<FluidStack> getContainedFluids(ItemStack box) {
        List<FluidStack> fluids = new ArrayList<>();

        if (!PackageItem.isPackage(box))
            return fluids;

        ItemStackHandler contents = PackageItem.getContents(box);

        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack slotStack = contents.getStackInSlot(i);
            if (CompressedTankItem.isFluidStack(slotStack)) {
                FluidStack fluid = CompressedTankItem.getFluid(slotStack);
                mergeFluid(fluids, fluid, slotStack.getCount());
            }
        }

        return fluids;
    }

    private void mergeFluid(List<FluidStack> fluids, FluidStack newFluid, int count) {
        int totalAmount = newFluid.getAmount() * count;
        for (FluidStack existing : fluids) {
            if (FluidStack.isSameFluidSameComponents(existing, newFluid)) {
                existing.grow(totalAmount);
                return;
            }
        }
        FluidStack copy = newFluid.copy();
        copy.setAmount(totalAmount);
        fluids.add(copy);
    }

    private boolean fluidlogistics$tryPlaceFluidFromTank(ServerLevel level, ItemStack tankStack) {
        FluidStack fluid = CompressedTankItem.getFluid(tankStack);
        if (fluid.getAmount() < 1000) {
            return false;
        }

        BlockPos pos = ((PackageEntity) (Object) this).blockPosition();
        if (level.dimensionType().ultraWarm() && fluid.getFluid().is(FluidTags.WATER)) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                    2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
            for (int i = 0; i < 8; i++) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + level.random.nextDouble(),
                        pos.getY() + level.random.nextDouble(), pos.getZ() + level.random.nextDouble(), 1, 0.0D, 0.0D,
                        0.0D, 0.0D);
            }
            return true;
        }

        BlockState fluidState = fluid.getFluid().defaultFluidState().createLegacyBlock();
        if (fluidState.isAir()) {
            return true;
        }

        BlockState existingState = level.getBlockState(pos);
        if (!existingState.canBeReplaced() || !fluidState.canSurvive(level, pos)) {
            return true;
        }

        level.setBlock(pos, fluidState, Block.UPDATE_ALL);
        return true;
    }
}
