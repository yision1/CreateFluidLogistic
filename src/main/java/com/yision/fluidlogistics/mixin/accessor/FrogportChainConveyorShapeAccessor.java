package com.yision.fluidlogistics.mixin.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChainConveyorShape.class)
public interface FrogportChainConveyorShapeAccessor {

    @Invoker("drawOutline")
    void fluidlogistics$invokeDrawOutline(BlockPos current, PoseStack ms, VertexConsumer vb);
}
