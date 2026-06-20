package com.yision.fluidlogistics.mixin.basin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.yision.fluidlogistics.util.CreateBasinCapacity;

@Mixin(value = BasinBlockEntity.class, remap = false)
public class BasinBlockEntityMixin {

    @ModifyArg(
        method = "addBehaviours",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/fluid/SmartFluidTankBehaviour;<init>(Lcom/simibubi/create/foundation/blockEntity/behaviour/BehaviourType;Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;IIZ)V",
            ordinal = 0
        ),
        index = 3
    )
    private int fluidlogistics$expandInputTankCapacity(int original) {
        return CreateBasinCapacity.SLOT_CAPACITY;
    }

    @ModifyArg(
        method = "addBehaviours",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/fluid/SmartFluidTankBehaviour;<init>(Lcom/simibubi/create/foundation/blockEntity/behaviour/BehaviourType;Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;IIZ)V",
            ordinal = 1
        ),
        index = 3
    )
    private int fluidlogistics$expandOutputTankCapacity(int original) {
        return CreateBasinCapacity.SLOT_CAPACITY;
    }

    @ModifyConstant(method = "createFluidParticles", constant = @Constant(floatValue = 2000.0F))
    private float fluidlogistics$scaleFluidParticles(float original) {
        return CreateBasinCapacity.TOTAL_CAPACITY;
    }
}
