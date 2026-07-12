package com.yision.fluidlogistics.mixin.logistics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.yision.fluidlogistics.compat.ghost.FluidGhostStacks;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

@Mixin(FilterItemStack.class)
public class FilterItemStackFluidMixin {

    @Inject(
        method = "test(Lnet/minecraft/world/level/Level;Lnet/neoforged/neoforge/fluids/FluidStack;Z)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fluidlogistics$testFluid(Level world, FluidStack stack, boolean matchNBT,
            CallbackInfoReturnable<Boolean> cir) {
        ItemStack filterItem = ((FilterItemStack) (Object) this).item();
        if (!FluidGhostStacks.isFluidGhost(filterItem)) {
            return;
        }

        if (stack.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }

        FluidStack filterFluid = FluidGhostStacks.getFluid(filterItem);
        if (filterFluid.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }

        if (!matchNBT) {
            cir.setReturnValue(filterFluid.getFluid().isSame(stack.getFluid()));
            return;
        }

        cir.setReturnValue(FluidStack.isSameFluidSameComponents(filterFluid, stack));
    }
}
