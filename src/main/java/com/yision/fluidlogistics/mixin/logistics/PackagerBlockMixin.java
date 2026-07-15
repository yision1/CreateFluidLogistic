package com.yision.fluidlogistics.mixin.logistics;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.yision.fluidlogistics.util.IPackagerOverrideData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PackagerBlock.class)
public class PackagerBlockMixin {

    @ModifyExpressionValue(
        method = "neighborChanged",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;hasNeighborSignal(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean fluidlogistics$lockNeighborChanged(boolean original, BlockState state, Level worldIn,
            BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        if (worldIn.getBlockEntity(pos) instanceof PackagerBlockEntity packager
                && packager instanceof IPackagerOverrideData data
                && data.fluidlogistics$isManualOverrideLocked()) {
            return state.getValue(PackagerBlock.POWERED);
        }
        return original;
    }
}
