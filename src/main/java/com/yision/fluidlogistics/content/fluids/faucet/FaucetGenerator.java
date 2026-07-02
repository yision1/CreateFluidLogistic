package com.yision.fluidlogistics.content.fluids.faucet;

import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.ModelFile;

public class FaucetGenerator extends SpecialBlockStateGen {

    private final String closedModel;
    private final String openModel;

    public FaucetGenerator(String closedModel, String openModel) {
        this.closedModel = closedModel;
        this.openModel = openModel;
    }

    @Override
    protected int getXRotation(BlockState state) {
        return 0;
    }

    @Override
    protected int getYRotation(BlockState state) {
        Direction facing = state.getValue(AbstractFaucetBlock.FACING);
        return horizontalAngle(facing) + 180;
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov,
        BlockState state) {
        String path = state.getValue(AbstractFaucetBlock.OPEN) ? openModel : closedModel;
        return prov.models().getExistingFile(prov.modLoc("block/" + path));
    }
}
