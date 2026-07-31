package com.yision.fluidlogistics.content.fluids.copperBucket.client;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.registry.AllItems;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class CopperBucketSpriteSource implements SpriteSource {

    private static final int BUCKET_COLOR_TOLERANCE = 18;

    private static final CopperBucketSpriteSource INSTANCE = new CopperBucketSpriteSource();
    private static final MapCodec<CopperBucketSpriteSource> CODEC = MapCodec.unit(INSTANCE);
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    private static final ResourceLocation EMPTY_BUCKET_SPRITE = ResourceLocation.withDefaultNamespace("item/bucket");
    private static final ResourceLocation COPPER_BUCKET_SPRITE = FluidLogistics.asResource("item/copper_bucket");

    private CopperBucketSpriteSource() {
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        Optional<Resource> emptyBucket = getTexture(resourceManager, EMPTY_BUCKET_SPRITE);
        Optional<Resource> copperBucket = getTexture(resourceManager, COPPER_BUCKET_SPRITE);
        if (emptyBucket.isEmpty() || copperBucket.isEmpty()) {
            FluidLogistics.LOGGER.warn("Cannot generate copper bucket sprites: a base bucket texture is missing");
            return;
        }

        Set<Item> seenBuckets = Collections.newSetFromMap(new IdentityHashMap<>());
        BuiltInRegistries.FLUID.forEach(fluid -> {
            Item bucket = fluid.getBucket();
            if (bucket == Items.AIR || bucket == AllItems.COPPER_BUCKET.get() || !seenBuckets.add(bucket)) {
                return;
            }

            ResourceLocation bucketId = BuiltInRegistries.ITEM.getKey(bucket);
            ResourceLocation sourceSprite = sourceSprite(bucketId);
            getTexture(resourceManager, sourceSprite).ifPresent(sourceTexture -> {
                ResourceLocation generatedSprite = generatedSprite(bucketId);
                output.add(generatedSprite, loader -> compose(loader, generatedSprite, sourceSprite,
                        sourceTexture, emptyBucket.get(), copperBucket.get()));
            });
        });
    }

    @Nullable
    private static SpriteContents compose(SpriteResourceLoader loader, ResourceLocation generatedSprite,
            ResourceLocation sourceSprite, Resource sourceTexture, Resource emptyBucketTexture,
            Resource copperBucketTexture) {
        SpriteContents source = loader.loadSprite(sourceSprite, sourceTexture);
        if (source == null) {
            return null;
        }

        try (source;
                NativeImage emptyBucket = NativeImage.read(emptyBucketTexture.open());
                NativeImage copperBucket = NativeImage.read(copperBucketTexture.open())) {
            if (source.width() != emptyBucket.getWidth() || source.height() != emptyBucket.getHeight()
                    || source.width() != copperBucket.getWidth() || source.height() != copperBucket.getHeight()) {
                FluidLogistics.LOGGER.debug("Skipping copper bucket sprite {} because its frame is not {}x{}",
                        sourceSprite, emptyBucket.getWidth(), emptyBucket.getHeight());
                return null;
            }

            NativeImage sourceImage = source.getOriginalImage();
            NativeImage result = new NativeImage(sourceImage.getWidth(), sourceImage.getHeight(), false);
            try {
                Set<Integer> emptyBucketColors = new HashSet<>();
                for (int y = 0; y < emptyBucket.getHeight(); y++) {
                    for (int x = 0; x < emptyBucket.getWidth(); x++) {
                        emptyBucketColors.add(emptyBucket.getPixelRGBA(x, y));
                    }
                }

                for (int y = 0; y < sourceImage.getHeight(); y++) {
                    for (int x = 0; x < sourceImage.getWidth(); x++) {
                        int frameX = x % source.width();
                        int frameY = y % source.height();
                        int sourcePixel = sourceImage.getPixelRGBA(x, y);
                        boolean protectedFluidPixel = isProtectedFluidPixel(frameX, frameY);
                        int resultPixel = !protectedFluidPixel && matchesBucketColor(sourcePixel, emptyBucketColors)
                                ? copperBucket.getPixelRGBA(frameX, frameY)
                                : sourcePixel;
                        result.setPixelRGBA(x, y, resultPixel);
                    }
                }

                return new SpriteContents(generatedSprite, new FrameSize(source.width(), source.height()),
                        result, source.metadata());
            } catch (RuntimeException exception) {
                result.close();
                throw exception;
            }
        } catch (IOException exception) {
            FluidLogistics.LOGGER.warn("Unable to generate copper bucket sprite from {}", sourceSprite, exception);
            return null;
        }
    }

    private static boolean isProtectedFluidPixel(int x, int y) {
        return y == 3 && x >= 4 && x <= 11
                || y == 4 && x >= 3 && x <= 12
                || y == 5 && x >= 5 && x <= 10;
    }

    private static boolean matchesBucketColor(int pixel, Set<Integer> bucketColors) {
        for (int bucketColor : bucketColors) {
            if ((pixel >>> 24) != (bucketColor >>> 24)) {
                continue;
            }
            if (Math.abs((pixel & 0xFF) - (bucketColor & 0xFF)) <= BUCKET_COLOR_TOLERANCE
                    && Math.abs((pixel >>> 8 & 0xFF) - (bucketColor >>> 8 & 0xFF)) <= BUCKET_COLOR_TOLERANCE
                    && Math.abs((pixel >>> 16 & 0xFF) - (bucketColor >>> 16 & 0xFF)) <= BUCKET_COLOR_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Resource> getTexture(ResourceManager resourceManager, ResourceLocation sprite) {
        return resourceManager.getResource(TEXTURE_ID_CONVERTER.idToFile(sprite));
    }

    public static ResourceLocation sourceSprite(ResourceLocation bucketId) {
        return ResourceLocation.fromNamespaceAndPath(bucketId.getNamespace(), "item/" + bucketId.getPath());
    }

    public static ResourceLocation generatedSprite(ResourceLocation bucketId) {
        return FluidLogistics.asResource("item/copper_bucket_generated/" + bucketId.getNamespace()
                + "/" + bucketId.getPath());
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }
}
