package com.yision.fluidlogistics.compat;

import net.neoforged.fml.ModList;

public final class CompatMods {
    public static final String CREATE_ENCHANTMENT_INDUSTRY = "create_enchantment_industry";
    public static final String CREATE_DRAGONS_PLUS = "create_dragons_plus";
    public static final String SABLE = "sable";

    private CompatMods() {
    }

    public static boolean createEnchantmentIndustryLoaded() {
        return ModList.get().isLoaded(CREATE_ENCHANTMENT_INDUSTRY);
    }

    public static boolean createDragonsPlusLoaded() {
        return ModList.get().isLoaded(CREATE_DRAGONS_PLUS);
    }

    public static boolean sableLoaded() {
        return ModList.get().isLoaded(SABLE);
    }
}
