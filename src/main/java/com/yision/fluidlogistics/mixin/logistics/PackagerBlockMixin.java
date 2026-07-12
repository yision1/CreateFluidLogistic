package com.yision.fluidlogistics.mixin.logistics;

import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.yision.fluidlogistics.util.IPackagerOverrideData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackagerBlock.class)
public class PackagerBlockMixin {

    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$lockNeighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn,
                                                    BlockPos fromPos, boolean isMoving, CallbackInfo ci) {
        if (worldIn.isClientSide) {
            return;
        }

        if (!(worldIn.getBlockEntity(pos) instanceof PackagerBlockEntity packager)) {
            return;
        }
        if (!(packager instanceof IPackagerOverrideData data) || !data.fluidlogistics$isManualOverrideLocked()) {
            return;
        }

        InvManipulationBehaviour behaviour = BlockEntityBehaviour.get(worldIn, pos, InvManipulationBehaviour.TYPE);
        if (behaviour != null) {
            behaviour.onNeighborChanged(fromPos);
        }

        ci.cancel();
    }
}
