package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.yision.fluidlogistics.registry.AllPartialModels;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public class MechanicalFluidGunVisual extends AbstractBlockEntityVisual<MechanicalFluidGunBlockEntity>
	implements SimpleDynamicVisual {

	private final TransformedInstance base;
	private final TransformedInstance cog;
	private final TransformedInstance gunBody;
	private final TransformedInstance top;
	private final TransformedInstance middle;
	private final TransformedInstance bottom;
	private final TransformedInstance sourceInterface;
	private final TransformedInstance[] instances;
	private final PoseStack poseStack = new PoseStack();
	private Direction sourceDirection;
	private boolean sourceVisible = true;
	private float lastYaw = Float.NaN;
	private float lastPitch = Float.NaN;

	public MechanicalFluidGunVisual(VisualizationContext context, MechanicalFluidGunBlockEntity blockEntity,
									float partialTick) {
		super(context, blockEntity, partialTick);
		base = create(AllPartialModels.MECHANICAL_FLUID_GUN_BASE);
		cog = create(AllPartialModels.MECHANICAL_FLUID_GUN_COG);
		gunBody = create(AllPartialModels.MECHANICAL_FLUID_GUN_GUN_BODY);
		top = create(AllPartialModels.MECHANICAL_FLUID_GUN_GUNPOINT_TOP);
		middle = create(AllPartialModels.MECHANICAL_FLUID_GUN_GUNPOINT_MIDDLE);
		bottom = create(AllPartialModels.MECHANICAL_FLUID_GUN_GUNPOINT_BOTTOM);
		sourceDirection = MechanicalFluidGunMount.getMountFace(blockState).getOpposite();
		sourceInterface = create(getSourcePartial(sourceDirection));
		instances = new TransformedInstance[]{base, cog, gunBody, top, middle, bottom, sourceInterface};
		sourceInterface.setIdentityTransform().translate(getVisualPosition()).setChanged();

		TransformStack.of(poseStack).translate(getVisualPosition());
		MechanicalFluidGunMount.rotateModel(poseStack, blockState);
		animate(partialTick);
	}

	private TransformedInstance create(PartialModel model) {
		return instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model)).createInstance();
	}

	private static PartialModel getSourcePartial(Direction direction) {
		return com.simibubi.create.AllPartialModels.PIPE_ATTACHMENTS
			.get(ComponentPartials.DRAIN)
			.get(direction);
	}

	@Override
	public void beginFrame(Context context) {
		animate(context.partialTick());
	}

	private void animate(float partialTick) {
		float yaw = blockEntity.yaw.getValue(partialTick);
		float pitch = blockEntity.pitch.getValue(partialTick);
		if (!Mth.equal(lastYaw, yaw) || !Mth.equal(lastPitch, pitch)) {
			setYawTransform(base, yaw);
			setSpoutTransform(gunBody, yaw, pitch);
			setSpoutTransform(top, yaw, pitch);
			setSpoutTransform(middle, yaw, pitch);
			setSpoutTransform(bottom, yaw, pitch);
			lastYaw = yaw;
			lastPitch = pitch;
		}

		poseStack.pushPose();
		float angle = KineticBlockEntityRenderer.getAngleForBe(blockEntity, blockEntity.getBlockPos(),
			MechanicalFluidGunMount.getMountFace(blockState).getAxis());
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(Axis.YP.rotation(angle));
		poseStack.translate(-0.5, -0.5, -0.5);
		cog.setTransform(poseStack).setChanged();
		poseStack.popPose();

		updateSourceInterface();
	}

	private void setYawTransform(TransformedInstance instance, float yaw) {
		poseStack.pushPose();
		MechanicalFluidGunRenderer.transformYaw(poseStack, yaw);
		instance.setTransform(poseStack).setChanged();
		poseStack.popPose();
	}

	private void setSpoutTransform(TransformedInstance instance, float yaw, float pitch) {
		poseStack.pushPose();
		MechanicalFluidGunRenderer.transformYaw(poseStack, yaw);
		MechanicalFluidGunRenderer.transformPitch(poseStack, pitch);
		instance.setTransform(poseStack).setChanged();
		poseStack.popPose();
	}

	private void updateSourceInterface() {
		Direction currentDirection = MechanicalFluidGunMount.getMountFace(blockEntity.getBlockState()).getOpposite();
		if (currentDirection != sourceDirection) {
			instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(getSourcePartial(currentDirection)))
				.stealInstance(sourceInterface);
			sourceDirection = currentDirection;
		}
		boolean visible = blockEntity.shouldRenderSourceInterface();
		if (visible != sourceVisible) {
			sourceInterface.handle().setVisible(visible);
			sourceVisible = visible;
		}
	}

	@Override
	public void update(float partialTick) {
		lastYaw = Float.NaN;
		lastPitch = Float.NaN;
		animate(partialTick);
	}

	@Override
	public void updateLight(float partialTick) {
		relight(instances);
	}

	@Override
	protected void _delete() {
		for (TransformedInstance instance : instances) {
			instance.delete();
		}
	}

	@Override
	public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
		for (TransformedInstance instance : instances) {
			consumer.accept(instance);
		}
	}
}
