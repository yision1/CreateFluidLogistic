package com.yision.fluidlogistics.block.Faucet;

import com.yision.fluidlogistics.config.FeatureToggle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FaucetBlockEntity extends AbstractFaucetBlockEntity {

    public FaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        if (!FeatureToggle.isEnabled(FeatureToggle.FAUCET)) {
            return;
        }
        super.tick();
    }
}
