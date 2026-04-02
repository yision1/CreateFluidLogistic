package com.yision.fluidlogistics.mixin.item;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.clipboard.ClipboardBlockItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClipboardBlockItem.class)
public class ClipboardBlockItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$blockUseOnPackager(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            return;
        }

        Level level = context.getLevel();
        if (AllBlocks.PACKAGER.has(level.getBlockState(context.getClickedPos()))
            || AllBlocks.REPACKAGER.has(level.getBlockState(context.getClickedPos()))
            || com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(level.getBlockState(context.getClickedPos()))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void fluidlogistics$blockUseOnTargetedPackager(Level level, Player player, InteractionHand hand,
                                                           CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        HitResult hitResult = player.pick(5.0D, 0.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        if (AllBlocks.PACKAGER.has(level.getBlockState(blockHitResult.getBlockPos()))
            || AllBlocks.REPACKAGER.has(level.getBlockState(blockHitResult.getBlockPos()))
            || com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(level.getBlockState(blockHitResult.getBlockPos()))) {
            cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
        }
    }
}
