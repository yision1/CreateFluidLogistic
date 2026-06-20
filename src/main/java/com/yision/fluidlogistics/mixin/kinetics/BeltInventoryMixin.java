package com.yision.fluidlogistics.mixin.kinetics;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.block.SmartFaucet.SmartFaucetBlock;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BeltInventory.class, remap = false)
public class BeltInventoryMixin {

    @Shadow(remap = false)
    @Final
    private BeltBlockEntity belt;

    @Inject(method = "getBeltProcessingAtSegment", at = @At("RETURN"), cancellable = true, remap = false)
    private void fluidlogistics$findSmartFaucetOneBlockAbove(int segment,
            CallbackInfoReturnable<BeltProcessingBehaviour> cir) {
        BlockPos beltPos = BeltHelper.getPositionForOffset(belt, segment);
        BlockPos oneBlockAbove = beltPos.above();
        if (belt.getLevel().getBlockState(oneBlockAbove).getBlock() instanceof SmartFaucetBlock) {
            BeltProcessingBehaviour behaviour =
                    BlockEntityBehaviour.get(belt.getLevel(), oneBlockAbove, BeltProcessingBehaviour.TYPE);
            cir.setReturnValue(behaviour);
            return;
        }

        if (belt.getLevel().getBlockState(beltPos.above(2)).getBlock() instanceof SmartFaucetBlock) {
            cir.setReturnValue(null);
            return;
        }

        if (cir.getReturnValue() != null) {
            return;
        }

        BeltProcessingBehaviour gunBehaviour =
                MechanicalFluidGunBlockEntity.getBeltProcessingAt(belt.getLevel(), beltPos);
        if (gunBehaviour != null) {
            cir.setReturnValue(gunBehaviour);
        }
    }
}
