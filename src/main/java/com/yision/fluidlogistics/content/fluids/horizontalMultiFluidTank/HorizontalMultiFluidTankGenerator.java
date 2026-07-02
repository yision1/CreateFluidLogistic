package com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank;

import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.ModelFile;

import static com.yision.fluidlogistics.content.fluids.horizontalMultiFluidTank.HorizontalMultiFluidTankBlock.*;
import static net.minecraft.core.Direction.Axis;

public class HorizontalMultiFluidTankGenerator extends SpecialBlockStateGen {

    public HorizontalMultiFluidTankGenerator() {
    }

    @Override
    protected int getXRotation(BlockState state) {
        return 0;
    }

    @Override
    protected int getYRotation(BlockState state) {
        return 0;
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov, BlockState state) {
        Boolean positive = state.getValue(POSITIVE);
        Boolean negative = state.getValue(NEGATIVE);
        Shape shape = state.getValue(SHAPE);
        Axis axis = state.getValue(AXIS);

        if (positive && negative)
            shape = shape.nonSingleVariant();

        String shapeName = "middle";
        if (positive && negative)
            shapeName = "single";
        else if (positive)
            shapeName = "positive";
        else if (negative)
            shapeName = "negative";

        String modelName = (axis == Axis.X ? "x" : "z") +
                "_" + shapeName +
                (shape == Shape.PLAIN ? "" : "_" + shape.getSerializedName());

        return AssetLookup.partialBaseModel(ctx, prov, modelName);
    }
}
