package com.yision.fluidlogistics.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.foundation.utility.CreateLang;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterMenu;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.PackageResourceDisplay;
import com.yision.fluidlogistics.api.packager.client.PackageResourceClient;
import com.yision.fluidlogistics.client.RedstoneRequesterAmountsAccess;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.SlotItemHandler;

@OnlyIn(Dist.CLIENT)
@Mixin(RedstoneRequesterScreen.class)
public abstract class RedstoneRequesterScreenMixin extends AbstractSimiContainerScreen<RedstoneRequesterMenu>
        implements RedstoneRequesterAmountsAccess {

    @Shadow(remap = false)
    @Final
    private List<Integer> amounts;

    public RedstoneRequesterScreenMixin(RedstoneRequesterMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    public List<Integer> fluidlogistics$getAmounts() {
        return amounts;
    }

    @WrapOperation(
        method = "renderForeground",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(" +
                     "Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            remap = true
        ),
        remap = false
    )
    private void fluidlogistics$redirectRenderItemDecorations(
            GuiGraphics graphics, Font font, ItemStack stack, int x, int y, String text,
            Operation<Void> original) {
        int guiLeft = getGuiLeft();
        int slotIndex = (x - (guiLeft + 27)) / 20;
        if (slotIndex >= 0 && slotIndex < amounts.size()) {
            var amountText = PackageResources.formatAmount(
                    stack, amounts.get(slotIndex), PackageResourceDisplay.Format.COMPACT);
            if (amountText.isPresent()) {
                original.call(graphics, font, stack, x, y, amountText.orElseThrow());
                return;
            }
        }
        original.call(graphics, font, stack, x, y, text);
    }

    @Inject(method = "renderForeground", at = @At("TAIL"), remap = false)
    private void fluidlogistics$renderRequestSelectorHint(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        if (!(this.hoveredSlot instanceof SlotItemHandler hoveredHandlerSlot)) {
            return;
        }

        int slotIndex = hoveredHandlerSlot.getSlotIndex();
        if (slotIndex < 0 || slotIndex >= menu.ghostInventory.getSlots()) {
            return;
        }

        if (!menu.ghostInventory.getStackInSlot(slotIndex).isEmpty()) {
            return;
        }

        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) {
            return;
        }

        Component hint = PackageResourceClient.getRequestSelectorHint(carried).orElse(null);
        if (hint == null) {
            if (!GenericItemEmptying.canItemBeEmptied(this.menu.contentHolder.getLevel(), carried)) {
                return;
            }
            hint = CreateLang.translateDirect("fluidlogistics.factory_panel.hold_alt_to_set_contained_fluid");
        }

        graphics.renderComponentTooltip(font,
            List.of(hint.copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)),
            mouseX, mouseY);
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("HEAD"), cancellable = true, remap = true)
    private void fluidlogistics$modifyResourceTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        if (this.hoveredSlot instanceof SlotItemHandler) {
            int slotIndex = this.hoveredSlot.getSlotIndex();
            if (slotIndex >= 0 && slotIndex < menu.ghostInventory.getSlots()) {
                ItemStack ghostStack = menu.ghostInventory.getStackInSlot(slotIndex);
                Component resourceName = PackageResources.nameOf(ghostStack).orElse(null);
                if (resourceName == null) {
                    return;
                }
                String amountText = PackageResources.formatAmount(
                        ghostStack, amounts.get(slotIndex), PackageResourceDisplay.Format.PRECISE)
                        .orElse(Integer.toString(amounts.get(slotIndex)));
                cir.setReturnValue(List.of(
                        CreateLang.translate("gui.factory_panel.send_item",
                                        resourceName.getString() + " x" + amountText)
                                .color(ScrollInput.HEADER_RGB)
                                .component(),
                        CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC)
                                .component(),
                        CreateLang.translate("fluidlogistics.scroll_precise_amount")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC)
                                .component()));
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$handleResourceScroll(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        int x = getGuiLeft();
        int y = getGuiTop();

        for (int i = 0; i < amounts.size(); i++) {
            int inputX = x + 27 + i * 20;
            int inputY = y + 28;
            if (mouseX >= inputX && mouseX < inputX + 16 && mouseY >= inputY && mouseY < inputY + 16) {
                ItemStack itemStack = menu.ghostInventory.getStackInSlot(i);
                int steps = Mth.ceil(Math.abs(scrollY));
                var adjusted = PackageResources.adjustAmount(itemStack, new PackageResourceDisplay.Adjustment(
                        amounts.get(i),
                        scrollY > 0,
                        hasShiftDown(),
                        hasControlDown(),
                        1,
                        Integer.MAX_VALUE,
                        steps,
                        PackageResourceDisplay.Interaction.REDSTONE_REQUESTER));
                if (adjusted.isPresent()) {
                    amounts.set(i, adjusted.getAsInt());
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
