package com.yision.fluidlogistics.mixin.client;

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

import com.simibubi.create.AllPackets;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterMenu;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.SlotItemHandler;

import static com.yision.fluidlogistics.util.FluidAmountHelper.adjustFluidRequestAmount;

@Mixin(value = RedstoneRequesterScreen.class, remap = false)
public abstract class RedstoneRequesterScreenMixin extends AbstractSimiContainerScreen<RedstoneRequesterMenu> {

    @Shadow
    @Final
    private List<Integer> amounts;

    public RedstoneRequesterScreenMixin(RedstoneRequesterMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        if (!(slot instanceof SlotItemHandler)) {
            super.slotClicked(slot, slotId, mouseButton, type);
            return;
        }

        int slotIndex = slot.getSlotIndex();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            menu.ghostInventory.setStackInSlot(slotIndex, ItemStack.EMPTY);
            AllPackets.getChannel().sendToServer(new GhostItemSubmitPacket(ItemStack.EMPTY, slotIndex));
            return;
        }

        Level level = menu.contentHolder.getLevel();
        if (hasAltDown() && level != null && GenericItemEmptying.canItemBeEmptied(level, carried)) {
            Pair<FluidStack, ItemStack> emptyResult = GenericItemEmptying.emptyItem(level, carried, true);
            FluidStack fluidStack = emptyResult.getFirst();

            if (!fluidStack.isEmpty()) {
                ItemStack virtualTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
                FluidStack template = fluidStack.copy();
                template.setAmount(1);
                CompressedTankItem.setFluidVirtual(virtualTank, template);
                menu.ghostInventory.setStackInSlot(slotIndex, virtualTank);
                amounts.set(slotIndex, FluidAmountHelper.DEFAULT_FLUID_REQUEST_AMOUNT);
                AllPackets.getChannel().sendToServer(new GhostItemSubmitPacket(virtualTank, slotIndex));
                return;
            }
        }

        ItemStack insert = carried.copy();
        insert.setCount(1);
        menu.ghostInventory.setStackInSlot(slotIndex, insert);
        AllPackets.getChannel().sendToServer(new GhostItemSubmitPacket(insert, slotIndex));
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
                String amountText = FluidAmountHelper.format(amounts.get(slotIndex));
                graphics.renderItemDecorations(font, stack, x, y, amountText);
                return;
            }
        }
        graphics.renderItemDecorations(font, stack, x, y, text);
    }

    @Inject(method = "renderForeground", at = @At("TAIL"))
    private void fluidlogistics$renderAltHintForFluidContainers(GuiGraphics graphics, int mouseX, int mouseY,
        float partialTicks, CallbackInfo ci) {
        if (!(hoveredSlot instanceof SlotItemHandler hoveredHandlerSlot)) {
            return;
        }

        int slotIndex = hoveredHandlerSlot.getSlotIndex();
        if (slotIndex < 0 || slotIndex >= menu.ghostInventory.getSlots()) {
            return;
        }

        if (!menu.ghostInventory.getStackInSlot(slotIndex).isEmpty()) {
            return;
        }

        Level level = menu.contentHolder.getLevel();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || level == null || !GenericItemEmptying.canItemBeEmptied(level, carried)) {
            return;
        }

        graphics.renderComponentTooltip(font, List.of(
            CreateLang.translateDirect("fluidlogistics.factory_panel.hold_alt_to_set_contained_fluid")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
        ), mouseX, mouseY);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = super.getTooltipFromContainerItem(stack);
        if (!(hoveredSlot instanceof SlotItemHandler)) {
            return tooltip;
        }

        int slotIndex = hoveredSlot.getSlotIndex();
        if (slotIndex < 0 || slotIndex >= menu.ghostInventory.getSlots()) {
            return tooltip;
        }

        ItemStack ghostStack = menu.ghostInventory.getStackInSlot(slotIndex);
        if (ghostStack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(ghostStack)) {
            FluidStack fluid = CompressedTankItem.getFluid(ghostStack);
            String amountText = FluidAmountHelper.formatPrecise(amounts.get(slotIndex));
            return List.of(
                CreateLang.translate("gui.factory_panel.send_item",
                    CreateLang.text(fluid.getDisplayName().getString()).add(CreateLang.text(" x" + amountText)))
                    .color(ScrollInput.HEADER_RGB)
                    .component(),
                CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                    .style(ChatFormatting.DARK_GRAY)
                    .style(ChatFormatting.ITALIC)
                    .component(),
                CreateLang.translate("fluidlogistics.scroll_precise_amount")
                    .style(ChatFormatting.DARK_GRAY)
                    .style(ChatFormatting.ITALIC)
                    .component()
            );
        }

        return tooltip;
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = true)
    private void fluidlogistics$mouseScrolled(double mouseX, double mouseY, double delta,
        CallbackInfoReturnable<Boolean> cir) {
        int x = getGuiLeft();
        int y = getGuiTop();

        for (int i = 0; i < amounts.size(); i++) {
            int inputX = x + 27 + i * 20;
            int inputY = y + 28;
            if (mouseX < inputX || mouseX >= inputX + 16 || mouseY < inputY || mouseY >= inputY + 16) {
                continue;
            }

            ItemStack itemStack = menu.ghostInventory.getStackInSlot(i);
            if (itemStack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(itemStack)) {
                int newAmount = adjustFluidRequestAmount(amounts.get(i), Math.signum(delta) > 0, hasShiftDown(),
                    hasControlDown(), 1, Integer.MAX_VALUE);
                amounts.set(i, newAmount);
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
