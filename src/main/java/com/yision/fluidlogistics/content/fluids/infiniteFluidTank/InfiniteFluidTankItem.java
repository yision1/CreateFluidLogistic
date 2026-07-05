package com.yision.fluidlogistics.content.fluids.infiniteFluidTank;

import java.util.List;
import java.util.function.Consumer;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.content.fluids.infiniteFluidTank.InfiniteFluidSupplyRules;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fluids.FluidStack;

public class InfiniteFluidTankItem extends BlockItem {

	public InfiniteFluidTankItem(Block block, Properties properties) {
		super(block, properties);
	}

	public static FluidStack getContainedFluid(ItemStack stack) {
		CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
		if (blockEntityTag == null)
			return FluidStack.EMPTY;

		if (!blockEntityTag.contains("TankContent"))
			return FluidStack.EMPTY;

		return FluidStack.loadFluidStackFromNBT(blockEntityTag.getCompound("TankContent"));
	}

	public static boolean isInfiniteSupply(FluidStack fluid) {
		return InfiniteFluidSupplyRules.isInfiniteSupply(fluid);
	}

	@Override
	public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents,
	                            TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);

		FluidStack fluid = getContainedFluid(stack);
		if (fluid.isEmpty())
			return;

		tooltipComponents.add(fluid.getDisplayName()
			.copy()
			.withStyle(ChatFormatting.GRAY));

		if (isInfiniteSupply(fluid)) {
			tooltipComponents.add(CreateLang.translateDirect("hint.hose_pulley.title")
				.withStyle(ChatFormatting.GOLD));
			return;
		}

		tooltipComponents.add(Component.empty()
			.append(Component.literal(InfiniteFluidSupplyRules.formatBuckets(fluid.getAmount()))
				.withStyle(ChatFormatting.GOLD))
			.append(Component.literal(" / ")
				.withStyle(ChatFormatting.GRAY))
			.append(Component.literal(InfiniteFluidSupplyRules.getRequiredAmountText())
				.withStyle(ChatFormatting.DARK_GRAY)));
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		consumer.accept(SimpleCustomRenderer.create(this,
			new com.yision.fluidlogistics.content.fluids.infiniteFluidTank.client.InfiniteFluidTankItemRenderer()));
	}

	public static class TooltipModifier extends ItemDescription.Modifier {
		private int cachedThreshold = Integer.MIN_VALUE;

		public TooltipModifier(Item item, FontHelper.Palette palette) {
			super(item, palette);
		}

		@Override
		public void modify(ItemTooltipEvent context) {
			int threshold = InfiniteFluidSupplyRules.getRequiredAmount();
			if (checkLocale() || threshold != cachedThreshold) {
				cachedThreshold = threshold;
				ItemDescription.Builder builder = new ItemDescription.Builder(palette);
				builder.addSummary(I18n.get(item.getDescriptionId() + ".tooltip.summary",
					InfiniteFluidSupplyRules.getRequiredAmountText()));
				description = builder.build();
			}
			if (description != null)
				context.getToolTip().addAll(1, description.getCurrentLines());
		}
	}
}
