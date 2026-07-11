package com.yision.fluidlogistics.content.logistics.copperFrogport;

import com.yision.fluidlogistics.registry.AllPartialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public class CopperFrogportVisual extends AbstractBlockEntityVisual<CopperFrogportBlockEntity>
    implements SimpleDynamicVisual {

    private final TransformedInstance body;
    private TransformedInstance head;
    private final TransformedInstance tongue;
    private final TransformedInstance rig;
    private final TransformedInstance box;
    private final Matrix4f basePose = new Matrix4f();
    private boolean lastGoggles;

    public CopperFrogportVisual(VisualizationContext context, CopperFrogportBlockEntity blockEntity,
                                float partialTick) {
        super(context, blockEntity, partialTick);

        body = context.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.COPPER_FROGPORT_BODY))
            .createInstance();
        head = context.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.COPPER_FROGPORT_HEAD))
            .createInstance();
        tongue = context.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.COPPER_FROGPORT_TONGUE))
            .createInstance();
        rig = context.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.block(Blocks.AIR.defaultBlockState()))
            .createInstance();
        box = context.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.block(Blocks.AIR.defaultBlockState()))
            .createInstance();

        rig.handle().setVisible(false);
        box.handle().setVisible(false);
        animate(partialTick);
    }

    @Override
    public void beginFrame(Context context) {
        animate(context.partialTick());
    }

    private void animate(float partialTicks) {
        updateGoggles();

        CopperFrogportAnimationState animation = CopperFrogportAnimationState.create(blockEntity, partialTicks);
        if (animation.animating()) {
            renderPackage(animation);
        } else {
            rig.handle().setVisible(false);
            box.handle().setVisible(false);
        }

        body.setIdentityTransform()
            .translate(getVisualPosition())
            .center()
            .rotate(animation.orientation())
            .uncenter()
            .center()
            .rotateYDegrees(animation.yaw())
            .uncenter()
            .setChanged();

        basePose.set(body.pose)
            .translate(8 / 16f, 10 / 16f, 11 / 16f);

        head.setTransform(basePose)
            .rotateXDegrees(animation.headPitch())
            .translateBack(8 / 16f, 10 / 16f, 11 / 16f)
            .setChanged();

        tongue.setTransform(basePose)
            .rotateXDegrees(animation.tonguePitch())
            .scale(1, 1, animation.tongueLength() / (7 / 16f))
            .translateBack(8 / 16f, 10 / 16f, 11 / 16f)
            .setChanged();
    }

    private void updateGoggles() {
        if (blockEntity.goggles == lastGoggles) {
            return;
        }

        head.delete();
        head = instancerProvider()
            .instancer(
                InstanceTypes.TRANSFORMED,
                Models.partial(blockEntity.goggles
                    ? AllPartialModels.COPPER_FROGPORT_HEAD_GOGGLES
                    : AllPartialModels.COPPER_FROGPORT_HEAD)
            )
            .createInstance();
        lastGoggles = blockEntity.goggles;
        updateLight(0);
    }

    private void renderPackage(CopperFrogportAnimationState animation) {
        if (blockEntity.animatedPackage == null || animation.packageScale() < 0.45) {
            rig.handle().setVisible(false);
            box.handle().setVisible(false);
            return;
        }

        ResourceLocation key = BuiltInRegistries.ITEM.getKey(blockEntity.animatedPackage.getItem());
        if (key == BuiltInRegistries.ITEM.getDefaultKey()) {
            rig.handle().setVisible(false);
            box.handle().setVisible(false);
            return;
        }

        boolean depositing = blockEntity.currentlyDepositing;
        var worldOffset = animation.packageOffset(blockEntity);
        instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED,
                Models.partial(com.simibubi.create.AllPartialModels.PACKAGES.get(key)))
            .stealInstance(box);
        box.handle().setVisible(true);

        box.setIdentityTransform()
            .translate(getVisualPosition())
            .translate(worldOffset)
            .center()
            .scale(animation.packageScale())
            .uncenter()
            .setChanged();

        if (!depositing) {
            rig.handle().setVisible(false);
            return;
        }

        instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED,
                Models.partial(com.simibubi.create.AllPartialModels.PACKAGE_RIGGING.get(key)))
            .stealInstance(rig);
        rig.handle().setVisible(true);
        rig.pose.set(box.pose);
        rig.setChanged();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(body);
        consumer.accept(head);
    }

    @Override
    public void updateLight(float partialTick) {
        relight(body, head, tongue, rig, box);
    }

    @Override
    protected void _delete() {
        body.delete();
        head.delete();
        tongue.delete();
        rig.delete();
        box.delete();
    }
}
