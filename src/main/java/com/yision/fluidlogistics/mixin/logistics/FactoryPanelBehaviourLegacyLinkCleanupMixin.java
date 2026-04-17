package com.yision.fluidlogistics.mixin.logistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;

import net.minecraft.core.GlobalPos;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourLegacyLinkCleanupMixin {

    @Inject(method = "initialize", at = @At("TAIL"))
    private void fluidlogistics$cleanupLegacyFactoryPanelLinks(CallbackInfo ci) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        if (self.getWorld() == null) {
            return;
        }
        var level = self.getWorld();
        if (level == null || level.isClientSide()) {
            return;
        }

        GlobalPos globalPos = GlobalPos.of(level.dimension(), self.getPos());
        boolean changed = false;

        for (LogisticsNetwork network : Create.LOGISTICS.logisticsNetworks.values()) {
            if (network.totalLinks.remove(globalPos)) {
                changed = true;
            }
            network.loadedLinks.remove(globalPos);
        }

        if (changed) {
            Create.LOGISTICS.markDirty();
        }
    }
}
