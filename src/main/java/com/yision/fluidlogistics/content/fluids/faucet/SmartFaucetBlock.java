package com.yision.fluidlogistics.content.fluids.faucet;

import com.yision.fluidlogistics.content.fluids.faucet.AbstractFaucetBlock;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class SmartFaucetBlock extends AbstractFaucetBlock<SmartFaucetBlockEntity> {

    public SmartFaucetBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<SmartFaucetBlockEntity> getBlockEntityClass() {
        return SmartFaucetBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SmartFaucetBlockEntity> getBlockEntityType() {
        return AllBlockEntities.SMART_FAUCET.get();
    }
}
