package com.yision.fluidlogistics.compat;

import net.neoforged.fml.ModList;

public final class CompatMods {
    public static final String CREATE_ENCHANTMENT_INDUSTRY = "create_enchantment_industry";
    public static final String CREATE_DRAGONS_PLUS = "create_dragons_plus";
    public static final String SABLE = "sable";
    public static final String JEI = "jei";
    public static final String EMI = "emi";
    public static final String KALEIDOSCOPE_TAVERN = "kaleidoscope_tavern";

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

    public static boolean jeiLoaded() {
        return ModList.get().isLoaded(JEI);
    }

    public static boolean emiLoaded() {
        return ModList.get().isLoaded(EMI);
    }

    public static boolean kaleidoscopeTavernLoaded() {
        return ModList.get().isLoaded(KALEIDOSCOPE_TAVERN);
    }
}
