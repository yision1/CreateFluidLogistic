package com.yision.fluidlogistics.mixin.logistics;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.FluidFormatter;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

@Mixin(value = PackageEntity.class, remap = false)
public abstract class PackageEntityMixin implements IHaveGoggleInformation {

    @Shadow
    public ItemStack box;

    @Inject(
        method = "onInsideBlock",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/box/PackageEntity;destroy(Lnet/minecraft/world/damagesource/DamageSource;)V",
            remap = false
        ),
        cancellable = true,
        remap = true
    )
    private void fluidlogistics$onWaterDestroy(BlockState state, CallbackInfo ci) {
        if (!box.isEmpty() && box.getItem() instanceof FluidPackageItem) {
            ci.cancel();
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true, remap = true)
    private void fluidlogistics$dropFluidAwarePackageContents(DamageSource source, CallbackInfo ci) {
        ci.cancel();
        fluidlogistics$dropFluidAwarePackageContents((PackageEntity) (Object) this);
    }

    private void fluidlogistics$dropFluidAwarePackageContents(PackageEntity packageEntity) {
        ItemStackHandler contents = PackageItem.getContents(box);
        boolean selectedFluid = false;

        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack itemStack = contents.getStackInSlot(i);
            if (itemStack.isEmpty()) {
                continue;
            }

            if (itemStack.getItem() instanceof SpawnEggItem spawnEggItem
                && packageEntity.level() instanceof ServerLevel serverLevel) {
                EntityType<?> entityType = spawnEggItem.getType(itemStack.getTag());
                Entity entity = entityType.spawn(serverLevel, itemStack, null, packageEntity.blockPosition(),
                    MobSpawnType.SPAWN_EGG, false, false);
                if (entity != null) {
                    itemStack.shrink(1);
                }
            }

            if (itemStack.isEmpty()) {
                continue;
            }

            if (itemStack.getItem() instanceof CompressedTankItem) {
                if (!selectedFluid) {
                    selectedFluid = fluidlogistics$tryPlaceFluidFromTank(packageEntity, itemStack);
                }
                continue;
            }

            packageEntity.level().addFreshEntity(new ItemEntity(packageEntity.level(), packageEntity.getX(),
                packageEntity.getY(), packageEntity.getZ(), itemStack.copy()));
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (box.isEmpty() || !(box.getItem() instanceof FluidPackageItem)) {
            return false;
        }

        List<FluidStack> fluids = getContainedFluids(box);
        if (fluids.isEmpty()) {
            return false;
        }

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

        if (!PackageItem.isPackage(box)) {
            return fluids;
        }

        ItemStackHandler contents = PackageItem.getContents(box);

        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack slotStack = contents.getStackInSlot(i);
            if (!slotStack.isEmpty() && slotStack.getItem() instanceof CompressedTankItem) {
                FluidStack fluid = CompressedTankItem.getFluid(slotStack);
                mergeFluid(fluids, fluid, slotStack.getCount());
            }
        }

        return fluids;
    }

    private void mergeFluid(List<FluidStack> fluids, FluidStack newFluid, int count) {
        int totalAmount = newFluid.getAmount() * count;
        for (FluidStack existing : fluids) {
            if (existing.isFluidEqual(newFluid)) {
                existing.grow(totalAmount);
                return;
            }
        }
        FluidStack copy = newFluid.copy();
        copy.setAmount(totalAmount);
        fluids.add(copy);
    }

    private boolean fluidlogistics$tryPlaceFluidFromTank(PackageEntity packageEntity, ItemStack tankStack) {
        FluidStack fluid = CompressedTankItem.getFluid(tankStack);
        if (fluid.getAmount() < 1000) {
            return false;
        }

        BlockPos pos = packageEntity.blockPosition();
        if (packageEntity.level() instanceof ServerLevel serverLevel
            && serverLevel.dimensionType().ultraWarm()
            && fluid.getFluid().is(FluidTags.WATER)) {
            serverLevel.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                2.6F + (serverLevel.random.nextFloat() - serverLevel.random.nextFloat()) * 0.8F);
            for (int i = 0; i < 8; i++) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + serverLevel.random.nextDouble(),
                    pos.getY() + serverLevel.random.nextDouble(),
                    pos.getZ() + serverLevel.random.nextDouble(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            return true;
        }

        BlockState fluidState = fluid.getFluid().defaultFluidState().createLegacyBlock();
        if (fluidState.isAir()) {
            return true;
        }

        BlockState existingState = packageEntity.level().getBlockState(pos);
        if (!existingState.canBeReplaced() || !fluidState.canSurvive(packageEntity.level(), pos)) {
            return true;
        }

        packageEntity.level().setBlock(pos, fluidState, Block.UPDATE_ALL);
        return true;
    }
}
