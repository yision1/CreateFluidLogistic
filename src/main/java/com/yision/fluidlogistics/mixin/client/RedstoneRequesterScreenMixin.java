package com.yision.fluidlogistics.mixin.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.foundation.utility.CreateLang;

import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterMenu;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.yision.fluidlogistics.client.RedstoneRequesterAmountsAccess;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.SlotItemHandler;

import static com.yision.fluidlogistics.util.FluidAmountHelper.adjustFactoryGaugeAmount;

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

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot instanceof SlotItemHandler) {
            int slotIndex = slot.getSlotIndex();
            if (this.menu.getCarried().isEmpty()) {
                menu.ghostInventory.setStackInSlot(slotIndex, ItemStack.EMPTY);
                CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(ItemStack.EMPTY, slotIndex));
            } else {
                ItemStack carried = this.menu.getCarried();
                
                if (hasAltDown() && GenericItemEmptying.canItemBeEmptied(this.menu.contentHolder.getLevel(), carried)) {
                    Pair<FluidStack, ItemStack> emptyResult = GenericItemEmptying.emptyItem(
                            this.menu.contentHolder.getLevel(), carried, true);
                    FluidStack fluidStack = emptyResult.getFirst();
                    
                    if (!fluidStack.isEmpty()) {
                        ItemStack virtualTank = new ItemStack(com.yision.fluidlogistics.registry.AllItems.COMPRESSED_STORAGE_TANK.get());
                        CompressedTankItem.setFluidVirtual(virtualTank, fluidStack.copyWithAmount(1));
                        menu.ghostInventory.setStackInSlot(slotIndex, virtualTank);
                        amounts.set(slotIndex, FluidAmountHelper.DEFAULT_FLUID_REQUEST_AMOUNT);
                        
                        CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(virtualTank, slotIndex));
                        return;
                    }
                }
                
                ItemStack insert = carried.copy();
                insert.setCount(1);
                menu.ghostInventory.setStackInSlot(slotIndex, insert);
                CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(insert, slotIndex));
            }
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Redirect(
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
            GuiGraphics graphics, Font font, ItemStack stack, int x, int y, String text) {
        if (stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)) {
            int guiLeft = getGuiLeft();
            int slotIndex = (x - (guiLeft + 27)) / 20;
            
            if (slotIndex >= 0 && slotIndex < amounts.size()) {
                int amount = amounts.get(slotIndex);
                String amountText = FluidAmountHelper.format(amount);
                graphics.renderItemDecorations(font, stack, x, y, amountText);
                return;
            }
        }
        graphics.renderItemDecorations(font, stack, x, y, text);
    }

    @Inject(method = "renderForeground", at = @At("TAIL"), remap = false)
    private void fluidlogistics$renderAltHintForFluidContainers(GuiGraphics graphics, int mouseX, int mouseY,
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
        if (carried.isEmpty() || !GenericItemEmptying.canItemBeEmptied(this.menu.contentHolder.getLevel(), carried)) {
            return;
        }

        graphics.renderComponentTooltip(font,
            List.of(CreateLang.translateDirect("fluidlogistics.factory_panel.hold_alt_to_set_contained_fluid")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)),
            mouseX, mouseY);
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("HEAD"), cancellable = true, remap = true)
    private void fluidlogistics$modifyFluidTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        if (this.hoveredSlot instanceof SlotItemHandler) {
            int slotIndex = this.hoveredSlot.getSlotIndex();
            if (slotIndex >= 0 && slotIndex < menu.ghostInventory.getSlots()) {
                ItemStack ghostStack = menu.ghostInventory.getStackInSlot(slotIndex);
                if (ghostStack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(ghostStack)) {
                    FluidStack fluid = CompressedTankItem.getFluid(ghostStack);
                    if (!fluid.isEmpty()) {
                        int amount = amounts.get(slotIndex);
                        String amountText = FluidAmountHelper.formatPrecise(amount);
                        List<Component> tooltip = new ArrayList<>();
                        tooltip.add(CreateLang.translate("gui.factory_panel.send_item", 
                                CreateLang.text(fluid.getHoverName().getString())
                                    .add(CreateLang.text(" x" + amountText)))
                            .color(ScrollInput.HEADER_RGB)
                            .component());
                        tooltip.add(CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                                .style(ChatFormatting.DARK_GRAY)
                                .style(ChatFormatting.ITALIC)
                                .component());
                        tooltip.add(CreateLang.translate("fluidlogistics.scroll_precise_amount")
                                .style(ChatFormatting.DARK_GRAY)
                                .style(ChatFormatting.ITALIC)
                                .component());
                        cir.setReturnValue(tooltip);
                    }
                }
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void fluidlogistics$handleFluidScroll(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        int x = getGuiLeft();
        int y = getGuiTop();

        for (int i = 0; i < amounts.size(); i++) {
            int inputX = x + 27 + i * 20;
            int inputY = y + 28;
            if (mouseX >= inputX && mouseX < inputX + 16 && mouseY >= inputY && mouseY < inputY + 16) {
                ItemStack itemStack = menu.ghostInventory.getStackInSlot(i);
                if (itemStack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(itemStack)) {
                    int newAmount = adjustFactoryGaugeAmount(amounts.get(i), Math.signum(scrollY)>0, hasShiftDown(), hasControlDown(), 1, Integer.MAX_VALUE);
                    amounts.set(i, newAmount);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
