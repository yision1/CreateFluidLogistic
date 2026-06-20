package com.yision.fluidlogistics.goggle;

import java.util.List;

import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class PackagerGoggleInfo {

	public static void addToTooltip(List<Component> tooltip, String address, boolean isManualOverride,
									boolean isRepackager, boolean isLinkedToNetwork, int cachedPackageCount) {
		if (isLinkedToNetwork) {
			CreateLang.builder()
				.translate("goggles.packager_title")
				.style(ChatFormatting.WHITE)
				.forGoggles(tooltip);

			CreateLang.builder()
				.translate("goggles.linked_to_network")
				.style(ChatFormatting.GREEN)
				.forGoggles(tooltip, 1);
		} else if (isRepackager) {
			CreateLang.builder()
				.translate("goggles.repackager_title")
				.style(ChatFormatting.WHITE)
				.forGoggles(tooltip);

			if (isManualOverride) {
				CreateLang.builder()
					.translate("goggles.manual_override")
					.style(ChatFormatting.RED)
					.forGoggles(tooltip, 1);
			}

			if (cachedPackageCount > 0) {
				CreateLang.builder()
					.translate("goggles.cached_packages", cachedPackageCount)
					.style(ChatFormatting.AQUA)
					.forGoggles(tooltip, 1);
			}
		} else {
			CreateLang.builder()
				.translate("goggles.packager_title")
				.style(ChatFormatting.WHITE)
				.forGoggles(tooltip);

			CreateLang.builder()
				.translate("goggles.address_label")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip);

			if (address != null && !address.isBlank()) {
				CreateLang.builder()
					.text(address)
					.style(ChatFormatting.GOLD)
					.forGoggles(tooltip, 1);
			} else {
				CreateLang.builder()
					.translate("goggles.no_address")
					.style(ChatFormatting.DARK_GRAY)
					.forGoggles(tooltip, 1);
			}

			if (isManualOverride) {
				CreateLang.builder()
					.translate("goggles.manual_override")
					.style(ChatFormatting.RED)
					.forGoggles(tooltip, 1);
			}
		}
	}

	public static void addToTooltip(List<Component> tooltip, String address, boolean isManualOverride,
									boolean isRepackager, boolean isLinkedToNetwork) {
		addToTooltip(tooltip, address, isManualOverride, isRepackager, isLinkedToNetwork, 0);
	}

	public static void addFluidPackagerToTooltip(List<Component> tooltip, String address, boolean isManualOverride,
												 boolean isLinkedToNetwork) {
		if (isLinkedToNetwork) {
			CreateLang.builder()
				.translate("goggles.fluid_packager_title")
				.style(ChatFormatting.WHITE)
				.forGoggles(tooltip);

			CreateLang.builder()
				.translate("goggles.linked_to_network")
				.style(ChatFormatting.GREEN)
				.forGoggles(tooltip, 1);
		} else {
			CreateLang.builder()
				.translate("goggles.fluid_packager_title")
				.style(ChatFormatting.WHITE)
				.forGoggles(tooltip);

			CreateLang.builder()
				.translate("goggles.address_label")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip);

			if (address != null && !address.isBlank()) {
				CreateLang.builder()
					.text(address)
					.style(ChatFormatting.GOLD)
					.forGoggles(tooltip, 1);
			} else {
				CreateLang.builder()
					.translate("goggles.no_address")
					.style(ChatFormatting.DARK_GRAY)
					.forGoggles(tooltip, 1);
			}
		}

		if (!isLinkedToNetwork && isManualOverride) {
			CreateLang.builder()
				.translate("goggles.manual_override")
				.style(ChatFormatting.RED)
				.forGoggles(tooltip, 1);
		}
	}

	public static void addFluidRepackagerToTooltip(List<Component> tooltip, String address,
												   boolean isManualOverride, int cachedPackageCount) {
		CreateLang.builder()
			.translate("goggles.fluid_repackager_title")
			.style(ChatFormatting.WHITE)
			.forGoggles(tooltip);

		if (isManualOverride) {
			CreateLang.builder()
				.translate("goggles.manual_override")
				.style(ChatFormatting.RED)
				.forGoggles(tooltip, 1);
		}

		if (cachedPackageCount > 0) {
			CreateLang.builder()
				.translate("goggles.cached_packages", cachedPackageCount)
				.style(ChatFormatting.AQUA)
				.forGoggles(tooltip, 1);
		}
	}

	public static void addFluidRepackagerToTooltip(List<Component> tooltip, String address,
												   boolean isManualOverride) {
		addFluidRepackagerToTooltip(tooltip, address, isManualOverride, 0);
	}
}
