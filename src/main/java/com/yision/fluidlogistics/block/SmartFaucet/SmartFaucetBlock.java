package com.yision.fluidlogistics.block.SmartFaucet;

import com.yision.fluidlogistics.block.Faucet.AbstractFaucetBlock;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SmartFaucetBlock extends AbstractFaucetBlock<SmartFaucetBlockEntity> {

    public SmartFaucetBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!FeatureToggle.isEnabled(FeatureToggle.SMART_FAUCET)) {
            return null;
        }
        return super.getStateForPlacement(context);
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
