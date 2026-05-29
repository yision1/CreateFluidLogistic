package com.yision.fluidlogistics.block.Faucet;

import com.mojang.serialization.MapCodec;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class FaucetBlock extends AbstractFaucetBlock<FaucetBlockEntity> {

    public static final MapCodec<FaucetBlock> CODEC = simpleCodec(FaucetBlock::new);

    public FaucetBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
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
