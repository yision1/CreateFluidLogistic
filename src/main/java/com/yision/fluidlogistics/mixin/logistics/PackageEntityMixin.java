package com.yision.fluidlogistics.mixin.logistics;

import java.util.List;
import java.util.Set;

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
import com.yision.fluidlogistics.api.packager.PackageDestroyContext;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.content.logistics.packageResource.PackageResourceGoggleDispatcher;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PackageEntity.class)
public abstract class PackageEntityMixin implements IHaveGoggleInformation {

    @Shadow
    public ItemStack box;

    @Unique
    private Set<ResourceLocation> fluidlogistics$consumedResourceTypes = Set.of();

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return PackageResourceGoggleDispatcher.append(box, tooltip, isPlayerSneaking);
    }

    @Inject(
        method = "onInsideBlock",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/box/PackageEntity;destroy(Lnet/minecraft/world/damagesource/DamageSource;)V"
        ),
        cancellable = true
    )
    private void fluidlogistics$onWaterDestroy(BlockState state, CallbackInfo ci) {
        if (PackageResources.isBootstrapped() && PackageResources.survivesWater(box)) {
            ci.cancel();
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void fluidlogistics$resetDroppedFluidSelection(ServerLevel level, DamageSource damageSource,
            CallbackInfo ci) {
        fluidlogistics$consumedResourceTypes = PackageResources.isBootstrapped()
                ? PackageResources.handleDestroyed(new PackageDestroyContext(
                        level, (PackageEntity) (Object) this, damageSource, box))
                : Set.of();
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
        if (!PackageResources.isBootstrapped()) {
            return original.call(level, entity);
        }

        return PackageResources.findType(itemEntity.getItem())
                .filter(type -> fluidlogistics$consumedResourceTypes.contains(type.id()))
                .isPresent()
                || original.call(level, entity);
    }
}
