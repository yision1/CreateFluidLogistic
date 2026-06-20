package com.yision.fluidlogistics.mixin.logistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.AllPackets;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageDestroyPacket;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.item.FluidPackageItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.material.Fluids;
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
import net.minecraftforge.network.PacketDistributor;

@Mixin(value = PackageEntity.class, remap = false)
public abstract class PackageEntityMixin {

    @Shadow
    public ItemStack box;

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$handleFluidPackageDestroy(DamageSource source, CallbackInfo ci) {
        if (box.isEmpty() || !(box.getItem() instanceof FluidPackageItem)) {
            return;
        }

        PackageEntity packageEntity = (PackageEntity) (Object) this;
        if (fluidlogistics$isWaterDestroy(packageEntity, source)) {
            ci.cancel();
            return;
        }

        AllPackets.getChannel()
            .send(PacketDistributor.TRACKING_ENTITY.with(() -> packageEntity),
                new PackageDestroyPacket(packageEntity.getBoundingBox().getCenter(), box));
        AllSoundEvents.PACKAGE_POP.playOnServer(packageEntity.level(), packageEntity.blockPosition());
        fluidlogistics$dropFluidAwarePackageContents(packageEntity);
        ci.cancel();
    }

    private boolean fluidlogistics$isWaterDestroy(PackageEntity packageEntity, DamageSource source) {
        return source == packageEntity.damageSources().drown() || "drown".equals(source.getMsgId());
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

    private boolean fluidlogistics$tryPlaceFluidFromTank(PackageEntity packageEntity, ItemStack tankStack) {
        FluidStack fluid = CompressedTankItem.getFluid(tankStack);
        if (fluid.getAmount() < 1000) {
            return false;
        }

        BlockPos pos = packageEntity.blockPosition();
        if (packageEntity.level() instanceof ServerLevel serverLevel
            && serverLevel.dimensionType().ultraWarm()
            && fluid.getFluid() == Fluids.WATER) {
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
