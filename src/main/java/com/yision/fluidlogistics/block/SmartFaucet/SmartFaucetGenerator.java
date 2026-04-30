package com.yision.fluidlogistics.block.SmartFaucet;

import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.ModelFile;

public class SmartFaucetGenerator extends SpecialBlockStateGen {

    @Override
    protected int getXRotation(BlockState state) {
        return 0;
    }

    @Override
    protected int getYRotation(BlockState state) {
        Direction facing = state.getValue(SmartFaucetBlock.FACING);
        return horizontalAngle(facing);
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov,
        BlockState state) {
        String path = state.getValue(SmartFaucetBlock.OPEN) ? "block/smart_faucet/smart_faucet_open" : "block/smart_faucet/smart_faucet";
        return prov.models().getExistingFile(prov.modLoc(path));
    }
}
