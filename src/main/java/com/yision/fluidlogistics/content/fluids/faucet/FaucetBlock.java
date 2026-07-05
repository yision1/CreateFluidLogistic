package com.yision.fluidlogistics.content.fluids.faucet;

import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class FaucetBlock extends AbstractFaucetBlock<FaucetBlockEntity> {

    public FaucetBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<FaucetBlockEntity> getBlockEntityClass() {
        return FaucetBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FaucetBlockEntity> getBlockEntityType() {
        return AllBlockEntities.FAUCET.get();
    }
}
