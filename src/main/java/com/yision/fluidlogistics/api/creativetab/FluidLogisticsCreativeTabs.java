package com.yision.fluidlogistics.api.creativetab;

import java.util.List;
import java.util.function.Supplier;

import com.yision.fluidlogistics.registry.CreativeTabSectionRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

public final class FluidLogisticsCreativeTabs {
    private FluidLogisticsCreativeTabs() {
        throw new AssertionError("This class should not be instantiated");
    }

    public static void registerSection(
            ResourceLocation id,
            Component title,
            ResourceLocation bannerTexture,
            int frameCount,
            long frameDurationMillis,
            List<? extends Supplier<? extends ItemLike>> items) {
        CreativeTabSectionRegistry.instance()
                .register(id, title, bannerTexture, frameCount, frameDurationMillis, items);
    }
}
