package com.yision.fluidlogistics.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import com.yision.fluidlogistics.item.CompressedTankItem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
@Mixin(FactoryPanelConnectionHandler.class)
public class FactoryPanelConnectionHandlerMixin {

	@Redirect(
		method = "panelClicked",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;",
			remap = true,
			ordinal = 0
		),
		remap = false
	)
	private static Component fluidlogistics$displaySourceFluidName(ItemStack stack) {
		return fluidlogistics$getFluidGaugeDisplayName(stack);
	}

	@Redirect(
		method = "panelClicked",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;",
			remap = true,
			ordinal = 1
		),
		remap = false
	)
	private static Component fluidlogistics$displayTargetFluidName(ItemStack stack) {
		return fluidlogistics$getFluidGaugeDisplayName(stack);
	}

	private static Component fluidlogistics$getFluidGaugeDisplayName(ItemStack stack) {
		if (stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)) {
			FluidStack fluid = CompressedTankItem.getFluid(stack);
			if (!fluid.isEmpty()) {
				return fluid.getDisplayName();
			}
		}
		return stack.getHoverName();
	}
}
