package com.yision.fluidlogistics.mixin.kinetics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionHandler;
import com.yision.fluidlogistics.registry.AllItems;

import net.minecraft.world.item.ItemStack;

@Mixin(ChainConveyorConnectionHandler.class)
public class ChainConveyorConnectionHandlerMixin {

	@Inject(method = "isChain", at = @At("RETURN"), cancellable = true)
	private static void fluidlogistics$isPhantomChain(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		if (AllItems.PHANTOM_CHAIN.isIn(itemStack)) {
			cir.setReturnValue(true);
		}
	}
}
