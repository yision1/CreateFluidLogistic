package com.yision.fluidlogistics.mixin.logistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerItemHandler;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

@Mixin(FrogportBlockEntity.class)
public class FrogportBlockEntityMixin {

    @Inject(
        method = "tryPullingFrom",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$tryPullingFromFluidPackager(IItemHandler handler, CallbackInfoReturnable<Boolean> cir) {
        if (handler instanceof FluidPackagerItemHandler) {
            FrogportBlockEntity self = (FrogportBlockEntity) (Object) this;
            
            if (self.isAnimationInProgress()) {
                cir.setReturnValue(false);
                return;
            }
            
            ItemStack extract = ItemHelper.extract(handler, stack -> {
                if (!PackageItem.isPackage(stack))
                    return false;
                return true;
            }, false);
            
            if (extract.isEmpty()) {
                cir.setReturnValue(false);
                return;
            }
            
            self.startAnimation(extract, true);
            cir.setReturnValue(true);
        }
    }
}
