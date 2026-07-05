package com.yision.fluidlogistics.content.fluids.multiFluidTank;

import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.generators.ModelFile;

public class MultiFluidTankGenerator extends SpecialBlockStateGen {

    @Override
    protected int getXRotation(BlockState state) {
        return 0;
    }

    @Override
    protected int getYRotation(BlockState state) {
        return 0;
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov,
                                          BlockState state) {
        Boolean top = state.getValue(MultiFluidTankBlock.TOP);
        Boolean bottom = state.getValue(MultiFluidTankBlock.BOTTOM);
        MultiFluidTankBlock.Shape shape = state.getValue(MultiFluidTankBlock.SHAPE);

        String shapeName = "middle";
        if (top && bottom)
            shapeName = "single";
        else if (top)
            shapeName = "top";
        else if (bottom)
            shapeName = "bottom";

        String modelName = shapeName + (shape == MultiFluidTankBlock.Shape.PLAIN ? "" : "_" + shape.getSerializedName());

        return AssetLookup.partialBaseModel(ctx, prov, modelName);
    }
}
