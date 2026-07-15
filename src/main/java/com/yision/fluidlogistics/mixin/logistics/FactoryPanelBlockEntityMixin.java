package com.yision.fluidlogistics.mixin.logistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.yision.fluidlogistics.api.IFluidPackager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FactoryPanelBlockEntity.class)
public abstract class FactoryPanelBlockEntityMixin {

    @Shadow(remap = false)
    public boolean restocker;

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
            return self.restocker;
        }

        BlockState state = self.getBlockState();
        BlockPos connectedPos = self.getBlockPos().relative(
            FactoryPanelBlock.connectedDirection(state).getOpposite());

        if (!self.getLevel().isLoaded(connectedPos)) {
            return self.restocker;
        }

        BlockEntity be = self.getLevel().getBlockEntity(connectedPos);
        return be instanceof IFluidPackager;
    }

}
