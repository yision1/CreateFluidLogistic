package com.yision.fluidlogistics.content.fluids.faucet;

import com.mojang.serialization.MapCodec;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class SmartFaucetBlock extends FaucetBlock {

    public static final MapCodec<SmartFaucetBlock> CODEC = simpleCodec(SmartFaucetBlock::new);

    public SmartFaucetBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntityType<? extends FaucetBlockEntity> getBlockEntityType() {
        return AllBlockEntities.SMART_FAUCET.get();
    }
}
