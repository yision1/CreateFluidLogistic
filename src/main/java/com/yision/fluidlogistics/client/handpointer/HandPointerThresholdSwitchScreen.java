package com.yision.fluidlogistics.client.handpointer;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.thresholdSwitch.ConfigureThresholdSwitchPacket;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity.ThresholdType;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;

public class HandPointerThresholdSwitchScreen extends AbstractSimiScreen {
	private static final int VALUE_TEXT_COLOR = 0xFFFFFFFF;
	private static final int INPUT_X = 64;
	private static final int INPUT_Y_TOP = 28;
	private static final int INPUT_Y_BOTTOM = 52;
	private static final int ITEM_INPUT_WIDTH = 38;
	private static final int WIDE_INPUT_WIDTH = 83;
	private static final int INPUT_HEIGHT = 10;

	private ScrollInput offBelow;
	private ScrollInput onAbove;
	private SelectionScrollInput inStacks;

	private IconButton confirmButton;
	private IconButton flipSignals;
	private EditBox offBelowBox;
	private EditBox onAboveBox;
	private boolean wasOffBelowBoxFocused;
	private boolean wasOnAboveBoxFocused;

	private final Component invertSignal = CreateLang.translateDirect("gui.threshold_switch.invert_signal");
	private final ItemStack renderedItem = new ItemStack(AllBlocks.THRESHOLD_SWITCH.get());
	private final AllGuiTextures background = AllGuiTextures.THRESHOLD_SWITCH;
	private final ThresholdSwitchBlockEntity blockEntity;
	private int lastModification = -1;

	public HandPointerThresholdSwitchScreen(ThresholdSwitchBlockEntity be) {
		super(CreateLang.translateDirect("gui.threshold_switch.title"));
		this.blockEntity = be;
	}

	@Override
	protected void init() {
		setWindowSize(background.getWidth(), background.getHeight());
		setWindowOffset(-20, 0);
		super.init();

		int x = guiLeft;
		int y = guiTop;

		inStacks = (SelectionScrollInput) new SelectionScrollInput(x + 100, y + 23, 52, 42)
			.forOptions(List.of(CreateLang.translateDirect("schedule.condition.threshold.items"),
				CreateLang.translateDirect("schedule.condition.threshold.stacks")))
			.titled(CreateLang.translateDirect("schedule.condition.threshold.item_measure"))
			.calling($ -> {
				lastModification = 0;
				syncTextBoxesFromScrollInputs(true);
			})
			.setState(blockEntity.inStacks ? 1 : 0);

		offBelow = new ScrollInput(x + 48, y + 47, 1, 18)
			.withRange(blockEntity.getMinLevel(), blockEntity.getMaxLevel() + 1 - getValueStep())
			.titled(CreateLang.translateDirect("gui.threshold_switch.lower_threshold"))
			.calling(state -> {
				lastModification = 0;
				int valueStep = getValueStep();

				if (onAbove.getState() / valueStep == 0 && state / valueStep == 0)
					return;

				if (onAbove.getState() / valueStep <= state / valueStep) {
					onAbove.setState((state + valueStep) / valueStep * valueStep);
					onAbove.onChanged();
				}
				syncTextBoxesFromScrollInputs(false);
			})
			.withStepFunction(sc -> sc.shift ? 10 * getValueStep() : getValueStep())
			.setState(blockEntity.offWhenBelow);

		onAbove = new ScrollInput(x + 48, y + 23, 1, 18)
			.withRange(blockEntity.getMinLevel() + getValueStep(), blockEntity.getMaxLevel() + 1)
			.titled(CreateLang.translateDirect("gui.threshold_switch.upper_threshold"))
			.calling(state -> {
				lastModification = 0;
				int valueStep = getValueStep();

				if (offBelow.getState() / valueStep == 0 && state / valueStep == 0)
					return;

				if (offBelow.getState() / valueStep >= state / valueStep) {
					offBelow.setState((state - valueStep) / valueStep * valueStep);
					offBelow.onChanged();
				}
				syncTextBoxesFromScrollInputs(false);
			})
			.withStepFunction(sc -> sc.shift ? 10 * getValueStep() : getValueStep())
			.setState(blockEntity.onWhenAbove);

		onAbove.onChanged();
		offBelow.onChanged();

		addRenderableWidget(inStacks);

		onAboveBox = createThresholdBox(x + INPUT_X, y + INPUT_Y_TOP, true);
		offBelowBox = createThresholdBox(x + INPUT_X, y + INPUT_Y_BOTTOM, false);
		addRenderableWidget(onAboveBox);
		addRenderableWidget(offBelowBox);

		confirmButton =
			new IconButton(x + background.getWidth() - 33, y + background.getHeight() - 24, AllIcons.I_CONFIRM);
		confirmButton.withCallback(this::onClose);
		addRenderableWidget(confirmButton);

		flipSignals = new IconButton(x + background.getWidth() - 62, y + background.getHeight() - 24, AllIcons.I_FLIP);
		flipSignals.withCallback(() -> send(!blockEntity.isInverted()));
		flipSignals.setToolTip(invertSignal);
		addRenderableWidget(flipSignals);

		updateInputBoxes();
		syncTextBoxesFromScrollInputs(true);
	}

