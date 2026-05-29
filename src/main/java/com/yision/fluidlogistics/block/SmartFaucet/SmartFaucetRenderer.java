package com.yision.fluidlogistics.block.SmartFaucet;

import com.yision.fluidlogistics.block.Faucet.AbstractFaucetRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class SmartFaucetRenderer extends AbstractFaucetRenderer<SmartFaucetBlockEntity> {
    public SmartFaucetRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }
}
