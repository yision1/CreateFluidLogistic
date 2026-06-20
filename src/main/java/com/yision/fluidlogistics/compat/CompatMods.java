package com.yision.fluidlogistics.compat;

import net.minecraftforge.fml.ModList;

public final class CompatMods {
    public static final String JEI = "jei";
    public static final String EMI = "emi";
    public static final String CREATE_DRAGONS_PLUS = "create_dragons_plus";

    private CompatMods() {
    }

    public static boolean jeiLoaded() {
        return ModList.get().isLoaded(JEI);
    }

    public static boolean emiLoaded() {
        return ModList.get().isLoaded(EMI);
    }

    public static boolean createDragonsPlusLoaded() {
        return ModList.get().isLoaded(CREATE_DRAGONS_PLUS);
    }
}
