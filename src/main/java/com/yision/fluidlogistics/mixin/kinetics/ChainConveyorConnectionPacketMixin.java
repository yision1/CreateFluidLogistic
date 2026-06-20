package com.yision.fluidlogistics.mixin.kinetics;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.PhantomChainConveyorAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Mixin(ChainConveyorConnectionPacket.class)
public class ChainConveyorConnectionPacketMixin {

	@Shadow
	@Final
	private BlockPos targetPos;

	@Shadow
	@Final
	private ItemStack chain;

	@ModifyExpressionValue(
		method = "applySettings",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/world/item/Items;CHAIN:Lnet/minecraft/world/item/Item;"
		)
	)
	private Item fluidlogistics$returnPhantomChainOnDisconnect(Item original, ServerPlayer player,
															   ChainConveyorBlockEntity be) {
		BlockPos connection = targetPos.subtract(be.getBlockPos());
		if (((PhantomChainConveyorAccess) be).fluidlogistics$isPhantomConnection(connection)) {
			return AllItems.PHANTOM_CHAIN.get();
		}
		return original;
	}

	@WrapOperation(
		method = "applySettings",
		at = @At(
			value = "INVOKE",
			target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;addConnectionTo(Lnet/minecraft/core/BlockPos;)Z",
			ordinal = 1
		)
	)
	private boolean fluidlogistics$markPhantomConnectionOnSuccessfulConnect(ChainConveyorBlockEntity instance,
																			BlockPos target,
																			Operation<Boolean> original,
																			ServerPlayer player,
																			ChainConveyorBlockEntity be) {
		boolean added = original.call(instance, target);
		if (!added || !chain.is(AllItems.PHANTOM_CHAIN.get())) {
			return added;
		}

		if (!(be.getLevel().getBlockEntity(targetPos) instanceof ChainConveyorBlockEntity clbe)) {
			return added;
		}

		BlockPos sourceOffset = targetPos.subtract(be.getBlockPos());
		BlockPos targetOffset = be.getBlockPos().subtract(targetPos);
		((PhantomChainConveyorAccess) be).fluidlogistics$setPhantomConnection(sourceOffset, true);
		((PhantomChainConveyorAccess) clbe).fluidlogistics$setPhantomConnection(targetOffset, true);
		be.notifyUpdate();
		clbe.notifyUpdate();
		return added;
	}
}
