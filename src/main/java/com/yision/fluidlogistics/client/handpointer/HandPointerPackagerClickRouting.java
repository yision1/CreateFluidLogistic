package com.yision.fluidlogistics.client.handpointer;

final class HandPointerPackagerClickRouting {

    enum PackagerClickAction {
        NOT_A_PACKAGER,
        TOGGLE_PACKAGER,
        HANDLE_AS_SELECTION,
        EXIT_MODE
    }

    private HandPointerPackagerClickRouting() {
    }

    static PackagerClickAction route(HandPointerModeManager.SelectionMode mode, boolean clickedPackager) {
        if (!clickedPackager) {
            return PackagerClickAction.NOT_A_PACKAGER;
        }

        if (mode == HandPointerModeManager.SelectionMode.NONE) {
            return PackagerClickAction.TOGGLE_PACKAGER;
        }

        if (mode == HandPointerModeManager.SelectionMode.ARM
            || mode == HandPointerModeManager.SelectionMode.DEPOT) {
            return PackagerClickAction.HANDLE_AS_SELECTION;
        }

        return PackagerClickAction.EXIT_MODE;
    }
}
