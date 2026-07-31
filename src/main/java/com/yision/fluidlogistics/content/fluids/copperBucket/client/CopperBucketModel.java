package com.yision.fluidlogistics.content.fluids.copperBucket.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import com.yision.fluidlogistics.content.fluids.copperBucket.CopperBucketItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.fluids.FluidStack;

public class CopperBucketModel extends BakedModelWrapper<BakedModel> {

    private final ItemOverrides overrides;

    public CopperBucketModel(BakedModel fallbackModel) {
        super(fallbackModel);
        overrides = new Overrides(fallbackModel);
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    private static class Overrides extends ItemOverrides {

        private final BakedModel fallbackModel;
        private final ItemOverrides fallbackOverrides;
        private final Map<Item, Optional<BakedModel>> cache = new IdentityHashMap<>();

        private Overrides(BakedModel fallbackModel) {
            this.fallbackModel = fallbackModel;
            fallbackOverrides = fallbackModel.getOverrides();
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                @Nullable LivingEntity entity, int seed) {
            BakedModel fallback = fallbackOverrides.resolve(fallbackModel, stack, level, entity, seed);
            FluidStack fluid = CopperBucketItem.getFluid(stack);
            if (fluid.isEmpty()) {
                return fallback;
            }

            Item bucketItem = fluid.getFluid().getBucket();
            if (bucketItem == Items.AIR || bucketItem == stack.getItem()) {
                return fallback;
            }

            Optional<BakedModel> replacement = cache.computeIfAbsent(bucketItem,
                    item -> createReplacement(item, level, entity, seed));
            return replacement.orElse(fallback);
        }

        private static Optional<BakedModel> createReplacement(Item bucketItem, @Nullable ClientLevel level,
                @Nullable LivingEntity entity, int seed) {
            ItemStack bucketStack = new ItemStack(bucketItem);
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel bucketModel = minecraft.getItemRenderer().getModel(bucketStack, level, entity, seed);
            if (bucketModel == minecraft.getModelManager().getMissingModel()
                    || bucketModel.isCustomRenderer() || bucketModel.isGui3d()) {
                return Optional.empty();
            }

            ResourceLocation bucketId = BuiltInRegistries.ITEM.getKey(bucketItem);
            ResourceLocation sourceSprite = CopperBucketSpriteSource.sourceSprite(bucketId);
            TextureAtlasSprite replacementSprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(CopperBucketSpriteSource.generatedSprite(bucketId));
            if (replacementSprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return Optional.empty();
            }

            return RetexturedBucketModel.create(bucketModel, bucketStack, sourceSprite, replacementSprite);
        }
    }

    private static class RetexturedBucketModel extends BakedModelWrapper<BakedModel> {

        private final List<BakedModel> normalPasses;
        private final List<BakedModel> fabulousPasses;

        private RetexturedBucketModel(BakedModel bucketModel, List<BakedModel> normalPasses,
                List<BakedModel> fabulousPasses) {
            super(bucketModel);
            this.normalPasses = normalPasses;
            this.fabulousPasses = fabulousPasses;
        }

        private static Optional<BakedModel> create(BakedModel bucketModel, ItemStack bucketStack,
                ResourceLocation sourceSprite, TextureAtlasSprite replacementSprite) {
            Passes normal = createPasses(bucketModel, bucketStack, sourceSprite, replacementSprite, false);
            Passes fabulous = createPasses(bucketModel, bucketStack, sourceSprite, replacementSprite, true);
            if (!normal.compatible() || !fabulous.compatible()) {
                return Optional.empty();
            }
            return Optional.of(new RetexturedBucketModel(bucketModel, normal.models(), fabulous.models()));
        }

        private static Passes createPasses(BakedModel bucketModel, ItemStack bucketStack,
                ResourceLocation sourceSprite, TextureAtlasSprite replacementSprite, boolean fabulous) {
            List<BakedModel> passes = new ArrayList<>();
            boolean replaced = false;
            for (BakedModel pass : bucketModel.getRenderPasses(bucketStack, fabulous)) {
                RetexturedPass retextured = new RetexturedPass(pass, bucketStack, sourceSprite, replacementSprite);
                if (retextured.hasTintedReplacement()) {
                    return Passes.INCOMPATIBLE;
                }
                replaced |= retextured.hasReplacement();
                passes.add(retextured);
            }
            return replaced ? new Passes(List.copyOf(passes), true) : Passes.INCOMPATIBLE;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext displayContext, PoseStack poseStack, boolean leftHand) {
            super.applyTransform(displayContext, poseStack, leftHand);
            return this;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return fabulous ? fabulousPasses : normalPasses;
        }
    }

    private record Passes(List<BakedModel> models, boolean compatible) {
        private static final Passes INCOMPATIBLE = new Passes(List.of(), false);
    }

    private static class RetexturedPass extends BakedModelWrapper<BakedModel> {

        private final ItemStack bucketStack;
        private final Map<Direction, List<BakedQuad>> sideQuads = new EnumMap<>(Direction.class);
        private final List<BakedQuad> unculledQuads;
        private boolean hasReplacement;
        private boolean hasTintedReplacement;

        private RetexturedPass(BakedModel model, ItemStack bucketStack, ResourceLocation sourceSprite,
                TextureAtlasSprite replacementSprite) {
            super(model);
            this.bucketStack = bucketStack;
            RandomSource random = RandomSource.create();
            for (Direction direction : Direction.values()) {
                random.setSeed(42L);
                sideQuads.put(direction, replaceSprite(model.getQuads(null, direction, random),
                        sourceSprite, replacementSprite));
            }
            random.setSeed(42L);
            unculledQuads = replaceSprite(model.getQuads(null, null, random), sourceSprite, replacementSprite);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable net.minecraft.world.level.block.state.BlockState state,
                @Nullable Direction side, RandomSource random) {
            return side == null ? unculledQuads : sideQuads.get(side);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return originalModel.getRenderTypes(bucketStack, fabulous);
        }

        private List<BakedQuad> replaceSprite(List<BakedQuad> quads, ResourceLocation sourceSprite,
                TextureAtlasSprite replacementSprite) {
            return quads.stream().map(quad -> {
                TextureAtlasSprite originalSprite = quad.getSprite();
                if (!originalSprite.contents().name().equals(sourceSprite)) {
                    return quad;
                }

                hasReplacement = true;
                if (quad.getTintIndex() >= 0 && Minecraft.getInstance().getItemColors()
                        .getColor(bucketStack, quad.getTintIndex()) != -1) {
                    hasTintedReplacement = true;
                    return quad;
                }

                int[] vertices = quad.getVertices().clone();
                for (int vertex = 0; vertex < vertices.length / BakedQuadHelper.VERTEX_STRIDE; vertex++) {
                    float u = BakedQuadHelper.getU(vertices, vertex);
                    float v = BakedQuadHelper.getV(vertices, vertex);
                    BakedQuadHelper.setU(vertices, vertex,
                            replacementSprite.getU(originalSprite.getUOffset(u)));
                    BakedQuadHelper.setV(vertices, vertex,
                            replacementSprite.getV(originalSprite.getVOffset(v)));
                }
                return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), replacementSprite,
                        quad.isShade(), quad.hasAmbientOcclusion());
            }).toList();
        }

        private boolean hasReplacement() {
            return hasReplacement;
        }

        private boolean hasTintedReplacement() {
            return hasTintedReplacement;
        }
    }
}
