package com.yision.fluidlogistics.client;

import java.lang.reflect.Method;
import java.util.Locale;

import com.yision.fluidlogistics.FluidLogistics;

public final class JechSearchBridge {

    private static final String MATCH_CLASS = "me.towdium.jecharacters.utils.Match";

    private static volatile Method containsIgnoreCaseMethod;
    private static volatile boolean initialized;

    private JechSearchBridge() {
    }

    public static boolean containsIgnoreCase(String candidate, String query) {
        if (candidate == null || query == null) {
            return false;
        }

        Method method = getContainsIgnoreCaseMethod();
        if (method != null) {
            try {
                return (boolean) method.invoke(null, candidate, query, true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                FluidLogistics.LOGGER.debug("Failed to delegate search matching to JEC, falling back to vanilla contains", e);
                containsIgnoreCaseMethod = null;
            }
        }

        return candidate.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static Method getContainsIgnoreCaseMethod() {
        if (initialized) {
            return containsIgnoreCaseMethod;
        }

        synchronized (JechSearchBridge.class) {
            if (initialized) {
                return containsIgnoreCaseMethod;
            }

            try {
                Class<?> matchClass = Class.forName(MATCH_CLASS, false, JechSearchBridge.class.getClassLoader());
                containsIgnoreCaseMethod = matchClass.getMethod("contains", CharSequence.class, CharSequence.class,
                        boolean.class);
            } catch (ReflectiveOperationException | RuntimeException e) {
                FluidLogistics.LOGGER.debug("JEC search bridge is unavailable, using vanilla contains", e);
                containsIgnoreCaseMethod = null;
            }

            initialized = true;
            return containsIgnoreCaseMethod;
        }
    }
}
