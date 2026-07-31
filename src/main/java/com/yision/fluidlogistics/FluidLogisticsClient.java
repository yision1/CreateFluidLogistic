package com.yision.fluidlogistics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yision.fluidlogistics.content.equipment.handPointer.client.FrogportSelectionHandler;
import com.yision.fluidlogistics.content.equipment.handPointer.client.HandPointerModeManager;
import com.yision.fluidlogistics.client.event.FluidSlotClickHandler;
import com.yision.fluidlogistics.api.packager.PackageResourceTypes;
import com.yision.fluidlogistics.api.packager.client.PackageResourceClient;
import com.yision.fluidlogistics.content.fluids.copperBucket.client.CopperBucketColor;
import com.yision.fluidlogistics.content.fluids.copperBucket.client.CopperBucketModel;
import com.yision.fluidlogistics.content.fluids.copperBucket.client.CopperBucketSpriteSource;
import com.yision.fluidlogistics.content.logistics.fluidPackage.client.FluidPackageClientRendering;
import com.yision.fluidlogistics.ponder.CopperBasinPonderPlugin;
import com.yision.fluidlogistics.ponder.CopperFrogportPonderPlugin;
import com.yision.fluidlogistics.ponder.FluidLogisticsPonderPlugin;
import com.yision.fluidlogistics.registry.AllFluidLogisticsParticleTypes;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.registry.AllPartialModels;
import com.yision.fluidlogistics.render.FactoryPanelFluidPreviewRenderer;
import com.yision.fluidlogistics.render.FluidSlotAmountRenderer;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.resource.PathPackResources;

import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FluidLogisticsClient {

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new CopperBasinPonderPlugin());
        PonderIndex.addPlugin(new FluidLogisticsPonderPlugin());
        PonderIndex.addPlugin(new CopperFrogportPonderPlugin());
        PackageResourceClient.registerStockKeeperAmountRenderer(
            PackageResourceTypes.FLUID, FluidSlotAmountRenderer::renderInStockKeeper);
        PackageResourceClient.registerFactoryPanelPreviewRenderer(
            PackageResourceTypes.FLUID, FactoryPanelFluidPreviewRenderer::render);
        MinecraftForge.EVENT_BUS.register(FluidSlotClickHandler.class);
        event.enqueueWork(() -> {
            com.simibubi.create.content.contraptions.wrench.RadialWrenchMenu
                .registerBlacklistedBlock(com.yision.fluidlogistics.registry.AllBlocks.MECHANICAL_FLUID_GUN.getId());
            FluidPackageClientRendering.registerFlywheelVisualizer();
        });
    }

    @SubscribeEvent
    static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation bucketLocation = new ModelResourceLocation(AllItems.COPPER_BUCKET.getId(), "inventory");
        var bucketModel = event.getModels().get(bucketLocation);
        if (bucketModel != null) {
            event.getModels().put(bucketLocation, new CopperBucketModel(bucketModel));
        }
    }

    @SubscribeEvent
    static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(new CopperBucketColor(), AllItems.COPPER_BUCKET.get());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        FluidPackageClientRendering.registerEntityRenderers(event);
    }

    @SubscribeEvent
    static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        CopperBucketSpriteSource.register();

        IModFile modFile = ModList.get().getModFileById(FluidLogistics.MODID).getFile();
        Path packPath = modFile.findResource("resourcepacks/cu_again_for_fluidlogistics");
        event.addRepositorySource(consumer -> consumer.accept(Pack.create(
                FluidLogistics.asResource("cu_again_for_fluidlogistics").toString(),
                Component.translatable("resourcepack.fluidlogistics.cu_again_for_fluidlogistics"),
                false,
                id -> new PathPackResources(id, true, packPath),
                new Pack.Info(Component.empty(), SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES), FeatureFlagSet.of()),
                PackType.CLIENT_RESOURCES,
                Pack.Position.TOP,
                false,
                PackSource.DEFAULT
        )));
    }

    @SubscribeEvent
    static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation modelLocation : AllPartialModels.customModelLocations()) {
            event.register(modelLocation);
        }

        AllPartialModels.register();
    }

    @SubscribeEvent
    static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        AllFluidLogisticsParticleTypes.registerFactories(event);
    }

    @Mod.EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents {
        @SubscribeEvent
        static void onRenderLevelStage(RenderLevelStageEvent event) {
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
