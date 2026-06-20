package com.yision.fluidlogistics.client;

import org.jetbrains.annotations.Nullable;

public final class JeiRuntimeHolder {

    @Nullable
    private static Object runtime;

    private JeiRuntimeHolder() {
    }

    public static void setRuntime(@Nullable Object jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Nullable
    public static Object getRuntime() {
        return runtime;
    }

    public static void clear() {
        runtime = null;
    }
}
