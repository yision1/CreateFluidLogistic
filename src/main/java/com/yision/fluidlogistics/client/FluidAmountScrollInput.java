package com.yision.fluidlogistics.client;

import java.util.function.Supplier;

import com.simibubi.create.foundation.gui.widget.ScrollInput;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class FluidAmountScrollInput extends ScrollInput {

    private Supplier<Component> secondaryHeader;

    public FluidAmountScrollInput(int xIn, int yIn, int widthIn, int heightIn) {
        super(xIn, yIn, widthIn, heightIn);
    }

    public FluidAmountScrollInput withSecondaryHeader(Supplier<Component> secondaryHeader) {
        this.secondaryHeader = secondaryHeader;
        updateTooltip();
        return this;
    }

    @Override
    protected void updateTooltip() {
        toolTip.clear();
        if (title == null) {
            return;
        }

        toolTip.add(title.plainCopy().withStyle(s -> s.withColor(HEADER_RGB.getRGB())));

        if (secondaryHeader != null) {
            Component component = secondaryHeader.get();
            if (component != null) {
                toolTip.add(component.copy().withStyle(s -> s.withColor(HEADER_RGB.getRGB())));
            }
        }

        if (hint != null) {
            toolTip.add(hint.plainCopy().withStyle(s -> s.withColor(HINT_RGB.getRGB())));
        }

        toolTip.add(scrollToModify.plainCopy().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        toolTip.add(CreateLang.translate("fluidlogistics.scroll_precise_amount")
                .style(ChatFormatting.DARK_GRAY)
                .style(ChatFormatting.ITALIC)
                .component());
        //toolTip.add(shiftScrollsFaster.plainCopy().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }
}
