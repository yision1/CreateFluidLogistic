package com.yision.fluidlogistics.content.fluids.faucet;

import com.yision.fluidlogistics.content.fluids.faucet.AbstractFaucetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SmartFaucetBlockEntity extends AbstractFaucetBlockEntity {

    public SmartFaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
