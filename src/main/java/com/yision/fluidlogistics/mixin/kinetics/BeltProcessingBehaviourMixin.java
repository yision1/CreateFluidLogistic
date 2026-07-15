package com.yision.fluidlogistics.mixin.kinetics;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.fluids.faucet.SmartFaucetBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BeltProcessingBehaviour.class)
public class BeltProcessingBehaviourMixin {

    @ModifyReturnValue(method = "isBlocked", at = @At("RETURN"))
    private static boolean fluidlogistics$allowSmartFaucet(boolean original, BlockGetter world,
            BlockPos processingSpace) {
        if (world.getBlockState(processingSpace.above()).getBlock() instanceof SmartFaucetBlock) {
            return false;
        }
        if (world.getBlockState(processingSpace.above()).getBlock() instanceof MechanicalFluidGunBlock
            && world.getBlockEntity(processingSpace.above()) instanceof MechanicalFluidGunBlockEntity gun
            && gun.targetsBeltPos(processingSpace)) {
            return false;
        }
        return original;
    }
}
