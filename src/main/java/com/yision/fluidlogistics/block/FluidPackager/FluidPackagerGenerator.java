package com.yision.fluidlogistics.block.FluidPackager;

import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.generators.ModelFile;

public class FluidPackagerGenerator extends SpecialBlockStateGen {

    @Override
    protected int getXRotation(BlockState state) {
        return 0;
    }

    @Override
    protected int getYRotation(BlockState state) {
        Direction facing = state.getValue(FluidPackagerBlock.FACING);
        if (facing.getAxis() == Axis.Y) return 0;
        return horizontalAngle(facing);
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov,
                                                BlockState state) {
        String suffix = state.getOptionalValue(FluidPackagerBlock.LINKED)
                .orElse(false) ? "linked" : state.getValue(FluidPackagerBlock.POWERED) ? "powered" : "";
        return state.getValue(FluidPackagerBlock.FACING)
                .getAxis() == Axis.Y ? AssetLookup.partialBaseModel(ctx, prov, "vertical", suffix)
                : AssetLookup.partialBaseModel(ctx, prov, suffix);
    }
}
