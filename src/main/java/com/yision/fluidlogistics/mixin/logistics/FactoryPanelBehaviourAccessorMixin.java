package com.yision.fluidlogistics.mixin.logistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.yision.fluidlogistics.api.IFluidPackager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourAccessorMixin {

    @Shadow(remap = false)
    public abstract FactoryPanelBlockEntity panelBE();

    @Inject(
        method = "getRelevantSummary",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getFluidRelevantSummary(CallbackInfoReturnable<InventorySummary> cir) {
        FactoryPanelBehaviour self = (FactoryPanelBehaviour) (Object) this;
        FactoryPanelBlockEntity panelBE = panelBE();
        
        if (panelBE.restocker) {
            net.minecraft.world.level.block.state.BlockState state = panelBE.getBlockState();
            if (!AllBlocks.FACTORY_GAUGE.has(state)) {
                return;
            }
            
            BlockPos packagerPos = panelBE.getBlockPos().relative(
                FactoryPanelBlock.connectedDirection(state).getOpposite());
            
            if (!panelBE.getLevel().isLoaded(packagerPos)) {
                return;
            }
            
            BlockEntity be = panelBE.getLevel().getBlockEntity(packagerPos);
            if (be instanceof IFluidPackager fluidPackager) {
                cir.setReturnValue(fluidPackager.getAvailableItems());
            }
        }
    }
}
