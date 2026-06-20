package com.yision.fluidlogistics.client.phantomchain;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.PhantomChainConveyorAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class PhantomChainVisibility {

	private PhantomChainVisibility() {
	}

	public static boolean isRevealing() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		return player != null
			&& (AllItems.PHANTOM_CHAIN.isIn(player.getMainHandItem())
				|| AllItems.PHANTOM_CHAIN.isIn(player.getOffhandItem()));
	}

	public static boolean shouldRenderConnection(ChainConveyorBlockEntity be, BlockPos connection) {
		return isRevealing()
			|| !((PhantomChainConveyorAccess) be).fluidlogistics$isPhantomConnection(connection);
	}
}
