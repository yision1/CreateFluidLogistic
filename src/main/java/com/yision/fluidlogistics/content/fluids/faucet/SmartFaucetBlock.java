package com.yision.fluidlogistics.content.fluids.faucet;

import com.mojang.serialization.MapCodec;
import com.yision.fluidlogistics.content.fluids.faucet.AbstractFaucetBlock;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class SmartFaucetBlock extends AbstractFaucetBlock<SmartFaucetBlockEntity> {

    public static final MapCodec<SmartFaucetBlock> CODEC = simpleCodec(SmartFaucetBlock::new);

    public SmartFaucetBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected ResourceLocation getFeature() {
        return FeatureToggle.SMART_FAUCET;
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
