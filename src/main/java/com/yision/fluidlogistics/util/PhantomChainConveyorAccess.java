package com.yision.fluidlogistics.util;

import net.minecraft.core.BlockPos;

public interface PhantomChainConveyorAccess {
	boolean fluidlogistics$isPhantomConnection(BlockPos connection);

	void fluidlogistics$setPhantomConnection(BlockPos connection, boolean phantom);
}
