package com.yision.fluidlogistics.mixin.kinetics;

import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.block.SmartFaucet.SmartFaucetBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BeltProcessingBehaviour.class, remap = false)
public class BeltProcessingBehaviourMixin {

    @Inject(method = "isBlocked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fluidlogistics$allowSmartFaucet(BlockGetter world, BlockPos processingSpace,
            CallbackInfoReturnable<Boolean> cir) {
        if (world.getBlockState(processingSpace.above()).getBlock() instanceof SmartFaucetBlock) {
            cir.setReturnValue(false);
        }
        if (world.getBlockState(processingSpace.above()).getBlock() instanceof MechanicalFluidGunBlock
                && world.getBlockEntity(processingSpace.above()) instanceof MechanicalFluidGunBlockEntity gun
                && gun.targetsBeltPos(processingSpace)) {
            cir.setReturnValue(false);
        }
    }
}
