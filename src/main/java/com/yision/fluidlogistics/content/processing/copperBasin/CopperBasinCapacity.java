package com.yision.fluidlogistics.content.processing.copperBasin;

import net.neoforged.neoforge.fluids.FluidType;

public final class CopperBasinCapacity {

	public static final int SLOT_CAPACITY = 10 * FluidType.BUCKET_VOLUME;
	public static final int TOTAL_CAPACITY = 4 * SLOT_CAPACITY;

	public static final int RENDER_FULL_CAPACITY = 2 * SLOT_CAPACITY;

	private CopperBasinCapacity() {
	}
}
