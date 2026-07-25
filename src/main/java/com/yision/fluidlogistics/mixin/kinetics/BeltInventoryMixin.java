package com.yision.fluidlogistics.mixin.kinetics;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.BeltInventory;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.fluids.faucet.SmartFaucetBlock;

import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BeltInventory.class, remap = false)
public class BeltInventoryMixin {

    @Shadow(remap = false)
    @Final
    private BeltBlockEntity belt;

    @ModifyReturnValue(method = "getBeltProcessingAtSegment", at = @At("RETURN"), remap = false)
    private BeltProcessingBehaviour fluidlogistics$findSmartFaucetOneBlockAbove(
            BeltProcessingBehaviour original, int segment) {
        BlockPos beltPos = BeltHelper.getPositionForOffset(belt, segment);
        BlockPos oneBlockAbove = beltPos.above();
        if (belt.getLevel().getBlockState(oneBlockAbove).getBlock() instanceof SmartFaucetBlock) {
            return BlockEntityBehaviour.get(belt.getLevel(), oneBlockAbove, BeltProcessingBehaviour.TYPE);
        }
        if (belt.getLevel().getBlockState(beltPos.above(2)).getBlock() instanceof SmartFaucetBlock) {
            return null;
        }
        if (original != null) {
            return original;
        }
        return MechanicalFluidGunBlockEntity.getBeltProcessingAt(belt.getLevel(), beltPos);
    }
}
