package com.yision.fluidlogistics.mixin.logistics;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.yision.fluidlogistics.api.IFluidPackager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(PackagerLinkBlockEntity.class)
public abstract class PackagerLinkBlockEntityMixin {

    @Shadow(remap = false)
    public LogisticallyLinkedBehaviour behaviour;

    @Inject(
        method = "fetchSummaryFromPackager",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$fetchSummaryFromFluidPackager(@Nullable IdentifiedInventory ignoredHandler, 
            CallbackInfoReturnable<InventorySummary> cir) {
        PackagerLinkBlockEntity self = (PackagerLinkBlockEntity) (Object) this;
        
        if (behaviour.redstonePower == 15) {
            return;
        }
        
        BlockPos source = self.getBlockPos().relative(PackagerLinkBlock.getConnectedDirection(self.getBlockState()).getOpposite());
        BlockEntity blockEntity = self.getLevel().getBlockEntity(source);
        
        if (blockEntity instanceof IFluidPackager fluidPackager) {
            if (fluidPackager.isTargetingSameInventory(ignoredHandler)) {
                cir.setReturnValue(InventorySummary.EMPTY);
                return;
            }
            cir.setReturnValue(fluidPackager.getAvailableItems());
        }
    }
}
