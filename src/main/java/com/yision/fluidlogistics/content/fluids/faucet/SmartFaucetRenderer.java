package com.yision.fluidlogistics.content.fluids.faucet;

import com.yision.fluidlogistics.content.fluids.faucet.AbstractFaucetRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class SmartFaucetRenderer extends AbstractFaucetRenderer<SmartFaucetBlockEntity> {

    public SmartFaucetRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }
}