	private EditBox createThresholdBox(int x, int y, boolean upper) {
		EditBox box = new EditBox(font, x, y, ITEM_INPUT_WIDTH, INPUT_HEIGHT,
			CreateLang.translateDirect(upper ? "gui.threshold_switch.upper_threshold" : "gui.threshold_switch.lower_threshold"));
		box.setBordered(false);
		box.setTextShadow(true);
		box.setTextColor(VALUE_TEXT_COLOR);
		box.setTextColorUneditable(VALUE_TEXT_COLOR);
		box.setMaxLength(10);
		box.setFilter(this::isIntegerText);
		box.setResponder($ -> lastModification = 0);
		return box;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			send(blockEntity.isInverted());
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int itemX = guiLeft + 13;
		int itemY = guiTop + 80;
		if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
			ScreenOpener.open(new PonderTagScreen(AllCreatePonderTags.THRESHOLD_SWITCH_TARGETS));
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		int x = guiLeft;
		int y = guiTop;

		background.render(graphics, x, y);
		graphics.drawString(font, title, x + background.getWidth() / 2 - font.width(title) / 2, y + 4, 0x592424, false);

		ThresholdType typeOfCurrentTarget = blockEntity.getTypeOfCurrentTarget();
		boolean forItems = typeOfCurrentTarget == ThresholdType.ITEM;
		boolean hasSeparateSuffix = forItems || typeOfCurrentTarget == ThresholdType.FLUID;
		AllGuiTextures inputBg =
			forItems ? AllGuiTextures.THRESHOLD_SWITCH_ITEMCOUNT_INPUTS : AllGuiTextures.THRESHOLD_SWITCH_MISC_INPUTS;

		inputBg.render(graphics, x + 44, y + 21);
		inputBg.render(graphics, x + 44, y + 21 + 24);

		int valueStep = 1;
		boolean stacks = inStacks.getState() == 1;
		if (typeOfCurrentTarget == ThresholdType.FLUID)
			valueStep = 1000;

		if (forItems) {
			Component suffix =
				inStacks.getState() == 0 ? CreateLang.translateDirect("schedule.condition.threshold.items")
					: CreateLang.translateDirect("schedule.condition.threshold.stacks");
			valueStep = inStacks.getState() == 0 ? 1 : 64;
			graphics.drawString(font, suffix, x + 105, y + 28, 0xFFFFFFFF, true);
			graphics.drawString(font, suffix, x + 105, y + 28 + 24, 0xFFFFFFFF, true);
		} else if (typeOfCurrentTarget == ThresholdType.FLUID) {
			Component suffix = CreateLang.translateDirect("schedule.condition.threshold.buckets");
			graphics.drawString(font, suffix, x + 105, y + 28, 0xFFFFFFFF, true);
			graphics.drawString(font, suffix, x + 105, y + 28 + 24, 0xFFFFFFFF, true);
		}

		graphics.drawString(font, Component.literal("\u2265"), x + 53, y + 28, 0xFFFFFFFF, true);
		graphics.drawString(font, Component.literal("\u2264"), x + 53, y + 28 + 24, 0xFFFFFFFF, true);

		GuiGameElement.of(renderedItem)
			.<GuiGameElement.GuiRenderBuilder>at(x + background.getWidth() + 6, y + background.getHeight() - 56, -200)
			.scale(5)
			.render(graphics);

		int itemX = x + 13;
		int itemY = y + 80;

		ItemStack displayItem = blockEntity.getDisplayItemForScreen();
		GuiGameElement.of(displayItem.isEmpty() ? new ItemStack(Items.BARRIER) : displayItem)
			.<GuiGameElement.GuiRenderBuilder>at(itemX, itemY, 0)
			.render(graphics);

		int torchX = x + 23;
		int torchY = y + 24;

		boolean highlightTopRow = blockEntity.isInverted() ^ blockEntity.isPowered();
		AllGuiTextures.THRESHOLD_SWITCH_CURRENT_STATE.render(graphics, torchX - 3,
			torchY - 4 + (highlightTopRow ? 0 : 24));

		PoseStack ms = graphics.pose();
		ms.pushPose();
		ms.translate(torchX - 5, torchY + 14, 200);
		TransformStack.of(ms)
			.rotateXDegrees(-22.5f)
			.rotateYDegrees(45);

		for (boolean power : Iterate.trueAndFalse) {
			GuiGameElement.of(Blocks.REDSTONE_TORCH.defaultBlockState()
					.setValue(RedstoneTorchBlock.LIT, blockEntity.isInverted() ^ power))
				.scale(20)
				.render(graphics);
			ms.translate(0, 26, 0);
		}

		ms.popPose();

		if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
			ArrayList<Component> list = new ArrayList<>();
			if (displayItem.isEmpty()) {
				list.add(CreateLang.translateDirect("gui.threshold_switch.not_attached"));
				list.add(CreateLang.translateDirect("display_link.view_compatible")
					.withStyle(ChatFormatting.DARK_GRAY));
				graphics.renderComponentTooltip(font, list, mouseX, mouseY);
				return;
			}

			list.add(displayItem.getHoverName());
			if (typeOfCurrentTarget == ThresholdType.UNSUPPORTED) {
				list.add(CreateLang.translateDirect("gui.threshold_switch.incompatible")
					.withStyle(ChatFormatting.GRAY));
				list.add(CreateLang.translateDirect("display_link.view_compatible")
					.withStyle(ChatFormatting.DARK_GRAY));
				graphics.renderComponentTooltip(font, list, mouseX, mouseY);
				return;
			}

			CreateLang
				.translate("gui.threshold_switch.currently",
					blockEntity.format(blockEntity.currentLevel / valueStep, stacks))
				.style(ChatFormatting.DARK_AQUA)
				.addTo(list);

			if (blockEntity.currentMinLevel / valueStep == 0)
				CreateLang
					.translate("gui.threshold_switch.range_max",
						blockEntity.format(blockEntity.currentMaxLevel / valueStep, stacks))
					.style(ChatFormatting.GRAY)
					.addTo(list);
			else
				CreateLang
					.translate("gui.threshold_switch.range", blockEntity.currentMinLevel / valueStep,
						blockEntity.format(blockEntity.currentMaxLevel / valueStep, stacks))
					.style(ChatFormatting.GRAY)
					.addTo(list);

			list.add(CreateLang.translateDirect("display_link.view_compatible")
				.withStyle(ChatFormatting.DARK_GRAY));

			graphics.renderComponentTooltip(font, list, mouseX, mouseY);
			return;
		}

