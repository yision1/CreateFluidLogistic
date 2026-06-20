package com.yision.fluidlogistics.mixin.accessor;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;

@Mixin(value = RedstoneRequesterScreen.class, remap = false)
public interface RedstoneRequesterScreenAccessor {

    @Accessor("amounts")
    List<Integer> getAmounts();
}
