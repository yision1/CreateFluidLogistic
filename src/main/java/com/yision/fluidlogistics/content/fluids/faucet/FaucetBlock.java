package com.yision.fluidlogistics.content.fluids.faucet;

import com.mojang.serialization.MapCodec;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.resources.ResourceLocation;
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
    protected ResourceLocation getFeature() {
        return FeatureToggle.FAUCET;
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