		for (boolean power : Iterate.trueAndFalse) {
			int thisTorchY = power ? torchY : torchY + 26;
			if (mouseX >= torchX && mouseX < torchX + 16 && mouseY >= thisTorchY && mouseY < thisTorchY + 16) {
				graphics.renderComponentTooltip(font,
					List.of(CreateLang
						.translate(power ^ blockEntity.isInverted() ? "gui.threshold_switch.power_on_when"
							: "gui.threshold_switch.power_off_when")
						.color(AbstractSimiWidget.HEADER_RGB)
						.component()),
					mouseX, mouseY);
				return;
			}
		}
	}

	@Override
	public void tick() {
		super.tick();

		handleTextBoxFocusChanges();

		if (lastModification >= 0)
			lastModification++;

		if (lastModification >= 20) {
			if (hasFocusedEmptyTextBox()) {
				lastModification = 0;
				return;
			}
			lastModification = -1;
			send(blockEntity.isInverted());
		}

		if (inStacks == null)
			return;

		updateInputBoxes();
		syncTextBoxesFromScrollInputs(false);
	}

	private void handleTextBoxFocusChanges() {
		if (onAboveBox == null || offBelowBox == null)
			return;

		int valueStep = getValueStep();
		boolean applyChangedValue = false;
		if (wasOnAboveBoxFocused && !onAboveBox.isFocused()) {
			if (onAboveBox.getValue().isEmpty())
				syncTextBox(onAboveBox, onAbove.getState() / valueStep, true);
			else
				applyChangedValue = true;
		}
		if (wasOffBelowBoxFocused && !offBelowBox.isFocused()) {
			if (offBelowBox.getValue().isEmpty())
				syncTextBox(offBelowBox, offBelow.getState() / valueStep, true);
			else
				applyChangedValue = true;
		}

		if (applyChangedValue) {
			applyTextBoxValues();
			lastModification = -1;
		}

		wasOnAboveBoxFocused = onAboveBox.isFocused();
		wasOffBelowBoxFocused = offBelowBox.isFocused();
	}

	private boolean hasFocusedEmptyTextBox() {
		return onAboveBox != null && onAboveBox.isFocused() && onAboveBox.getValue().isEmpty()
			|| offBelowBox != null && offBelowBox.isFocused() && offBelowBox.getValue().isEmpty();
	}

	private void updateInputBoxes() {
		ThresholdType typeOfCurrentTarget = blockEntity.getTypeOfCurrentTarget();
		boolean forItems = typeOfCurrentTarget == ThresholdType.ITEM;
		boolean hasSeparateSuffix = forItems || typeOfCurrentTarget == ThresholdType.FLUID;
		final int valueStep = getValueStep();
		inStacks.active = inStacks.visible = forItems;
		onAbove.setWidth(forItems ? 48 : 103);
		offBelow.setWidth(forItems ? 48 : 103);

		boolean supported = typeOfCurrentTarget != ThresholdType.UNSUPPORTED;
		onAbove.visible = supported;
		offBelow.visible = supported;
		onAboveBox.visible = onAboveBox.active = supported;
		offBelowBox.visible = offBelowBox.active = supported;
		onAboveBox.setWidth(hasSeparateSuffix ? ITEM_INPUT_WIDTH : WIDE_INPUT_WIDTH);
		offBelowBox.setWidth(hasSeparateSuffix ? ITEM_INPUT_WIDTH : WIDE_INPUT_WIDTH);

		if (!supported)
			return;

		int min = blockEntity.currentMinLevel + valueStep;
		int max = blockEntity.currentMaxLevel;
		onAbove.withRange(min, max + 1);
		int roundedState = Mth.clamp((onAbove.getState() / valueStep) * valueStep, min, max);
		if (roundedState != onAbove.getState()) {
			onAbove.setState(roundedState);
			onAbove.onChanged();
		}

		min = blockEntity.currentMinLevel;
		max = blockEntity.currentMaxLevel - valueStep;
		offBelow.withRange(min, max + 1);
		roundedState = Mth.clamp((offBelow.getState() / valueStep) * valueStep, min, max);
		if (roundedState != offBelow.getState()) {
			offBelow.setState(roundedState);
			offBelow.onChanged();
		}
	}

	private int getValueStep() {
		boolean stacks = inStacks.getState() == 1;
		int valueStep = 1;
		if (blockEntity.getTypeOfCurrentTarget() == ThresholdType.FLUID)
			valueStep = 1000;
		else if (stacks)
			valueStep = 64;
		return valueStep;
	}

	@Override
	public void removed() {
		send(blockEntity.isInverted());
	}

	protected void send(boolean invert) {
		applyTextBoxValues();
		CatnipServices.NETWORK.sendToServer(new ConfigureThresholdSwitchPacket(blockEntity.getBlockPos(), offBelow.getState(),
			onAbove.getState(), invert, inStacks.getState() == 1));
	}

	private void applyTextBoxValues() {
		if (onAboveBox == null || offBelowBox == null)
			return;
		if (blockEntity.getTypeOfCurrentTarget() == ThresholdType.UNSUPPORTED)
			return;

		int valueStep = getValueStep();
		Long upper = parseLong(onAboveBox);
		Long lower = parseLong(offBelowBox);

		int minUpper = blockEntity.currentMinLevel + valueStep;
		int maxUpper = blockEntity.currentMaxLevel;
		int minLower = blockEntity.currentMinLevel;
		int maxLower = blockEntity.currentMaxLevel - valueStep;

		if (upper != null)
			onAbove.setState(clampScaledValue(upper, valueStep, minUpper, maxUpper));

		if (lower != null)
			offBelow.setState(clampScaledValue(lower, valueStep, minLower, maxLower));

		if (offBelow.getState() >= onAbove.getState()) {
			if (upper != null)
				offBelow.setState(Mth.clamp(onAbove.getState() - valueStep, minLower, maxLower));
			else
				onAbove.setState(Mth.clamp(offBelow.getState() + valueStep, minUpper, maxUpper));
		}

		onAbove.onChanged();
		offBelow.onChanged();
		syncTextBoxesFromScrollInputs(true);
	}

	private void syncTextBoxesFromScrollInputs(boolean force) {
		if (onAboveBox == null || offBelowBox == null)
			return;

		int valueStep = getValueStep();
		syncTextBox(onAboveBox, onAbove.getState() / valueStep, force);
		syncTextBox(offBelowBox, offBelow.getState() / valueStep, force);
	}

	private void syncTextBox(EditBox box, int value, boolean force) {
		if (!force && box.isFocused())
			return;

		String expected = Integer.toString(value);
		if (!expected.equals(box.getValue())) {
			box.setValue(expected);
			box.moveCursorToStart(false);
			box.moveCursorToEnd(false);
		}
	}

	private boolean isIntegerText(String text) {
		return text.isEmpty() || text.chars().allMatch(c -> c >= '0' && c <= '9');
	}

	private Long parseLong(EditBox box) {
		String value = box.getValue();
		if (value == null || value.isEmpty())
			return null;
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ex) {
			return Long.MAX_VALUE;
		}
	}

	private int clampScaledValue(long value, int valueStep, int min, int max) {
		long scaled = value > Long.MAX_VALUE / valueStep ? Long.MAX_VALUE : value * valueStep;
		return (int) Mth.clamp(scaled, min, max);
	}
}
