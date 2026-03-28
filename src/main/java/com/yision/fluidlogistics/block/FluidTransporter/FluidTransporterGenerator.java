package com.yision.fluidlogistics.block.FluidTransporter;

import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.ModelFile;

public class FluidTransporterGenerator extends SpecialBlockStateGen {

    @Override
    protected int getXRotation(BlockState state) {
        return switch (state.getValue(FluidTransporterBlock.FACING)) {
            case DOWN -> 0;
            case UP -> 180;
            case NORTH, EAST, SOUTH, WEST -> 90;
        };
    }

    @Override
    protected int getYRotation(BlockState state) {
        Direction facing = state.getValue(FluidTransporterBlock.FACING);
        if (facing == Direction.DOWN || facing == Direction.UP) {
            return 0;
        }
        return horizontalAngle(facing) + 180;
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov,
        BlockState state) {
        String suffix = state.getValue(FluidTransporterBlock.POWERED) ? "powered" : "";
        return AssetLookup.partialBaseModel(ctx, prov, "vertical", suffix);
    }
}
