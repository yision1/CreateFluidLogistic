package com.yision.fluidlogistics.mixin.logistics;

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
import com.yision.fluidlogistics.api.packager.ResourcePackagers;

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
        return ResourcePackagers.of(be).isPresent();
    }

    @Inject(method = "getRestockedPackager", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$getResourcePackagerOwner(
            CallbackInfoReturnable<PackagerBlockEntity> cir) {
        FactoryPanelBlockEntity self = (FactoryPanelBlockEntity) (Object) this;
        if (!self.restocker || self.getLevel() == null || !AllBlocks.FACTORY_GAUGE.has(self.getBlockState())) {
            return;
        }
        BlockPos connectedPos = self.getBlockPos().relative(
                FactoryPanelBlock.connectedDirection(self.getBlockState()).getOpposite());
        if (!self.getLevel().isLoaded(connectedPos)) {
            return;
        }
        ResourcePackagers.of(self.getLevel().getBlockEntity(connectedPos))
                .ifPresent(packager -> cir.setReturnValue(packager.owner()));
    }

}
