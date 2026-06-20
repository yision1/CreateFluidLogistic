package com.yision.fluidlogistics.mixin.kinetics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.PhantomChainConveyorAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Mixin(ChainConveyorBlock.class)
public class ChainConveyorBlockMixin {

	@ModifyExpressionValue(
		method = "useItemOn",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"
		)
	)
	private boolean fluidlogistics$useOnPhantomChain(boolean original, ItemStack stack) {
		return original || AllItems.PHANTOM_CHAIN.isIn(stack);
	}

	@ModifyExpressionValue(
		method = "lambda$onSneakWrenched$1",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/item/Items;CHAIN:Lnet/minecraft/world/item/Item;"
		)
	)
	private static Item fluidlogistics$returnPhantomChainOnWrench(Item original,
																  @Local(argsOnly = true) ChainConveyorBlockEntity be,
																  @Local(ordinal = 0) BlockPos targetPos) {
		if (((PhantomChainConveyorAccess) be).fluidlogistics$isPhantomConnection(targetPos)) {
			return AllItems.PHANTOM_CHAIN.get();
		}
		return original;
	}
}
