package com.yision.fluidlogistics.ponder;

import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.FrogAndConveyorScenes;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.yision.fluidlogistics.registry.AllBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CopperFrogportPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return Create.ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> registration =
                helper.withKeyFunction(RegistryEntry::getId);

        registration.forComponents(AllBlocks.COPPER_FROGPORT)
                .addStoryBoard("high_logistics/package_frogport", FrogAndConveyorScenes::frogPort);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<ItemProviderEntry<?, ?>> registration =
                helper.withKeyFunction(RegistryEntry::getId);

        registration.addToTag(AllCreatePonderTags.HIGH_LOGISTICS)
                .add(AllBlocks.COPPER_FROGPORT);
    }
}
