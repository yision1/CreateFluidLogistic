package com.yision.fluidlogistics.mixin.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.render.FluidRenderEntry;
import com.yision.fluidlogistics.render.FluidSlotRenderer;
import com.yision.fluidlogistics.util.FluidAmountHelper;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
@Mixin(FactoryPanelScreen.class)
public abstract class FactoryPanelScreenMixin extends AbstractSimiScreen {

    @Shadow(remap = false)
    private List<BigItemStack> inputConfig;

    @Shadow(remap = false)
    private BigItemStack outputConfig;

    @Shadow(remap = false)
    private FactoryPanelBehaviour behaviour;

    @Shadow(remap = false)
    private boolean restocker;

    protected FactoryPanelScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private static final int fluidlogistics$MAX_BUCKETS = 1000;
    
    @Unique
    private static final int fluidlogistics$MB_PER_BUCKET = 1000;

    @Unique
    private boolean fluidlogistics$isVirtualTank = false;

    @Unique
    private FluidStack fluidlogistics$cachedFluid = null;
    
    @Unique
    private int fluidlogistics$cachedFluidX = 0;
    
    @Unique
    private int fluidlogistics$cachedFluidY = 0;

    @Inject(
        method = "renderInputItem",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$onRenderInputItemHead(GuiGraphics graphics, int slot, BigItemStack itemStack, 
            int mouseX, int mouseY, CallbackInfo ci) {
        fluidlogistics$isVirtualTank = false;
        fluidlogistics$cachedFluid = null;

        if (itemStack.stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(itemStack.stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(itemStack.stack);
            if (!fluid.isEmpty()) {
                fluidlogistics$isVirtualTank = true;
                fluidlogistics$cachedFluid = fluid;
                fluidlogistics$cachedFluidX = guiLeft + (restocker ? 88 : 68 + (slot % 3 * 20));
                fluidlogistics$cachedFluidY = guiTop + (restocker ? 12 : 28) + (slot / 3 * 20);
            }
        }
    }

    @Unique
    private List<FluidRenderEntry> fluidlogistics$pendingFluidRenders = new ArrayList<>();

    @Inject(
        method = "renderWindow",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$onRenderWindowHead(CallbackInfo ci) {
        fluidlogistics$pendingFluidRenders.clear();
    }

    @Redirect(
        method = "renderInputItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(" +
                     "Lnet/minecraft/world/item/ItemStack;II)V",
            remap = true
        ),
        remap = false
    )
    private void fluidlogistics$redirectRenderItem(GuiGraphics graphics, ItemStack stack, int x, int y,
            @Local(argsOnly = true) BigItemStack itemStack) {
        if (fluidlogistics$isVirtualTank && fluidlogistics$cachedFluid != null) {
            fluidlogistics$pendingFluidRenders.add(new FluidRenderEntry(x, y, fluidlogistics$cachedFluid.copy()));
            return;
        }
        graphics.renderItem(stack, x, y);
    }

    @Redirect(
        method = "renderInputItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(" +
                     "Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            remap = true
        ),
        remap = false
    )
    private void fluidlogistics$redirectRenderItemDecorations(GuiGraphics graphics, net.minecraft.client.gui.Font font, 
            ItemStack stack, int x, int y, String text, @Local(argsOnly = true) BigItemStack itemStack) {
        if (fluidlogistics$isVirtualTank) {
            String amountText = FluidAmountHelper.format(itemStack.count);
            graphics.renderItemDecorations(font, stack, x, y, amountText);
            return;
        }
        graphics.renderItemDecorations(font, stack, x, y, text);
    }

    @Redirect(
        method = "renderInputItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(" +
                     "Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V",
            remap = true
        ),
        remap = false
    )
    private void fluidlogistics$redirectInputTooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, 
            List<Component> tooltips, int mouseX, int mouseY,
            @Local(argsOnly = true) BigItemStack itemStack) {
        if (fluidlogistics$isVirtualTank && fluidlogistics$cachedFluid != null) {
            String fluidName = fluidlogistics$cachedFluid.getHoverName().getString();
            String amountText = FluidAmountHelper.formatPrecise(itemStack.count);
            List<Component> newTooltips = new ArrayList<>();
            
            if (restocker) {
                newTooltips.add(CreateLang.translate("gui.factory_panel.sending_item", fluidName)
                    .color(ScrollInput.HEADER_RGB)
                    .component());
                newTooltips.add(CreateLang.translate("gui.factory_panel.sending_item_tip")
                    .style(ChatFormatting.GRAY)
                    .component());
                newTooltips.add(CreateLang.translate("gui.factory_panel.sending_item_tip_1")
                    .style(ChatFormatting.GRAY)
                    .component());
            } else {
                newTooltips.add(CreateLang.translate("gui.factory_panel.sending_item", 
                        CreateLang.text(fluidName + " x" + amountText).string())
                    .color(ScrollInput.HEADER_RGB)
                    .component());
                newTooltips.add(CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                    .style(ChatFormatting.DARK_GRAY)
                    .style(ChatFormatting.ITALIC)
                    .component());
                newTooltips.add(CreateLang.translate("gui.factory_panel.left_click_disconnect")
                    .style(ChatFormatting.DARK_GRAY)
                    .style(ChatFormatting.ITALIC)
                    .component());
            }
            graphics.renderComponentTooltip(font, newTooltips, mouseX, mouseY);
            return;
        }
        graphics.renderComponentTooltip(font, tooltips, mouseX, mouseY);
    }

    @Unique
    private static final int fluidlogistics$MB_PER_0_1_BUCKET = 100;

    @Redirect(
        method = "mouseScrolled",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(III)I",
            remap = true
        ),
        remap = false
    )
    private int fluidlogistics$redirectClamp(int value, int min, int max,
            @Local BigItemStack itemStack) {
        if (itemStack.stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(itemStack.stack)) {
            FactoryPanelScreen self = (FactoryPanelScreen) (Object) this;
            int currentAmount = itemStack.count;
            
            boolean useBuckets = currentAmount >= fluidlogistics$MB_PER_BUCKET;
            int delta;
            
            if (useBuckets) {
                int bucketDelta;
                if (self.hasControlDown()) {
                    bucketDelta = 100;
                } else if (self.hasShiftDown()) {
                    bucketDelta = 10;
                } else {
                    bucketDelta = 1;
                }
                delta = bucketDelta * fluidlogistics$MB_PER_BUCKET;
            } else {
                if (self.hasControlDown()) {
                    delta = 100;
                } else if (self.hasShiftDown()) {
                    delta = 10;
                } else {
                    delta = 1;
                }
            }
            
            int scrollDirection = (int) Math.signum(value - itemStack.count);
            int newAmount = currentAmount + scrollDirection * delta;
            
            if (scrollDirection > 0) {
                if (currentAmount < fluidlogistics$MB_PER_0_1_BUCKET && newAmount >= fluidlogistics$MB_PER_0_1_BUCKET && newAmount < fluidlogistics$MB_PER_BUCKET) {
                    newAmount = fluidlogistics$MB_PER_0_1_BUCKET;
                } else if (currentAmount < fluidlogistics$MB_PER_BUCKET && newAmount >= fluidlogistics$MB_PER_BUCKET) {
                    newAmount = fluidlogistics$MB_PER_BUCKET;
                }
            } else if (scrollDirection < 0) {
                if (currentAmount >= fluidlogistics$MB_PER_BUCKET && newAmount < fluidlogistics$MB_PER_BUCKET && newAmount >= fluidlogistics$MB_PER_0_1_BUCKET) {
                    newAmount = fluidlogistics$MB_PER_0_1_BUCKET;
                }
            }
            
            return Mth.clamp(newAmount, 1, fluidlogistics$MAX_BUCKETS * fluidlogistics$MB_PER_BUCKET);
        }
        return Mth.clamp(value, min, max);
    }

    @Redirect(
        method = "renderWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(" +
                     "Lnet/minecraft/world/item/ItemStack;II)V",
            ordinal = 0,
            remap = true
        ),
        remap = false
    )
    private void fluidlogistics$redirectOutputRenderItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (outputConfig.stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(outputConfig.stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(outputConfig.stack);
            if (!fluid.isEmpty()) {
                fluidlogistics$pendingFluidRenders.add(new FluidRenderEntry(x, y, fluid.copy()));
                return;
            }
        }
        graphics.renderItem(stack, x, y);
    }

    @Redirect(
        method = "renderWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(" +
                     "Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            ordinal = 0,
            remap = true
        ),
        remap = false
    )
    private void fluidlogistics$redirectOutputRenderItemDecorations(GuiGraphics graphics, 
            net.minecraft.client.gui.Font font, ItemStack stack, int x, int y, String text) {
        if (outputConfig.stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(outputConfig.stack)) {
            String amountText = FluidAmountHelper.format(outputConfig.count);
            graphics.renderItemDecorations(font, stack, x, y, amountText);
            return;
        }
        graphics.renderItemDecorations(font, stack, x, y, text);
    }

    @Redirect(
        method = "renderWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(" +
                     "Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            ordinal = 1,
            remap = true
        ),
        remap = false
    )
    private void fluidlogistics$redirectPromiseItemDecorations(GuiGraphics graphics, 
            net.minecraft.client.gui.Font font, ItemStack stack, int x, int y, String text) {
        ItemStack filter = behaviour.getFilter();
        if (filter.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(filter)) {
            int promised = behaviour.getPromised();
            String amountText = FluidAmountHelper.format(promised);
            graphics.renderItemDecorations(font, stack, x, y, amountText);
            return;
        }
        graphics.renderItemDecorations(font, stack, x, y, text);
    }

    @Redirect(
        method = "renderWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(" +
                     "Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V",
            ordinal = 1,
            remap = true
        ),
        remap = false
    )
    private void fluidlogistics$redirectOutputTooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, 
            List<Component> tooltips, int mouseX, int mouseY) {
        if (outputConfig.stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(outputConfig.stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(outputConfig.stack);
            if (!fluid.isEmpty()) {
                String fluidName = fluid.getHoverName().getString();
                String amountText = FluidAmountHelper.formatPrecise(outputConfig.count);
                
                MutableComponent c1 = CreateLang
                    .translate("gui.factory_panel.expected_output", 
                        CreateLang.text(fluidName + " x" + amountText).string())
                    .color(ScrollInput.HEADER_RGB)
                    .component();
                MutableComponent c2 = CreateLang.translate("gui.factory_panel.expected_output_tip")
                    .style(ChatFormatting.GRAY)
                    .component();
                MutableComponent c3 = CreateLang.translate("gui.factory_panel.expected_output_tip_1")
                    .style(ChatFormatting.GRAY)
                    .component();
                MutableComponent c4 = CreateLang.translate("gui.factory_panel.expected_output_tip_2")
                    .style(ChatFormatting.DARK_GRAY)
                    .style(ChatFormatting.ITALIC)
                    .component();
                
                graphics.renderComponentTooltip(font, List.of(c1, c2, c3, c4), mouseX, mouseY);
                return;
            }
        }
        graphics.renderComponentTooltip(font, tooltips, mouseX, mouseY);
    }

    @Redirect(
        method = "renderWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(" +
                     "Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V",
            ordinal = 3,
            remap = true
        ),
        remap = false
    )
    private void fluidlogistics$redirectPromiseTooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, 
            List<Component> tooltips, int mouseX, int mouseY) {
        ItemStack filter = behaviour.getFilter();
        if (filter.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(filter)) {
            FluidStack fluid = CompressedTankItem.getFluid(filter);
            if (!fluid.isEmpty()) {
                int promised = behaviour.getPromised();
                if (promised > 0) {
                    String fluidName = fluid.getHoverName().getString();
                    String amountText = FluidAmountHelper.formatPrecise(promised);
                    List<Component> newTooltips = List.of(
                        CreateLang.translate("gui.factory_panel.promised_items")
                            .color(ScrollInput.HEADER_RGB)
                            .component(),
                        CreateLang.text(fluidName + " x" + amountText)
                            .component(),
                        CreateLang.translate("gui.factory_panel.left_click_reset")
                            .style(ChatFormatting.DARK_GRAY)
                            .style(ChatFormatting.ITALIC)
                            .component()
                    );
                    graphics.renderComponentTooltip(font, newTooltips, mouseX, mouseY);
                    return;
                }
            }
        }
        graphics.renderComponentTooltip(font, tooltips, mouseX, mouseY);
    }

    @Unique
    private FluidStack fluidlogistics$cachedFluidForGauge;

    @WrapOperation(
        method = "renderWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/createmod/catnip/gui/element/GuiGameElement;of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/catnip/gui/element/GuiGameElement$GuiRenderBuilder;",
            ordinal = 1,
            remap = false
        ),
        remap = false
    )
    private GuiGameElement.GuiRenderBuilder fluidlogistics$redirectFilterItemRender(
            ItemStack stack,
            Operation<GuiGameElement.GuiRenderBuilder> original) {
        if (stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(stack);
            if (!fluid.isEmpty()) {
                fluidlogistics$cachedFluidForGauge = fluid.copy();
                return GuiGameElement.of(net.minecraft.world.level.block.Blocks.AIR.asItem().getDefaultInstance());
            }
        }
        fluidlogistics$cachedFluidForGauge = null;
        return original.call(stack);
    }

    @Inject(
        method = "renderWindow",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 0, shift = At.Shift.BEFORE),
        remap = false
    )
    private void fluidlogistics$renderFluidOnGauge(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (fluidlogistics$cachedFluidForGauge != null && !fluidlogistics$cachedFluidForGauge.isEmpty()) {
            int x = guiLeft;
            int y = guiTop;
            int fluidX = x + 219;
            int fluidY = y + 74;
            
            PoseStack ms = graphics.pose();
            ms.pushPose();
            ms.translate(0, 0, 200);
            FluidSlotRenderer.renderFluidSlot(graphics, fluidX, fluidY, fluidlogistics$cachedFluidForGauge);
            ms.popPose();
        }
    }
    
    @Inject(
        method = "renderWindow",
        at = @At("TAIL"),
        remap = false
    )
    private void fluidlogistics$renderPendingFluids(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        for (FluidRenderEntry entry : fluidlogistics$pendingFluidRenders) {
            FluidSlotRenderer.renderFluidSlot(graphics, entry.x, entry.y, entry.fluid);
        }
    }
}
