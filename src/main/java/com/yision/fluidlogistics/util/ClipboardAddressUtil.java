package com.yision.fluidlogistics.util;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;

import net.minecraft.world.item.ItemStack;

public class ClipboardAddressUtil {

    private ClipboardAddressUtil() {
    }

    public static @Nullable String extractFirstAddress(ItemStack clipboardItem) {
        if (clipboardItem == null || clipboardItem.isEmpty()) {
            return null;
        }

        List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(clipboardItem);
        if (pages.isEmpty()) {
            return null;
        }

        for (List<ClipboardEntry> page : pages) {
            for (ClipboardEntry entry : page) {
                String text = entry.text.getString();
                if (text != null && text.startsWith("#") && text.length() > 1) {
                    String candidate = text.substring(1).stripLeading();
                    if (!candidate.isBlank()) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }
}
