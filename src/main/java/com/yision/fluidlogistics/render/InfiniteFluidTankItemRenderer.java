package com.yision.fluidlogistics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import com.yision.fluidlogistics.item.InfiniteFluidTankItem;
import com.yision.fluidlogistics.util.InfiniteFluidSupplyRules;

import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
public class InfiniteFluidTankItemRenderer extends CustomRenderedItemModelRenderer {

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
	                      ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffer, int light,
	                      int overlay) {
		renderer.render(model.getOriginalModel(), light);

		FluidStack fluid = InfiniteFluidTankItem.getContainedFluid(stack);
		if (fluid.isEmpty())
			return;

		renderFluidInTank(fluid, ms, buffer, light);
	}

	private static void renderFluidInTank(FluidStack fluid, PoseStack ms, MultiBufferSource buffer, int light) {
		float capHeight = 1 / 4f;
		float tankHullWidth = 1 / 16f + 1 / 128f;
		float minPuddleHeight = 1 / 16f;
		float totalHeight = 1 - 2 * capHeight - minPuddleHeight;

		float fillState = Mth.clamp((float) fluid.getAmount() / InfiniteFluidSupplyRules.getRequiredAmount(), 0, 1);
		float fluidHeight = Mth.clamp(fillState * totalHeight, 0, totalHeight);
		if (fluidHeight <= 0)
			return;

		float xMin = tankHullWidth;
		float xMax = 1 - tankHullWidth;
		float yMin = capHeight + minPuddleHeight;
		float yMax = yMin + fluidHeight;
		float zMin = tankHullWidth;
		float zMax = 1 - tankHullWidth;

		if (fluid.getFluid().getFluidType().isLighterThanAir()) {
			yMax = 1 - capHeight;
			yMin = yMax - fluidHeight;
		}

		ms.pushPose();
		ms.translate(-0.5f, -0.5f, -0.5f);
		ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, xMin, yMin, zMin, xMax, yMax, zMax,
			buffer, ms, light, true, false);
		ms.popPose();
	}
}
