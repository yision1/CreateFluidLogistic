package com.yision.fluidlogistics.compat.cal;

import java.lang.reflect.Field;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;

public final class CalFactoryPanelCompat {

    private static final Field PROMISE_LIMIT = findField("CAL$promiseLimit");
    private static final Field ADDITIONAL_STOCK = findField("CAL$AdditionalStock");
    private static final Field REMAINING_ADDITIONAL = findField("CAL$RemainingAdditional");

    private CalFactoryPanelCompat() {
    }

    public static void resetFluidPanelState(FactoryPanelBehaviour panel) {
        setInt(PROMISE_LIMIT, panel, -1);
        setInt(ADDITIONAL_STOCK, panel, 0);
        setInt(REMAINING_ADDITIONAL, panel, 0);
    }

    private static Field findField(String name) {
        try {
            Field field = FactoryPanelBehaviour.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void setInt(Field field, Object owner, int value) {
        if (field == null) {
            return;
        }
        try {
            field.setInt(owner, value);
        } catch (IllegalAccessException ignored) {
        }
    }
}
