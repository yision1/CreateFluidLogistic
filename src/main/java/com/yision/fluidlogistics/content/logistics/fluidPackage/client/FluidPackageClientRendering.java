package com.yision.fluidlogistics.content.logistics.fluidPackage.client;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageVisual;
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;

import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class FluidPackageClientRendering {

    private FluidPackageClientRendering() {
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AllEntityTypes.PACKAGE.get(), FluidAwarePackageRenderer::new);
    }

    public static void registerFlywheelVisualizer() {
        SimpleEntityVisualizer.builder(AllEntityTypes.PACKAGE.get())
            .factory(FluidPackageClientRendering::createPackageVisual)
            .neverSkipVanillaRender()
            .apply();
    }

    private static EntityVisual<? super PackageEntity> createPackageVisual(VisualizationContext context,
                                                                          PackageEntity entity,
                                                                          float partialTick) {
        if (!entity.box.isEmpty() && entity.box.getItem() instanceof FluidPackageItem) {
            return new EmptyPackageVisual();
        }

        return new PackageVisual(context, entity, partialTick);
    }
}
