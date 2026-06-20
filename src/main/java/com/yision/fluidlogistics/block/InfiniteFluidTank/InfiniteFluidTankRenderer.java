package com.yision.fluidlogistics.block.InfiniteFluidTank;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraftforge.fluids.FluidStack;

public class InfiniteFluidTankRenderer extends SafeBlockEntityRenderer<InfiniteFluidTankBlockEntity> {

	public InfiniteFluidTankRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	protected void renderSafe(InfiniteFluidTankBlockEntity be, float partialTicks, PoseStack ms,
							  MultiBufferSource buffer, int light, int overlay) {
		LerpedFloat fluidLevel = be.getFluidLevel();
		if (fluidLevel == null)
			return;

		FluidStack fluidStack = be.getTankInventory().getFluid();
		if (fluidStack.isEmpty())
			return;

		float capHeight = 1 / 4f;
		float tankHullWidth = 1 / 16f + 1 / 128f;
		float minPuddleHeight = 1 / 16f;
		float totalHeight = 1 - 2 * capHeight - minPuddleHeight;

		float level = fluidLevel.getValue(partialTicks);
		if (level < 1 / (512f * totalHeight))
			return;

		float clampedLevel = Mth.clamp(level * totalHeight, 0, totalHeight);
		boolean top = fluidStack.getFluid().getFluidType().isLighterThanAir();

		float xMin = tankHullWidth;
		float xMax = 1 - tankHullWidth;
		float yMin = totalHeight + capHeight + minPuddleHeight - clampedLevel;
		float yMax = yMin + clampedLevel;

		if (top) {
			yMin += totalHeight - clampedLevel;
			yMax += totalHeight - clampedLevel;
		}

		float zMin = tankHullWidth;
		float zMax = 1 - tankHullWidth;

		ms.pushPose();
		ms.translate(0, clampedLevel - totalHeight, 0);
		ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluidStack, xMin, yMin, zMin, xMax, yMax, zMax,
			buffer, ms, light, false, true);
		ms.popPose();
	}
}
