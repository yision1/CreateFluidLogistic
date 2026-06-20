package com.yision.fluidlogistics.block.FluidPump;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;

import net.minecraft.core.Direction.AxisDirection;

public enum FluidTransferDirection implements INamedIconOptions {
	NEGATIVE(AxisDirection.NEGATIVE, AllIcons.I_MTD_LEFT, "fluidlogistics.fluid_pump.direction.forward"),
	POSITIVE(AxisDirection.POSITIVE, AllIcons.I_MTD_RIGHT, "fluidlogistics.fluid_pump.direction.reverse"),
	;

	private final String translationKey;
	private final AllIcons guiIcon;
	private final AxisDirection axisDirection;

	FluidTransferDirection(AxisDirection axisDirection, AllIcons guiIcon, String translationKey) {
		this.axisDirection = axisDirection;
		this.guiIcon = guiIcon;
		this.translationKey = translationKey;
	}

	@Override
	public AllIcons getIcon() {
		return AllIcons.I_MTD_RIGHT;
	}

	@Override
	public String getTranslationKey() {
		return translationKey;
	}

	public AxisDirection getAxisDirection() {
		return axisDirection;
	}

	public static INamedIconOptions[] guiOptions() {
		FluidTransferDirection[] values = values();
		INamedIconOptions[] options = new INamedIconOptions[values.length];
		for (int i = 0; i < values.length; i++) {
			FluidTransferDirection option = values[i];
			options[i] = new INamedIconOptions() {
				@Override
				public AllIcons getIcon() {
					return option.guiIcon;
				}

				@Override
				public String getTranslationKey() {
					return option.translationKey;
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
