package com.yision.fluidlogistics.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;

@Mixin(LogisticallyLinkedBehaviour.class)
public interface LogisticallyLinkedBehaviourAccessor {

    @Accessor("global")
    boolean fluidlogistics$isGlobal();
}
