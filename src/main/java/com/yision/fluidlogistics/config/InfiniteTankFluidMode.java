package com.yision.fluidlogistics.config;

import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

public enum InfiniteTankFluidMode {
    FOLLOW_CREATE(null),
    ALLOW_ALL(BottomlessFluidMode.ALLOW_ALL),
    DENY_ALL(BottomlessFluidMode.DENY_ALL),
    ALLOW_BY_TAG(BottomlessFluidMode.ALLOW_BY_TAG),
    DENY_BY_TAG(BottomlessFluidMode.DENY_BY_TAG);

    @Nullable private final BottomlessFluidMode createMode;

    InfiniteTankFluidMode(@Nullable BottomlessFluidMode createMode) {
        this.createMode = createMode;
    }

    public boolean test(Fluid fluid) {
        BottomlessFluidMode mode = createMode != null
            ? createMode
            : AllConfigs.server().fluids.bottomlessFluidMode.get();
        return mode.test(fluid);
    }
}
