package com.yision.fluidlogistics.content.fluids.faucet;

import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class SmartFaucetBlock extends FaucetBlock {

    public SmartFaucetBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends FaucetBlockEntity> getBlockEntityType() {
        return AllBlockEntities.SMART_FAUCET.get();
    }
}
