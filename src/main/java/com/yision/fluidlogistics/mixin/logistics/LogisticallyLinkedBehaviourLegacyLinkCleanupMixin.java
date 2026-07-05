package com.yision.fluidlogistics.mixin.logistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;

import net.minecraft.core.GlobalPos;

@Mixin(value = LogisticallyLinkedBehaviour.class, remap = false)
public abstract class LogisticallyLinkedBehaviourLegacyLinkCleanupMixin {

    @Shadow(remap = false)
    private boolean global;

    @Inject(method = "initialize", at = @At("TAIL"))
    private void fluidlogistics$cleanupLegacyNonGlobalLinks(CallbackInfo ci) {
        if (global) {
            return;
        }

        LogisticallyLinkedBehaviour self = (LogisticallyLinkedBehaviour) (Object) this;
        if (self.getWorld() == null || self.getWorld().isClientSide()) {
            return;
        }

        GlobalPos globalPos = GlobalPos.of(self.getWorld().dimension(), self.getPos());
        boolean changed = false;

        var networkIterator = Create.LOGISTICS.logisticsNetworks.entrySet().iterator();
        while (networkIterator.hasNext()) {
            LogisticsNetwork network = networkIterator.next().getValue();
            if (network.totalLinks.remove(globalPos)) {
                changed = true;
            }
            network.loadedLinks.remove(globalPos);
            if (network.totalLinks.isEmpty()) {
                networkIterator.remove();
                changed = true;
            }
        }

        if (changed) {
            Create.LOGISTICS.markDirty();
        }
    }
}
