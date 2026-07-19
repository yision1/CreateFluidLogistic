package com.yision.fluidlogistics.mixin.logistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.yision.fluidlogistics.api.packager.ResourcePackagers;
import com.yision.fluidlogistics.content.logistics.packageResource.ResourcePackagerInventoryIdentifier;

@Mixin(LogisticsManager.class)
public abstract class LogisticsManagerMixin {
    @Inject(method = "getInventoryIdentifierFromLink", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fluidlogistics$identifyResourcePackagerInventory(
            LogisticallyLinkedBehaviour link,
            CallbackInfoReturnable<InventoryIdentifier> cir) {
        ResourcePackagers.fromLink(link)
                .map(packager -> packager.scan().storageIdentity())
                .map(ResourcePackagerInventoryIdentifier::new)
                .ifPresent(cir::setReturnValue);
    }
}
