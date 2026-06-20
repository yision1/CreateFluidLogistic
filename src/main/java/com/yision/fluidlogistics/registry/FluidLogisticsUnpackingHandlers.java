package com.yision.fluidlogistics.registry;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.impl.unpacking.BasinUnpackingHandler;

public class FluidLogisticsUnpackingHandlers {

	public static void registerDefaults() {
		UnpackingHandler.REGISTRY.register(AllBlocks.COPPER_BASIN.get(), BasinUnpackingHandler.INSTANCE);
	}
}
