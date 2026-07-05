package com.yision.fluidlogistics.registry;

import com.yision.fluidlogistics.FluidLogistics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

@Mod.EventBusSubscriber(modid = FluidLogistics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FluidPackageRemapHelper {
    private static final ResourceLocation OLD_PRIMARY = FluidLogistics.asResource("rare_fluid_package");
    private static final ResourceLocation OLD_SECONDARY = FluidLogistics.asResource("rare_fluid_package_1");
    private static final ResourceLocation NEW_PRIMARY = FluidLogistics.asResource("fluid_package");

    private FluidPackageRemapHelper() {
    }

    @SubscribeEvent
    public static void remapItems(MissingMappingsEvent event) {
        Item target = ForgeRegistries.ITEMS.getValue(NEW_PRIMARY);
        if (target == null) {
            return;
        }

        for (MissingMappingsEvent.Mapping<Item> mapping : event.getMappings(Registries.ITEM, FluidLogistics.MODID)) {
            ResourceLocation key = mapping.getKey();
            if (OLD_PRIMARY.equals(key) || OLD_SECONDARY.equals(key)) {
                mapping.remap(target);
            }
        }
    }
}
