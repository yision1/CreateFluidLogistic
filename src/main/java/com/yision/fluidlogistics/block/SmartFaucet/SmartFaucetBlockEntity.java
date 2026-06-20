package com.yision.fluidlogistics.block.SmartFaucet;

import com.yision.fluidlogistics.block.Faucet.AbstractFaucetBlockEntity;
import com.yision.fluidlogistics.config.FeatureToggle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SmartFaucetBlockEntity extends AbstractFaucetBlockEntity {

    public SmartFaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        if (!FeatureToggle.isEnabled(FeatureToggle.SMART_FAUCET)) {
            return;
        }
        super.tick();
    }

    @Override
    protected boolean supportsFluidFilter() {
        return true;
    }

    @Override
    protected boolean supportsBeltFilling() {
        return true;
    }
}
