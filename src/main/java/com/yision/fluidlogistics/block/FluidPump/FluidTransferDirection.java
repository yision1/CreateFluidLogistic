package com.yision.fluidlogistics.block.FluidPump;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;

import net.createmod.catnip.lang.Lang;

import net.minecraft.core.Direction.AxisDirection;

public enum FluidTransferDirection implements INamedIconOptions {
	NEGATIVE(AxisDirection.NEGATIVE, AllIcons.I_MTD_LEFT),
	POSITIVE(AxisDirection.POSITIVE, AllIcons.I_MTD_RIGHT),

	;

	private static final String PUMP_OUT_TRANSLATION_KEY = "fluidlogistics.fluid_pump.direction.positive";
	private static final String DRAW_IN_TRANSLATION_KEY = "fluidlogistics.fluid_pump.direction.negative";

	private final String translationKey;
	private final AllIcons icon;
	private final AxisDirection axisDirection;

	FluidTransferDirection(AxisDirection axisDirection, AllIcons icon) {
		this.axisDirection = axisDirection;
		this.icon = icon;
		this.translationKey = "fluidlogistics.fluid_pump.direction." + Lang.asId(name());
	}

	@Override
	public AllIcons getIcon() {
		return icon;
	}

	@Override
	public String getTranslationKey() {
		return translationKey;
	}

	public AxisDirection getAxisDirection() {
		return axisDirection;
	}

	public String getDisplayTranslationKey(AxisDirection pumpOutDirection) {
		return axisDirection == pumpOutDirection ? PUMP_OUT_TRANSLATION_KEY : DRAW_IN_TRANSLATION_KEY;
	}

	public static INamedIconOptions[] displayOptionsFor(AxisDirection pumpOutDirection) {
		FluidTransferDirection[] values = values();
		INamedIconOptions[] options = new INamedIconOptions[values.length];
		for (int i = 0; i < values.length; i++) {
			FluidTransferDirection option = values[i];
			options[i] = new INamedIconOptions() {
				@Override
				public AllIcons getIcon() {
					return option.getIcon();
				}

				@Override
				public String getTranslationKey() {
					return option.getDisplayTranslationKey(pumpOutDirection);
				}
			};
		}
		return options;
	}

	public static FluidTransferDirection fromAxisDirection(AxisDirection axisDirection) {
		for (FluidTransferDirection value : values()) {
			if (value.axisDirection == axisDirection)
				return value;
		}
		return NEGATIVE;
	}
}
