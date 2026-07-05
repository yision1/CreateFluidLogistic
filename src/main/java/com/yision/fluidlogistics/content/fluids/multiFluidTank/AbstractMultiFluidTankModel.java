package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;

import net.createmod.catnip.data.Iterate;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

public abstract class AbstractMultiFluidTankModel extends CTModel {

    protected static final ModelProperty<CullData> CULL_PROPERTY = new ModelProperty<>();

    protected AbstractMultiFluidTankModel(BakedModel originalModel, CTSpriteShiftEntry side, CTSpriteShiftEntry top,
            CTSpriteShiftEntry inner, ConnectedTextureBehaviour behaviour) {
        super(originalModel, behaviour);
    }

    protected abstract CullData createCullData();

    protected abstract boolean shouldCheck(Direction direction, BlockState state);

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos,
            BlockState state, ModelData blockEntityData) {
        super.gatherModelData(builder, world, pos, state, blockEntityData);
        CullData cullData = createCullData();
        for (Direction d : Iterate.directions) {
            if (!shouldCheck(d, state)) {
                continue;
            }
            cullData.setCulled(d, ConnectivityHandler.isConnected(world, pos, pos.relative(d)));
        }
        return builder.with(CULL_PROPERTY, cullData);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
            RenderType renderType) {
        if (side != null) {
            return Collections.emptyList();
        }

        List<BakedQuad> quads = new ArrayList<>();
        for (Direction d : Iterate.directions) {
            if (extraData.has(CULL_PROPERTY) && extraData.get(CULL_PROPERTY)
                    .isCulled(d)) {
                continue;
            }
            quads.addAll(super.getQuads(state, d, rand, extraData, renderType));
        }
        quads.addAll(super.getQuads(state, null, rand, extraData, renderType));
        return quads;
    }

    public abstract static class CullData {
        protected final boolean[] culledFaces;

        protected CullData(int size) {
            culledFaces = new boolean[size];
            java.util.Arrays.fill(culledFaces, false);
        }

        public abstract void setCulled(Direction face, boolean cull);

        public abstract boolean isCulled(Direction face);
    }
}
