package com.yision.fluidlogistics.item;

import java.util.List;

import com.simibubi.create.foundation.item.ItemDescription;
import com.yision.fluidlogistics.client.FluidTooltipHelper;

import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class CompressedTankTooltipModifier extends ItemDescription.Modifier {

    public CompressedTankTooltipModifier(Item item, Palette palette) {
        super(item, palette);
    }

    @Override
    public void modify(ItemTooltipEvent context) {
        ItemStack stack = context.getItemStack();
        List<Component> fluidTooltip = FluidTooltipHelper.getVirtualCompressedTankTooltipLines(stack);

        if (!fluidTooltip.isEmpty()) {
            List<Component> tooltip = context.getToolTip();
            tooltip.clear();
            tooltip.addAll(fluidTooltip);
            return;
        }

        super.modify(context);
    }
}
