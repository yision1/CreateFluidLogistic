package com.yision.fluidlogistics.mixin.client;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.client.FluidAmountScrollInput;
import com.yision.fluidlogistics.client.FluidLogisticsGuiTextures;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.network.FactoryPanelSetFluidAdditionalStockPacket;
import com.yision.fluidlogistics.network.FactoryPanelSetFluidPromiseLimitPacket;
import com.yision.fluidlogistics.network.FactoryPanelSetFluidRestockThresholdPacket;
import com.yision.fluidlogistics.util.IFluidAdditionalStock;
import com.yision.fluidlogistics.util.FluidAmountHelper;
import com.yision.fluidlogistics.util.FluidGaugeHelper;
import com.yision.fluidlogistics.util.IFluidPromiseLimit;
import com.yision.fluidlogistics.util.IFluidRestockThreshold;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.platform.CatnipServices;
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
    private static final int fluidlogistics$BOTTOM_ROW_X_OFFSET = -20;

    @Unique
    private boolean fluidlogistics$isVirtualTank = false;

    @Unique
    private FluidStack fluidlogistics$cachedFluid = null;

    @Inject(
        method = "renderInputItem",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$onRenderInputItemHead(GuiGraphics graphics, int slot, BigItemStack itemStack, 
            int mouseX, int mouseY, CallbackInfo ci) {
        fluidlogistics$isVirtualTank = false;
        fluidlogistics$cachedFluid = null;

        if (FluidGaugeHelper.isVirtualFluidFilter(itemStack.stack)) {
            FluidStack fluid = CompressedTankItem.getFluid(itemStack.stack);
            if (!fluid.isEmpty()) {
                fluidlogistics$isVirtualTank = true;
                fluidlogistics$cachedFluid = fluid;
            }
        }
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
                newTooltips.add(CreateLang.translate("fluidlogistics.scroll_precise_amount")
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
    private ScrollInput fluidlogistics$restockThresholdInput;

    @Unique
    private ScrollInput fluidlogistics$additionalStockInput;

    @Unique
    private ScrollInput fluidlogistics$promiseLimitInput;

    @Unique
    private boolean fluidlogistics$hasVirtualFluidFilter() {
        return FluidGaugeHelper.isVirtualFluidFilter(behaviour.getFilter());
    }

    @Unique
    private boolean fluidlogistics$hasFluidRestockThresholdControl() {
        return restocker
            && behaviour instanceof IFluidRestockThreshold
            && fluidlogistics$hasVirtualFluidFilter();
    }

    @Unique
    private boolean fluidlogistics$hasFluidPromiseLimitControl() {
        return behaviour instanceof IFluidPromiseLimit
            && fluidlogistics$hasVirtualFluidFilter();
    }

    @Unique
    private boolean fluidlogistics$hasFluidAdditionalStockControl() {
        return restocker
            && behaviour instanceof IFluidAdditionalStock
            && fluidlogistics$hasVirtualFluidFilter();
    }

    @Inject(
        method = "init",
        at = @At("RETURN"),
        remap = false
    )
    private void fluidlogistics$initRestockThresholdInput(CallbackInfo ci) {
        fluidlogistics$restockThresholdInput = null;
        fluidlogistics$additionalStockInput = null;
        fluidlogistics$promiseLimitInput = null;
        if (!fluidlogistics$hasFluidRestockThresholdControl()
            && !fluidlogistics$hasFluidAdditionalStockControl()
            && !fluidlogistics$hasFluidPromiseLimitControl()) {
            return;
        }

        int x = guiLeft;
        int y = guiTop;
        if (fluidlogistics$hasFluidRestockThresholdControl()) {
            IFluidRestockThreshold thresholdData = (IFluidRestockThreshold) behaviour;

            FluidAmountScrollInput thresholdInput = new FluidAmountScrollInput(x + 5, y + windowHeight - 24, 47, 16);
            fluidlogistics$configureFluidAmountInput(thresholdInput, 0, false);
            thresholdInput.withSecondaryHeader(() -> CreateLang.text(fluidlogistics$formatRestockThresholdTooltip(
                fluidlogistics$restockThresholdInput == null ? 0 : fluidlogistics$restockThresholdInput.getState()))
                .component());
            fluidlogistics$restockThresholdInput =
                thresholdInput.setState(thresholdData.fluidlogistics$getRestockThreshold());
            fluidlogistics$updateRestockThresholdLabel();
            addRenderableWidget(fluidlogistics$restockThresholdInput);
        }

        fluidlogistics$initAdditionalStockInput(x, y);
        fluidlogistics$initPromiseLimitInput(x, y);
        fluidlogistics$clearCalCompatibilityControls();
    }

    @Unique
    private void fluidlogistics$initAdditionalStockInput(int x, int y) {
        if (!fluidlogistics$hasFluidAdditionalStockControl()) {
            return;
        }

        IFluidAdditionalStock additionalStockData = (IFluidAdditionalStock) behaviour;
        FluidAmountScrollInput additionalInput = new FluidAmountScrollInput(x + 44 + fluidlogistics$BOTTOM_ROW_X_OFFSET,
            y + windowHeight + 1, 36, 16);
        fluidlogistics$configureFluidAmountInput(additionalInput, 0, false);
        additionalInput.withSecondaryHeader(() -> CreateLang.text(fluidlogistics$formatAdditionalStockTooltip(
            fluidlogistics$additionalStockInput == null ? 0 : fluidlogistics$additionalStockInput.getState()))
            .component());
        fluidlogistics$additionalStockInput = additionalInput.setState(additionalStockData.fluidlogistics$getAdditionalStock());
        fluidlogistics$updateAdditionalStockLabel();
        addRenderableWidget(fluidlogistics$additionalStockInput);
    }

    @Unique
    private void fluidlogistics$initPromiseLimitInput(int x, int y) {
        if (!fluidlogistics$hasFluidPromiseLimitControl()) {
            return;
        }

        IFluidPromiseLimit promiseLimitData = (IFluidPromiseLimit) behaviour;
        int promiseInputX = restocker ? x + 88 + fluidlogistics$BOTTOM_ROW_X_OFFSET : x + 5;
        int promiseInputY = restocker ? y + windowHeight + 1 : y + windowHeight - 24;
        int promiseInputWidth = restocker ? 56 : 47;
        FluidAmountScrollInput promiseInput =
            new FluidAmountScrollInput(promiseInputX, promiseInputY, promiseInputWidth, 16);
        fluidlogistics$configureFluidAmountInput(promiseInput, -1, true);
        promiseInput.withSecondaryHeader(() -> CreateLang.text(fluidlogistics$formatPromiseLimitTooltip(
            fluidlogistics$promiseLimitInput == null ? -1 : fluidlogistics$promiseLimitInput.getState()))
            .component());
        fluidlogistics$promiseLimitInput = promiseInput.setState(promiseLimitData.fluidlogistics$getPromiseLimit());
        fluidlogistics$updatePromiseLimitLabel();
        addRenderableWidget(fluidlogistics$promiseLimitInput);
    }

    @Unique
    private void fluidlogistics$configureFluidAmountInput(FluidAmountScrollInput input, int minValue,
        boolean allowUnlimited) {
        input.withRange(minValue, FluidGaugeHelper.MAX_FLUID_AMOUNT + 1)
            .withShiftStep(1)
            .withStepFunction(context -> {
                if (allowUnlimited && context.currentValue < 0) {
                    return 1;
                }

                int next = FluidAmountHelper.adjustFluidRequestAmount(context.currentValue, context.forward,
                    context.shift, context.control, 0, FluidGaugeHelper.MAX_FLUID_AMOUNT);
                return Math.max(1, Math.abs(next - context.currentValue));
            });
    }

    @Unique
    private void fluidlogistics$updateRestockThresholdLabel() {
        if (fluidlogistics$restockThresholdInput == null) {
            return;
        }

        fluidlogistics$restockThresholdInput.titled(
            CreateLang.translateDirect("fluidlogistics.gauge.restock_threshold")
        );
    }

    @Unique
    private void fluidlogistics$updatePromiseLimitLabel() {
        if (fluidlogistics$promiseLimitInput == null) {
            return;
        }

        String key = fluidlogistics$promiseLimitInput.getState() < 0
            ? "fluidlogistics.gauge.promise_limit.none"
            : "fluidlogistics.gauge.promise_limit";
        fluidlogistics$promiseLimitInput.titled(CreateLang.translateDirect(key));
    }

    @Unique
    private void fluidlogistics$updateAdditionalStockLabel() {
        if (fluidlogistics$additionalStockInput == null) {
            return;
        }

        String key = fluidlogistics$additionalStockInput.getState() <= 0
            ? "fluidlogistics.gauge.request_additional.none"
            : "fluidlogistics.gauge.request_additional";
        fluidlogistics$additionalStockInput.titled(CreateLang.translateDirect(key));
    }

    @Inject(
        method = "tick",
        at = @At("RETURN"),
        remap = false
    )
    private void fluidlogistics$tickRestockThresholdInput(CallbackInfo ci) {
        fluidlogistics$updateRestockThresholdLabel();
        fluidlogistics$updateAdditionalStockLabel();
        fluidlogistics$updatePromiseLimitLabel();
    }

    @Inject(
        method = "renderWindow",
        at = @At("HEAD"),
        remap = false
    )
    private void fluidlogistics$beforeRenderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        fluidlogistics$clearCalCompatibilityControls();
    }

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
        if (FluidGaugeHelper.isVirtualFluidFilter(itemStack.stack)) {
            FactoryPanelScreen self = (FactoryPanelScreen) (Object) this;
            return FluidAmountHelper.adjustFluidRequestAmount(itemStack.count, value > itemStack.count, self.hasShiftDown(),
                self.hasControlDown(), 1, FluidGaugeHelper.MAX_FLUID_AMOUNT);
        }
        return Mth.clamp(value, min, max);
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
                MutableComponent c5 = CreateLang.translate("fluidlogistics.scroll_precise_amount")
                        .style(ChatFormatting.DARK_GRAY)
                        .style(ChatFormatting.ITALIC)
                        .component();
                
                graphics.renderComponentTooltip(font, List.of(c1, c2, c3, c4, c5), mouseX, mouseY);
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

    @Inject(
        method = "renderWindow",
        at = @At("RETURN"),
        remap = false
    )
    private void fluidlogistics$renderRestockThresholdControl(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTicks, CallbackInfo ci) {
        if (fluidlogistics$restockThresholdInput == null) {
            if (fluidlogistics$additionalStockInput == null && fluidlogistics$promiseLimitInput == null) {
                return;
            }
        }

        if (fluidlogistics$restockThresholdInput != null) {
            FluidLogisticsGuiTextures.ADDITIONAL_STOCK_BG.render(graphics, fluidlogistics$restockThresholdInput.getX() + 11,
                fluidlogistics$restockThresholdInput.getY() - 1);
            String label = " " + FluidAmountHelper.formatOptionalCompact(fluidlogistics$restockThresholdInput.getState(), true);
            graphics.drawString(font, CreateLang.text(label).component(), fluidlogistics$restockThresholdInput.getX() + 15,
                fluidlogistics$restockThresholdInput.getY() + 4, 0xffeeeeee, true);
        }

        if (fluidlogistics$promiseLimitInput != null) {
            if (restocker) {
                FluidLogisticsGuiTextures.PROMISE_LIMIT_BG.render(graphics, fluidlogistics$promiseLimitInput.getX() - 8,
                    fluidlogistics$promiseLimitInput.getY() - 4);
            } else {
                FluidLogisticsGuiTextures.ADDITIONAL_STOCK_BG.render(graphics, fluidlogistics$promiseLimitInput.getX() + 11,
                    fluidlogistics$promiseLimitInput.getY() - 1);
            }
            String label = " " + FluidAmountHelper.formatOptionalCompact(fluidlogistics$promiseLimitInput.getState(), false);
            int promiseLabelX = fluidlogistics$promiseLimitInput.getX() + (restocker ? 3 : 15);
            graphics.drawString(font, CreateLang.text(label).component(), promiseLabelX,
                fluidlogistics$promiseLimitInput.getY() + 4, 0xffeeeeee, true);
        }

        if (fluidlogistics$additionalStockInput != null) {
            FluidLogisticsGuiTextures.ADDITIONAL_STOCK_LEFT_BG.render(graphics, fluidlogistics$additionalStockInput.getX() - 1,
                fluidlogistics$additionalStockInput.getY() - 4);
            String label = " " + FluidAmountHelper.formatOptionalCompact(fluidlogistics$additionalStockInput.getState(), true);
            graphics.drawString(font, CreateLang.text(label).component(), fluidlogistics$additionalStockInput.getX() + 8,
                fluidlogistics$additionalStockInput.getY() + 4, 0xffeeeeee, true);
        }
    }

    @Inject(
        method = "sendIt",
        at = @At("RETURN"),
        remap = false
    )
    private void fluidlogistics$sendRestockThreshold(CallbackInfo ci) {
        if (!(behaviour instanceof IFluidRestockThreshold) || fluidlogistics$restockThresholdInput == null) {
            if (!(behaviour instanceof IFluidPromiseLimit) || fluidlogistics$promiseLimitInput == null) {
                if (!(behaviour instanceof IFluidAdditionalStock) || fluidlogistics$additionalStockInput == null) {
                    return;
                }
            }
        }

        if (fluidlogistics$additionalStockInput != null) {
            CatnipServices.NETWORK.sendToServer(new FactoryPanelSetFluidAdditionalStockPacket(
                behaviour.getPanelPosition(),
                fluidlogistics$additionalStockInput.getState()
            ));
        }

        if (fluidlogistics$restockThresholdInput != null) {
            CatnipServices.NETWORK.sendToServer(new FactoryPanelSetFluidRestockThresholdPacket(
                behaviour.getPanelPosition(),
                fluidlogistics$restockThresholdInput.getState()
            ));
        }

        if (fluidlogistics$promiseLimitInput != null) {
            CatnipServices.NETWORK.sendToServer(new FactoryPanelSetFluidPromiseLimitPacket(
                behaviour.getPanelPosition(),
                fluidlogistics$promiseLimitInput.getState()
            ));
        }
    }

    @Unique
    private static String fluidlogistics$formatAdditionalStockTooltip(int amount) {
        return FluidAmountHelper.formatOptionalPreciseMultiplier(amount, true);
    }

    @Unique
    private void fluidlogistics$clearCalCompatibilityControls() {
        boolean hasAdditionalStockControl = fluidlogistics$hasFluidAdditionalStockControl();
        boolean hasPromiseLimitControl = fluidlogistics$hasFluidPromiseLimitControl();
        if (!hasAdditionalStockControl && !hasPromiseLimitControl) {
            return;
        }

        if (hasAdditionalStockControl) {
            fluidlogistics$clearOptionalScrollInputField("CAL$requestAdditional");
        }
        if (hasPromiseLimitControl) {
            fluidlogistics$clearOptionalScrollInputField("CAL$promiseLimit");
        }
    }

    @Unique
    private static String fluidlogistics$formatRestockThresholdTooltip(int threshold) {
        return FluidAmountHelper.formatOptionalPreciseMultiplier(threshold, true);
    }

    @Unique
    private static String fluidlogistics$formatPromiseLimitTooltip(int threshold) {
        return FluidAmountHelper.formatOptionalPreciseMultiplier(threshold, false);
    }

    @Unique
    private void fluidlogistics$clearOptionalScrollInputField(String fieldName) {
        ScrollInput widget = fluidlogistics$getOptionalScrollInputField(fieldName);
        if (widget == null) {
            return;
        }

        removeWidget(widget);
        fluidlogistics$setOptionalScrollInputField(fieldName, null);
    }

    @Unique
    private ScrollInput fluidlogistics$getOptionalScrollInputField(String fieldName) {
        try {
            java.lang.reflect.Field field = ((Object) this).getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(this);
            return value instanceof ScrollInput scrollInput ? scrollInput : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Unique
    private void fluidlogistics$setOptionalScrollInputField(String fieldName, ScrollInput value) {
        try {
            java.lang.reflect.Field field = ((Object) this).getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(this, value);
        } catch (ReflectiveOperationException e) {
        }
    }
}
