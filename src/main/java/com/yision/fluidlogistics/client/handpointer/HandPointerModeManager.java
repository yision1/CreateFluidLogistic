package com.yision.fluidlogistics.client.handpointer;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class HandPointerModeManager {

    public enum SelectionMode {
        NONE,
        ARM,
        DEPOT,
        DISPLAY_LINK,
        FROGPORT,
        MAILBOX,
        MECHANICAL_FLUID_GUN
    }

    private static SelectionMode currentMode = SelectionMode.NONE;

    public static SelectionMode getCurrentMode() {
        return currentMode;
    }

    public static boolean isInSelectionMode() {
        return currentMode != SelectionMode.NONE;
    }

    public static boolean tryEnterMode(SelectionMode mode) {
        if (mode == SelectionMode.NONE || isInSelectionMode()) {
            return false;
        }
        currentMode = mode;
        return true;
    }

    public static void exitMode(Player player, Level level) {
        SelectionMode exiting = currentMode;
        currentMode = SelectionMode.NONE;
        if (exiting == SelectionMode.ARM) {
            ArmSelectionHandler.clearSelection(level);
        } else if (exiting == SelectionMode.DEPOT) {
            DepotSelectionHandler.clearSelection();
        } else if (exiting == SelectionMode.DISPLAY_LINK) {
            DisplayLinkSelectionHandler.clearSelection();
        } else if (exiting == SelectionMode.FROGPORT) {
            FrogportSelectionHandler.clearSelection();
        } else if (exiting == SelectionMode.MAILBOX) {
            MailboxSelectionHandler.clearSelection();
        } else if (exiting == SelectionMode.MECHANICAL_FLUID_GUN) {
            MechanicalFluidGunSelectionHandler.clearSelection();
        }
    }

    public static void forceReset(Level level) {
        SelectionMode exiting = currentMode;
        currentMode = SelectionMode.NONE;
        if (exiting == SelectionMode.ARM) {
            ArmSelectionHandler.clearSelection(level);
        } else {
            DepotSelectionHandler.clearSelection();
            DisplayLinkSelectionHandler.clearSelection();
            FrogportSelectionHandler.clearSelection();
            MailboxSelectionHandler.clearSelection();
            MechanicalFluidGunSelectionHandler.clearSelection();
        }
    }
}
