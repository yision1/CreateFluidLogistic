package com.yision.fluidlogistics.content.fluids.multiFluidAccessPort;

import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.generators.ModelFile;

public class MultiFluidAccessPortGenerator extends SpecialBlockStateGen {

    @Override
    protected int getXRotation(BlockState state) {
        return 0;
    }

    @Override
    protected int getYRotation(BlockState state) {
        return horizontalAngle(state.getValue(MultiFluidAccessPortBlock.FACING)) + 180;
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov,
        BlockState state) {
        boolean attached = state.getValue(MultiFluidAccessPortBlock.ATTACHED);
        ResourceLocation path = prov.modLoc("block/multi_fluid_access_port/block_"
            + state.getValue(MultiFluidAccessPortBlock.TARGET).getSerializedName());
        return prov.models().withExistingParent(path + (attached ? "_on" : "_off"), path)
            .texture("top", prov.modLoc("block/multi_fluid_access_port/multi-fluid_access_port_" + (attached ? "top_on" : "top")))
            .texture("down", prov.modLoc("block/multi_fluid_access_port/multi-fluid_access_port_" + (attached ? "down_on" : "down")));
    }
}
