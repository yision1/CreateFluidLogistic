package com.yision.fluidlogistics.block.FluidRepackager;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class FluidRepackagerVisual extends AbstractBlockEntityVisual<FluidRepackagerBlockEntity> implements SimpleDynamicVisual {
    public final TransformedInstance hatch;
    public final TransformedInstance tray;

    public float lastTrayOffset = Float.NaN;
    public PartialModel lastHatchPartial;

    public FluidRepackagerVisual(VisualizationContext ctx, FluidRepackagerBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        lastHatchPartial = FluidRepackagerRenderer.getHatchModel(blockEntity);
        hatch = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(lastHatchPartial))
                .createInstance();

        tray = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(FluidRepackagerRenderer.getTrayModel()))
                .createInstance();

        Direction facing = blockState.getValue(FluidRepackagerBlock.FACING)
                .getOpposite();

        var lowerCorner = Vec3.atLowerCornerOf(facing.getNormal());
        hatch.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(lowerCorner
                        .scale(.49999f))
                .rotateYCenteredDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXCenteredDegrees(AngleHelper.verticalAngle(facing))
                .setChanged();

        animate(partialTick);
    }

    @Override
    public void beginFrame(Context ctx) {
        animate(ctx.partialTick());
    }

    public void animate(float partialTick) {
        var hatchPartial = FluidRepackagerRenderer.getHatchModel(blockEntity);

        if (hatchPartial != this.lastHatchPartial) {
            instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(hatchPartial))
                    .stealInstance(hatch);

            this.lastHatchPartial = hatchPartial;
        }

        float trayOffset = blockEntity.getTrayOffset(partialTick);

        if (trayOffset != lastTrayOffset) {
            Direction facing = blockState.getValue(FluidRepackagerBlock.FACING)
                    .getOpposite();

            var lowerCorner = Vec3.atLowerCornerOf(facing.getNormal());

            if (trayOffset <= 0.5f) {
                tray.setIdentityTransform()
                        .translate(getVisualPosition())
                        .translate(lowerCorner.scale(trayOffset))
                        .rotateYCenteredDegrees(facing.toYRot())
                        .setChanged();
            } else {
                tray.setIdentityTransform()
                        .translate(getVisualPosition())
                        .translate(0, -1000, 0)
                        .setChanged();
            }

            lastTrayOffset = trayOffset;
        }
    }

    @Override
    public void updateLight(float partialTick) {
        relight(hatch, tray);
    }

    @Override
    protected void _delete() {
        hatch.delete();
        tray.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
    }
}
