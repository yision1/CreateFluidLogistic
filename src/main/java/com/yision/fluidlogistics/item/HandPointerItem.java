package com.yision.fluidlogistics.item;

import com.yision.fluidlogistics.config.FeatureToggle;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class HandPointerItem extends Item {

    public HandPointerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!FeatureToggle.isEnabled(FeatureToggle.HAND_POINTER)) {
            return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        }
        return InteractionResultHolder.success(player.getItemInHand(usedHand));
    }
}
