package com.yision.fluidlogistics.mixin.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.api.packager.PackageResources;
import com.yision.fluidlogistics.api.packager.PackageResourceDisplay;
import com.yision.fluidlogistics.content.logistics.packageResource.ResourceRestockSettings;
import com.yision.fluidlogistics.api.packager.client.PackageResourceClient;
import com.yision.fluidlogistics.client.ResourceAmountScrollInput;
import com.yision.fluidlogistics.client.FluidLogisticsGuiTextures;
import com.yision.fluidlogistics.network.factoryPanel.FactoryPanelSetResourceRestockSettingPacket;
import com.yision.fluidlogistics.network.factoryPanel.FactoryPanelSetResourceRestockSettingPacket.Setting;
import com.yision.fluidlogistics.util.ResourceGaugeHelper;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.RenderElement;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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

    @Shadow(remap = false)
    private boolean sendRedstoneReset;

    @Shadow(remap = false)
    private void sendIt(@Nullable FactoryPanelPosition toRemove, boolean clearPromises) {}

    @Shadow(remap = false)
    private void playButtonSound() {}

    protected FactoryPanelScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private static final int fluidlogistics$BOTTOM_ROW_X_OFFSET = -20;

    @WrapOperation(
        method = {"renderInputItem", "renderWindow"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem("
                    + "Lnet/minecraft/world/item/ItemStack;II)V",
            remap = true),
        remap = false)
    private void fluidlogistics$renderResourceIcon(
            GuiGraphics graphics, ItemStack stack, int x, int y, Operation<Void> original) {
        original.call(graphics, PackageResources.iconOf(stack).orElse(stack), x, y);
    }

    @WrapOperation(
        method = "renderWindow",
        at = @At(
            value = "INVOKE",
            target = "Lnet/createmod/catnip/gui/element/RenderElement;render("
                    + "Lnet/minecraft/client/gui/GuiGraphics;II)V",
            ordinal = 1,
            remap = false
        ),
        remap = false
    )
    private void fluidlogistics$renderFactoryPanelResourcePreview(
            RenderElement element, GuiGraphics graphics, int x, int y, Operation<Void> original) {
        if (PackageResourceClient.tryRenderFactoryPanelPreview(
                graphics, behaviour.getFilter(), x, y)) {
            return;
        }
        original.call(element, graphics, x, y);
    }

    @WrapOperation(
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
            ItemStack stack, int x, int y, String text, Operation<Void> original,
            @Local(argsOnly = true) BigItemStack itemStack) {
        var amountText = PackageResources.formatAmount(
                itemStack.stack, itemStack.count, PackageResourceDisplay.Format.COMPACT);
        if (amountText.isPresent()) {
            original.call(graphics, font, stack, x, y, amountText.orElseThrow());
            return;
        }
        original.call(graphics, font, stack, x, y, text);
    }

    @WrapOperation(
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
            List<Component> tooltips, int mouseX, int mouseY, Operation<Void> original,
            @Local(argsOnly = true) BigItemStack itemStack) {
        Component resourceName = PackageResources.nameOf(itemStack.stack).orElse(null);
        if (resourceName == null) {
            original.call(graphics, font, tooltips, mouseX, mouseY);
            return;
        }
        String amountText = PackageResources.formatAmount(
                itemStack.stack, itemStack.count, PackageResourceDisplay.Format.PRECISE)
                .orElse(Integer.toString(itemStack.count));
        List<Component> newTooltips = new ArrayList<>();
        String header = restocker
                ? resourceName.getString()
                : resourceName.getString() + " x" + amountText;
        newTooltips.add(CreateLang.translate("gui.factory_panel.sending_item", header)
                .color(ScrollInput.HEADER_RGB)
                .component());
        if (restocker) {
            newTooltips.add(CreateLang.translate("gui.factory_panel.sending_item_tip")
                    .style(ChatFormatting.GRAY).component());
            newTooltips.add(CreateLang.translate("gui.factory_panel.sending_item_tip_1")
                    .style(ChatFormatting.GRAY).component());
        } else {
            newTooltips.add(CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                    .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());
            newTooltips.add(CreateLang.translate("fluidlogistics.scroll_precise_amount")
                    .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());
            newTooltips.add(CreateLang.translate("gui.factory_panel.left_click_disconnect")
                    .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component());
        }
        original.call(graphics, font, newTooltips, mouseX, mouseY);
    }

    @Unique
    private ScrollInput fluidlogistics$restockThresholdInput;

    @Unique
    private ScrollInput fluidlogistics$additionalStockInput;

    @Unique
    private ScrollInput fluidlogistics$promiseLimitInput;

    @Unique
    private int fluidlogistics$additionalStockLabelKind = Integer.MIN_VALUE;

    @Unique
    private int fluidlogistics$promiseLimitLabelKind = Integer.MIN_VALUE;

    @Unique
    private boolean fluidlogistics$calCompatibilityControlsCleared;

    @Unique
    private ResourceRestockSettings fluidlogistics$resourceRestockSettings() {
        return behaviour instanceof ResourceRestockSettings settings ? settings : null;
    }

    @Unique
    private PackageResourceDisplay.FactoryPanelRestockPolicy fluidlogistics$resourceRestockPolicy() {
        return ResourceGaugeHelper.policy(behaviour);
    }

    @Unique
    private boolean fluidlogistics$hasResourceRestockThresholdControl() {
        return restocker && fluidlogistics$resourceRestockSettings() != null
                && fluidlogistics$resourceRestockPolicy().configurableThreshold();
    }

    @Unique
    private boolean fluidlogistics$hasResourcePromiseLimitControl() {
        return fluidlogistics$resourceRestockSettings() != null
                && fluidlogistics$resourceRestockPolicy().configurablePromiseLimit();
    }

    @Unique
    private boolean fluidlogistics$hasResourceAdditionalStockControl() {
        return restocker && fluidlogistics$resourceRestockSettings() != null
                && fluidlogistics$resourceRestockPolicy().configurableAdditionalStock();
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
        fluidlogistics$additionalStockLabelKind = Integer.MIN_VALUE;
        fluidlogistics$promiseLimitLabelKind = Integer.MIN_VALUE;
        fluidlogistics$calCompatibilityControlsCleared = false;
        if (!fluidlogistics$hasResourceRestockThresholdControl()
            && !fluidlogistics$hasResourceAdditionalStockControl()
            && !fluidlogistics$hasResourcePromiseLimitControl()) {
            return;
        }

        int x = guiLeft;
        int y = guiTop;
        if (fluidlogistics$hasResourceRestockThresholdControl()) {
            ResourceRestockSettings settings = fluidlogistics$resourceRestockSettings();

            ResourceAmountScrollInput thresholdInput =
                    new ResourceAmountScrollInput(x + 5, y + windowHeight - 24, 47, 16);
            fluidlogistics$configureResourceAmountInput(thresholdInput, 0, false);
            thresholdInput.withSecondaryHeader(() -> CreateLang.text(fluidlogistics$formatRestockThresholdTooltip(
                fluidlogistics$restockThresholdInput == null ? 0 : fluidlogistics$restockThresholdInput.getState()))
                .component());
            fluidlogistics$restockThresholdInput =
                thresholdInput.setState(settings.fluidlogistics$getRestockThreshold());
            fluidlogistics$updateRestockThresholdLabel();
            addRenderableWidget(fluidlogistics$restockThresholdInput);
        }

        fluidlogistics$initAdditionalStockInput(x, y);
        fluidlogistics$initPromiseLimitInput(x, y);
    }

    @Unique
    private void fluidlogistics$initAdditionalStockInput(int x, int y) {
        if (!fluidlogistics$hasResourceAdditionalStockControl()) {
            return;
        }

        ResourceRestockSettings settings = fluidlogistics$resourceRestockSettings();
        ResourceAmountScrollInput additionalInput = new ResourceAmountScrollInput(
            x + 44 + fluidlogistics$BOTTOM_ROW_X_OFFSET,
            y + windowHeight + 1, 36, 16);
        fluidlogistics$configureResourceAmountInput(additionalInput, 0, false);
        additionalInput.withSecondaryHeader(() -> CreateLang.text(fluidlogistics$formatAdditionalStockTooltip(
            fluidlogistics$additionalStockInput == null ? 0 : fluidlogistics$additionalStockInput.getState()))
            .component());
        fluidlogistics$additionalStockInput =
                additionalInput.setState(settings.fluidlogistics$getAdditionalStock());
        fluidlogistics$updateAdditionalStockLabel();
        addRenderableWidget(fluidlogistics$additionalStockInput);
    }

    @Unique
    private void fluidlogistics$initPromiseLimitInput(int x, int y) {
        if (!fluidlogistics$hasResourcePromiseLimitControl()) {
            return;
        }

        ResourceRestockSettings settings = fluidlogistics$resourceRestockSettings();
        int promiseInputX = restocker ? x + 88 + fluidlogistics$BOTTOM_ROW_X_OFFSET : x + 5;
        int promiseInputY = restocker ? y + windowHeight + 1 : y + windowHeight - 24;
        int promiseInputWidth = restocker ? 56 : 47;
        ResourceAmountScrollInput promiseInput =
            new ResourceAmountScrollInput(promiseInputX, promiseInputY, promiseInputWidth, 16);
        fluidlogistics$configureResourceAmountInput(promiseInput, -1, true);
        promiseInput.withSecondaryHeader(() -> CreateLang.text(fluidlogistics$formatPromiseLimitTooltip(
            fluidlogistics$promiseLimitInput == null ? -1 : fluidlogistics$promiseLimitInput.getState()))
            .component());
        if (!restocker) {
            promiseInput.withRedstoneLinkInfo(() -> !behaviour.targetedByLinks.isEmpty());
        }
        fluidlogistics$promiseLimitInput = promiseInput.setState(settings.fluidlogistics$getPromiseLimit());
        fluidlogistics$updatePromiseLimitLabel();
        addRenderableWidget(fluidlogistics$promiseLimitInput);
    }

    @Unique
    private void fluidlogistics$configureResourceAmountInput(ResourceAmountScrollInput input, int minValue,
        boolean allowUnlimited) {
        int maximum = fluidlogistics$resourceRestockPolicy().maxSettingAmount();
        ItemStack key = behaviour.getFilter();
        input.withRange(minValue, maximum + 1)
            .withShiftStep(1)
            .withStepFunction(context -> {
                if (allowUnlimited && context.currentValue < 0) {
                    return 1;
                }
                int next = PackageResources.adjustAmount(key, new PackageResourceDisplay.Adjustment(
                        context.currentValue,
                        context.forward,
                        context.shift,
                        context.control,
                        minValue,
                        maximum,
                        1,
                        PackageResourceDisplay.Interaction.FACTORY_PANEL))
                        .orElse(context.currentValue);
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

        int labelKind = fluidlogistics$promiseLimitInput.getState() < 0 ? -1 : 0;
        if (fluidlogistics$promiseLimitLabelKind == labelKind) {
            return;
        }
        fluidlogistics$promiseLimitLabelKind = labelKind;
        String key = labelKind < 0
            ? "fluidlogistics.gauge.promise_limit.none"
            : "fluidlogistics.gauge.promise_limit";
        fluidlogistics$promiseLimitInput.titled(CreateLang.translateDirect(key));
    }

    @Unique
    private void fluidlogistics$updateAdditionalStockLabel() {
        if (fluidlogistics$additionalStockInput == null) {
            return;
        }

        int labelKind = fluidlogistics$additionalStockInput.getState() <= 0 ? 0 : 1;
        if (fluidlogistics$additionalStockLabelKind == labelKind) {
            return;
        }
        fluidlogistics$additionalStockLabelKind = labelKind;
        String key = labelKind == 0
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
        if (fluidlogistics$calCompatibilityControlsCleared) {
            return;
        }
        fluidlogistics$calCompatibilityControlsCleared = true;
        fluidlogistics$clearCalCompatibilityControls();
    }

    @WrapOperation(
        method = "renderWindow",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;isEmpty()Z"
        ),
        remap = false
    )
    private boolean fluidlogistics$hideRedstoneLinkWhenResourcePromiseLimitPresent(
            Map map, Operation<Boolean> original) {
        if (!restocker && fluidlogistics$hasResourcePromiseLimitControl()
                && map == behaviour.targetedByLinks) {
            return true;
        }
        return original.call(map);
    }

    @Inject(
        method = "mouseClicked",
        at = @At("HEAD"),
        remap = false,
        cancellable = true
    )
    private void fluidlogistics$handleResourcePromiseLimitClick(double mouseX, double mouseY, int pButton,
            CallbackInfoReturnable<Boolean> cir) {
        if (restocker || !fluidlogistics$hasResourcePromiseLimitControl()
            || fluidlogistics$promiseLimitInput == null || behaviour.targetedByLinks.isEmpty()) {
            return;
        }
        if (pButton != 0) {
            return;
        }
        int inputX = fluidlogistics$promiseLimitInput.getX();
        int inputY = fluidlogistics$promiseLimitInput.getY();
        if (mouseX >= inputX && mouseX < inputX + fluidlogistics$promiseLimitInput.getWidth()
            && mouseY >= inputY && mouseY < inputY + fluidlogistics$promiseLimitInput.getHeight()) {
            sendRedstoneReset = true;
            sendIt(null, false);
            playButtonSound();
            cir.setReturnValue(true);
        }
    }

    @WrapOperation(
        method = "mouseScrolled",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(III)I",
            remap = true
        ),
        remap = false
    )
    private int fluidlogistics$redirectClamp(int value, int min, int max, Operation<Integer> original,
            @Local BigItemStack itemStack) {
        PackageResourceDisplay display = PackageResources.displayOf(itemStack.stack).orElse(null);
        if (display == null) {
            return original.call(value, min, max);
        }
        int maximum = display.factoryPanelRestockPolicy(itemStack.stack).maxRequestPerBatch();
        FactoryPanelScreen self = (FactoryPanelScreen) (Object) this;
        var adjusted = PackageResources.adjustAmount(itemStack.stack, new PackageResourceDisplay.Adjustment(
                itemStack.count,
                value > itemStack.count,
                self.hasShiftDown(),
                self.hasControlDown(),
                1,
                maximum,
                1,
                PackageResourceDisplay.Interaction.FACTORY_PANEL));
        if (adjusted.isPresent()) {
            return adjusted.getAsInt();
        }
        return original.call(value, min, max);
    }

    @WrapOperation(
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
            net.minecraft.client.gui.Font font, ItemStack stack, int x, int y, String text,
            Operation<Void> original) {
        var amountText = PackageResources.formatAmount(
                outputConfig.stack, outputConfig.count, PackageResourceDisplay.Format.COMPACT);
        if (amountText.isPresent()) {
            original.call(graphics, font, stack, x, y, amountText.orElseThrow());
            return;
        }
        original.call(graphics, font, stack, x, y, text);
    }

    @WrapOperation(
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
            net.minecraft.client.gui.Font font, ItemStack stack, int x, int y, String text,
            Operation<Void> original) {
        ItemStack filter = behaviour.getFilter();
        var amountText = PackageResources.formatAmount(
                filter, behaviour.getPromised(), PackageResourceDisplay.Format.COMPACT);
        if (amountText.isPresent()) {
            original.call(graphics, font, stack, x, y, amountText.orElseThrow());
            return;
        }
        original.call(graphics, font, stack, x, y, text);
    }

    @WrapOperation(
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
            List<Component> tooltips, int mouseX, int mouseY, Operation<Void> original) {
        Component resourceName = PackageResources.nameOf(outputConfig.stack).orElse(null);
        if (resourceName == null) {
            original.call(graphics, font, tooltips, mouseX, mouseY);
            return;
        }
        String amountText = PackageResources.formatAmount(
                outputConfig.stack, outputConfig.count, PackageResourceDisplay.Format.PRECISE)
                .orElse(Integer.toString(outputConfig.count));
        List<Component> resourceTooltips = new ArrayList<>(List.of(
                CreateLang.translate("gui.factory_panel.expected_output",
                        resourceName.getString() + " x" + amountText)
                        .color(ScrollInput.HEADER_RGB).component(),
                CreateLang.translate("gui.factory_panel.expected_output_tip")
                        .style(ChatFormatting.GRAY).component(),
                CreateLang.translate("gui.factory_panel.expected_output_tip_1")
                        .style(ChatFormatting.GRAY).component(),
                CreateLang.translate("gui.factory_panel.expected_output_tip_2")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component(),
                CreateLang.translate("fluidlogistics.scroll_precise_amount")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component()));
        original.call(graphics, font, resourceTooltips, mouseX, mouseY);
    }

    @WrapOperation(
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
            List<Component> tooltips, int mouseX, int mouseY, Operation<Void> original) {
        ItemStack filter = behaviour.getFilter();
        Component resourceName = PackageResources.nameOf(filter).orElse(null);
        int promised = behaviour.getPromised();
        if (resourceName == null || promised <= 0) {
            original.call(graphics, font, tooltips, mouseX, mouseY);
            return;
        }
        String amountText = PackageResources.formatAmount(
                filter, promised, PackageResourceDisplay.Format.PRECISE)
                .orElse(Integer.toString(promised));
        List<Component> resourceTooltips = new ArrayList<>(List.of(
                CreateLang.translate("gui.factory_panel.promised_items")
                        .color(ScrollInput.HEADER_RGB).component(),
                Component.literal(resourceName.getString() + " x" + amountText),
                CreateLang.translate("gui.factory_panel.left_click_reset")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component()));
        original.call(graphics, font, resourceTooltips, mouseX, mouseY);
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
            String label = " " + fluidlogistics$formatResourceAmount(
                    fluidlogistics$restockThresholdInput.getState(), true, false);
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
            String label = " " + fluidlogistics$formatResourceAmount(
                    fluidlogistics$promiseLimitInput.getState(), false, false);
            int promiseLabelX = fluidlogistics$promiseLimitInput.getX() + (restocker ? 3 : 15);
            graphics.drawString(font, CreateLang.text(label).component(), promiseLabelX,
                fluidlogistics$promiseLimitInput.getY() + 4, 0xffeeeeee, true);
        }

        if (fluidlogistics$additionalStockInput != null) {
            FluidLogisticsGuiTextures.ADDITIONAL_STOCK_LEFT_BG.render(graphics, fluidlogistics$additionalStockInput.getX() - 1,
                fluidlogistics$additionalStockInput.getY() - 4);
            String label = " " + fluidlogistics$formatResourceAmount(
                    fluidlogistics$additionalStockInput.getState(), true, false);
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
        ResourceRestockSettings settings = fluidlogistics$resourceRestockSettings();
        if (settings == null
                || fluidlogistics$restockThresholdInput == null
                && fluidlogistics$promiseLimitInput == null
                && fluidlogistics$additionalStockInput == null) {
            return;
        }

        if (fluidlogistics$additionalStockInput != null
                && fluidlogistics$additionalStockInput.getState() != settings.fluidlogistics$getAdditionalStock()) {
            fluidlogistics$sendRestockSetting(
                    Setting.ADDITIONAL_STOCK, fluidlogistics$additionalStockInput.getState());
        }

        if (fluidlogistics$restockThresholdInput != null
                && fluidlogistics$restockThresholdInput.getState() != settings.fluidlogistics$getRestockThreshold()) {
            fluidlogistics$sendRestockSetting(
                    Setting.RESTOCK_THRESHOLD, fluidlogistics$restockThresholdInput.getState());
        }

        if (fluidlogistics$promiseLimitInput != null
                && fluidlogistics$promiseLimitInput.getState() != settings.fluidlogistics$getPromiseLimit()) {
            fluidlogistics$sendRestockSetting(
                    Setting.PROMISE_LIMIT, fluidlogistics$promiseLimitInput.getState());
        }
    }

    @Unique
    private void fluidlogistics$sendRestockSetting(Setting setting, int value) {
        CatnipServices.NETWORK.sendToServer(new FactoryPanelSetResourceRestockSettingPacket(
                behaviour.getPanelPosition(), setting, value));
    }

    @Unique
    private String fluidlogistics$formatAdditionalStockTooltip(int amount) {
        return fluidlogistics$formatResourceAmount(amount, true, true);
    }

    @Unique
    private void fluidlogistics$clearCalCompatibilityControls() {
        boolean hasAdditionalStockControl = fluidlogistics$hasResourceAdditionalStockControl();
        boolean hasPromiseLimitControl = fluidlogistics$hasResourcePromiseLimitControl();
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
    private String fluidlogistics$formatRestockThresholdTooltip(int threshold) {
        return fluidlogistics$formatResourceAmount(threshold, true, true);
    }

    @Unique
    private String fluidlogistics$formatPromiseLimitTooltip(int threshold) {
        return fluidlogistics$formatResourceAmount(threshold, false, true);
    }

    @Unique
    private String fluidlogistics$formatResourceAmount(
            int amount, boolean zeroIsInactive, boolean multiplier) {
        if (amount < 0 || zeroIsInactive && amount == 0) {
            return "---";
        }
        String formatted = PackageResources.formatAmount(
                behaviour.getFilter(), amount, PackageResourceDisplay.Format.PRECISE)
                .orElse(Integer.toString(amount));
        return multiplier ? "x" + formatted : formatted;
    }

    @Unique
    private void fluidlogistics$clearOptionalScrollInputField(String fieldName) {
        try {
            java.lang.reflect.Field field = ((Object) this).getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(this);
            if (value instanceof ScrollInput scrollInput) {
                removeWidget(scrollInput);
                field.set(this, null);
            }
        } catch (ReflectiveOperationException e) {
        }
    }
}
