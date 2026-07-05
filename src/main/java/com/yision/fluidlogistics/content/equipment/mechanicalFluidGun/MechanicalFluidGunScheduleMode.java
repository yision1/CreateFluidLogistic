package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;

import net.createmod.catnip.lang.Lang;

public enum MechanicalFluidGunScheduleMode implements INamedIconOptions {
    ROUND_ROBIN(AllIcons.I_ARM_ROUND_ROBIN),
    FORCED_ROUND_ROBIN(AllIcons.I_ARM_FORCED_ROUND_ROBIN),
    PREFER_FIRST(AllIcons.I_ARM_PREFER_FIRST),

    ;

    private final String translationKey;
    private final AllIcons icon;

    MechanicalFluidGunScheduleMode(AllIcons icon) {
        this.icon = icon;
        this.translationKey = "fluidlogistics.mechanical_fluid_gun.schedule_mode." + Lang.asId(name());
    }

    @Override
    public AllIcons getIcon() {
        return icon;
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }
}
