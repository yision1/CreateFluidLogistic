package com.yision.fluidlogistics.client;

import java.util.function.Supplier;

import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class ResourceAmountScrollInput extends ScrollInput {
    private Supplier<Component> secondaryHeader;
    private Supplier<Boolean> hasRedstoneLink = () -> false;

    public ResourceAmountScrollInput(int xIn, int yIn, int widthIn, int heightIn) {
        super(xIn, yIn, widthIn, heightIn);
    }

    public ResourceAmountScrollInput withSecondaryHeader(Supplier<Component> secondaryHeader) {
        this.secondaryHeader = secondaryHeader;
        updateTooltip();
        return this;
    }

    public ResourceAmountScrollInput withRedstoneLinkInfo(Supplier<Boolean> hasRedstoneLink) {
        this.hasRedstoneLink = hasRedstoneLink;
        updateTooltip();
        return this;
    }

    @Override
    protected void updateTooltip() {
        toolTip.clear();
        if (title == null) {
            return;
        }
        toolTip.add(title.plainCopy().withStyle(style -> style.withColor(HEADER_RGB.getRGB())));
        if (secondaryHeader != null) {
            Component component = secondaryHeader.get();
            if (component != null) {
                toolTip.add(component.copy().withStyle(style -> style.withColor(HEADER_RGB.getRGB())));
            }
        }
        if (hint != null) {
            toolTip.add(hint.plainCopy().withStyle(style -> style.withColor(HINT_RGB.getRGB())));
        }
        toolTip.add(scrollToModify.plainCopy().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        toolTip.add(CreateLang.translate("fluidlogistics.scroll_precise_amount")
                .style(ChatFormatting.DARK_GRAY)
                .style(ChatFormatting.ITALIC)
                .component());
        if (hasRedstoneLink.get()) {
            toolTip.add(Component.empty());
            toolTip.add(CreateLang.translate("gui.factory_panel.has_link_connections")
                    .component().withStyle(style -> style.withColor(HEADER_RGB.getRGB())));
            toolTip.add(CreateLang.translate("gui.factory_panel.left_click_disconnect")
                    .component().withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        }
    }
}
