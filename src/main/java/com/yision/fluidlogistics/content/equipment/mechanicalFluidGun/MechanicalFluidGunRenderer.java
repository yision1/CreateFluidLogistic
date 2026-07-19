package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.yision.fluidlogistics.registry.AllPartialModels;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalFluidGunRenderer extends KineticBlockEntityRenderer<MechanicalFluidGunBlockEntity> {

	public MechanicalFluidGunRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(MechanicalFluidGunBlockEntity be, float partialTicks, PoseStack ms,
							  MultiBufferSource buffer, int light, int overlay) {
		if (VisualizationManager.supportsVisualization(be.getLevel())) return;
		ms.pushPose();
		MechanicalFluidGunMount.rotateModel(ms, be.getBlockState());

		renderMountedCog(be, ms, buffer, light);

		float yaw = be.yaw.getValue(partialTicks);
		float pitch = be.pitch.getValue(partialTicks);

		renderYawingPartial(be, AllPartialModels.MECHANICAL_FLUID_GUN_BASE, yaw, ms, buffer, light);

		renderSpoutPart(be, AllPartialModels.MECHANICAL_FLUID_GUN_GUN_BODY, yaw, pitch, ms, buffer, light);
		renderSpoutPart(be, AllPartialModels.MECHANICAL_FLUID_GUN_GUNPOINT_TOP, yaw, pitch, ms, buffer, light);
		renderSpoutPart(be, AllPartialModels.MECHANICAL_FLUID_GUN_GUNPOINT_MIDDLE, yaw, pitch, ms, buffer, light);
		renderSpoutPart(be, AllPartialModels.MECHANICAL_FLUID_GUN_GUNPOINT_BOTTOM, yaw, pitch, ms, buffer, light);

		ms.popPose();

		if (be.shouldRenderSourceInterface()) {
			renderSourceInterface(be, ms, buffer, light);
		}


	}

	private void renderYawingPartial(MechanicalFluidGunBlockEntity be, PartialModel model, float yaw,
									 PoseStack ms, MultiBufferSource buffer, int light) {
		ms.pushPose();
		transformYaw(ms, yaw);
		renderPartial(be, model, ms, buffer, light);
		ms.popPose();
	}

	private void renderSpoutPart(MechanicalFluidGunBlockEntity be, PartialModel model, float yaw, float pitch,
								 PoseStack ms, MultiBufferSource buffer, int light) {
		ms.pushPose();
		transformYaw(ms, yaw);
		transformPitch(ms, pitch);
		renderPartial(be, model, ms, buffer, light);
		ms.popPose();
	}

	private void renderPartial(MechanicalFluidGunBlockEntity be, PartialModel model,
							   PoseStack ms, MultiBufferSource buffer, int light) {
		SuperByteBuffer buf = CachedBuffers.partial(model, be.getBlockState());
		buf.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	private void renderSourceInterface(MechanicalFluidGunBlockEntity be, PoseStack ms,
									   MultiBufferSource buffer, int light) {
		Direction sourceDirection = MechanicalFluidGunMount.getMountFace(be.getBlockState()).getOpposite();
		PartialModel partial = com.simibubi.create.AllPartialModels.PIPE_ATTACHMENTS
			.get(ComponentPartials.DRAIN)
			.get(sourceDirection);
		SuperByteBuffer attachment = CachedBuffers.partial(partial, be.getBlockState());
		attachment.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
	}

	private void renderMountedCog(MechanicalFluidGunBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
		BlockState state = be.getBlockState();
		SuperByteBuffer cog = CachedBuffers.partial(AllPartialModels.MECHANICAL_FLUID_GUN_COG, state);
		float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(),
			MechanicalFluidGunMount.getMountFace(state).getAxis());
		KineticBlockEntityRenderer.kineticRotationTransform(cog, be, net.minecraft.core.Direction.Axis.Y, angle, light)
			.renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
	}

	static void transformYaw(PoseStack ms, float yaw) {
		ms.translate(0.5, 14.0 / 16.0, 0.5);
		ms.mulPose(Axis.YP.rotationDegrees(yaw));
		ms.translate(-0.5, -14.0 / 16.0, -0.5);
	}

	static void transformPitch(PoseStack ms, float pitch) {
		ms.translate(MechanicalFluidGunTarget.PITCH_PIVOT_X, MechanicalFluidGunTarget.PITCH_PIVOT_Y,
			MechanicalFluidGunTarget.PITCH_PIVOT_Z);
		ms.mulPose(Axis.XP.rotationDegrees(pitch));
		ms.translate(-MechanicalFluidGunTarget.PITCH_PIVOT_X, -MechanicalFluidGunTarget.PITCH_PIVOT_Y,
			-MechanicalFluidGunTarget.PITCH_PIVOT_Z);
	}

	@Override
	protected SuperByteBuffer getRotatedModel(MechanicalFluidGunBlockEntity be, BlockState state) {
		return CachedBuffers.partial(AllPartialModels.MECHANICAL_FLUID_GUN_COG, state);
	}

	@Override
	public boolean shouldRenderOffScreen(MechanicalFluidGunBlockEntity be) {
		return true;
	}
}
