package com.yision.fluidlogistics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yision.fluidlogistics.client.handpointer.FrogportSelectionHandler;
import com.yision.fluidlogistics.client.handpointer.HandPointerModeManager;
import com.yision.fluidlogistics.client.handpointer.HandPointerInteractionHandler;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import com.yision.fluidlogistics.ponder.FluidLogisticsPonderPlugin;
import com.yision.fluidlogistics.registry.AllSpriteShifts;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = FluidLogistics.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class FluidLogisticsClient {
    public FluidLogisticsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.register(new HandPointerInteractionHandler());
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AllSpriteShifts.register();
        PonderIndex.addPlugin(new FluidLogisticsPonderPlugin());
        FluidLogistics.LOGGER.info("HELLO FROM CLIENT SETUP");
        FluidLogistics.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation modelLocation : com.yision.fluidlogistics.registry.AllPartialModels.customModelLocations()) {
            event.register(ModelResourceLocation.standalone(modelLocation));
        }

        com.yision.fluidlogistics.registry.AllPartialModels.register();
    }

    @EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        static void onRenderWorld(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }

            if (HandPointerModeManager.getCurrentMode() != HandPointerModeManager.SelectionMode.FROGPORT) {
                return;
            }

            PoseStack ms = event.getPoseStack();
            ms.pushPose();
            SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

            FrogportSelectionHandler.tickChainTarget(Minecraft.getInstance());
            FrogportSelectionHandler.drawChainContour(ms, buffer, camera);

            buffer.draw();
            ms.popPose();
        }
    }
}
