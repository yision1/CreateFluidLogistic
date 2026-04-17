package com.yision.fluidlogistics.mixin.logistics;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.yision.fluidlogistics.api.IFluidPackager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FactoryPanelBlockEntity.class)
public abstract class FactoryPanelBlockEntityMixin {

    @Shadow(remap = false)
    public boolean restocker;

    @Shadow(remap = false)
    public boolean redraw;

    @ModifyExpressionValue(
        method = "lazyTick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z",
            remap = false
        ),
        remap = false
    )
    private boolean fluidlogistics$modifyRestockerCheck(boolean original) {
        if (original) {
            return true;
        }
        
        FactoryPanelBlockEntity self = (FactoryPanelBlockEntity) (Object) this;
        
        if (self.getLevel() == null) {
            return false;
        }
        
        BlockState state = self.getBlockState();
        BlockPos connectedPos = self.getBlockPos().relative(
            FactoryPanelBlock.connectedDirection(state).getOpposite());
        
        if (!self.getLevel().isLoaded(connectedPos)) {
            return false;
        }
        
        BlockEntity be = self.getLevel().getBlockEntity(connectedPos);
        return be instanceof IFluidPackager;
    }

    @Inject(
        method = "getRestockedPackager",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$getFluidRestockedPackager(CallbackInfoReturnable<@Nullable PackagerBlockEntity> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        
        FactoryPanelBlockEntity self = (FactoryPanelBlockEntity) (Object) this;
        
        if (!self.restocker) {
            return;
        }
        
        BlockState state = self.getBlockState();
        if (!AllBlocks.FACTORY_GAUGE.has(state)) {
            return;
        }
        
        BlockPos packagerPos = self.getBlockPos().relative(FactoryPanelBlock.connectedDirection(state).getOpposite());
        if (!self.getLevel().isLoaded(packagerPos)) {
            return;
        }
        
        BlockEntity be = self.getLevel().getBlockEntity(packagerPos);
        if (be == null) {
            return;
        }
        
        if (be instanceof PackagerBlockEntity pbe && !(pbe instanceof RepackagerBlockEntity)) {
            cir.setReturnValue(pbe);
        }
    }

}
