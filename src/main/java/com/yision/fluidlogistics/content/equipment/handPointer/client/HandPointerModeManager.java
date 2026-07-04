package com.yision.fluidlogistics.content.equipment.handPointer.client;

import java.util.EnumMap;

import net.minecraft.client.Minecraft;
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
        LOGISTICS,
        MECHANICAL_FLUID_GUN
    }

    public interface HandPointerMode {
        void tick(Minecraft mc);
        void clear(Player player, Level level);
    }

    private static final EnumMap<SelectionMode, HandPointerMode> MODES = new EnumMap<>(SelectionMode.class);
    private static SelectionMode currentMode = SelectionMode.NONE;

    static {
        MODES.put(SelectionMode.ARM, new HandPointerMode() {
            @Override
            public void tick(Minecraft mc) {
                ArmSelectionHandler.renderSelection(mc);
                clearHoverPreviewsExcept(SelectionMode.ARM);
            }

            @Override
            public void clear(Player player, Level level) {
                ArmSelectionHandler.clearSelection(level);
            }
        });
        MODES.put(SelectionMode.DEPOT, new HandPointerMode() {
            @Override
            public void tick(Minecraft mc) {
                DepotSelectionHandler.renderSelection(mc);
                clearHoverPreviewsExcept(SelectionMode.DEPOT);
            }

            @Override
            public void clear(Player player, Level level) {
                DepotSelectionHandler.clearSelection();
            }
        });
        MODES.put(SelectionMode.DISPLAY_LINK, new HandPointerMode() {
            @Override
            public void tick(Minecraft mc) {
                DisplayLinkSelectionHandler.renderSelection(mc);
                clearHoverPreviewsExcept(SelectionMode.DISPLAY_LINK);
            }

            @Override
            public void clear(Player player, Level level) {
                DisplayLinkSelectionHandler.clearSelection();
            }
        });
        MODES.put(SelectionMode.FROGPORT, new HandPointerMode() {
            @Override
            public void tick(Minecraft mc) {
                FrogportSelectionHandler.tickChainTarget(mc);
                FrogportSelectionHandler.renderSelection(mc);
                clearHoverPreviewsExcept(SelectionMode.FROGPORT);
            }

            @Override
            public void clear(Player player, Level level) {
                FrogportSelectionHandler.clearSelection();
            }
        });
        MODES.put(SelectionMode.MAILBOX, new HandPointerMode() {
            @Override
            public void tick(Minecraft mc) {
                MailboxSelectionHandler.tickStationTarget(mc);
                MailboxSelectionHandler.renderSelection(mc);
                clearHoverPreviewsExcept(SelectionMode.MAILBOX);
            }

            @Override
            public void clear(Player player, Level level) {
                MailboxSelectionHandler.clearSelection();
            }
        });
        MODES.put(SelectionMode.LOGISTICS, new HandPointerMode() {
            @Override
            public void tick(Minecraft mc) {
                LogisticsSelectionHandler.renderSelection(mc);
                LogisticsSelectionHandler.renderHoverPreview(mc);
                clearHoverPreviewsExcept(SelectionMode.LOGISTICS);
            }

            @Override
            public void clear(Player player, Level level) {
                LogisticsSelectionHandler.clearSelection();
            }
        });
        MODES.put(SelectionMode.MECHANICAL_FLUID_GUN, new HandPointerMode() {
            @Override
            public void tick(Minecraft mc) {
                MechanicalFluidGunSelectionHandler.renderSelection(mc);
                clearHoverPreviewsExcept(SelectionMode.MECHANICAL_FLUID_GUN);
            }

            @Override
            public void clear(Player player, Level level) {
                MechanicalFluidGunSelectionHandler.clearSelection();
            }
        });
    }

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

    public static boolean tickCurrentMode(Minecraft mc) {
        HandPointerMode mode = MODES.get(currentMode);
        if (mode == null) {
            return false;
        }
        mode.tick(mc);
        return true;
    }

    public static void exitMode(Player player, Level level) {
        SelectionMode exiting = currentMode;
        currentMode = SelectionMode.NONE;
        HandPointerMode mode = MODES.get(exiting);
        if (mode != null) {
            mode.clear(player, level);
        }
        clearHoverPreviews();
    }

    public static void clearHoverPreviews() {
        clearHoverPreviewsExcept(SelectionMode.NONE);
    }

    private static void clearHoverPreviewsExcept(SelectionMode mode) {
        if (mode != SelectionMode.DEPOT) {
            DepotSelectionHandler.clearHoverPreview();
        }
        if (mode != SelectionMode.DISPLAY_LINK) {
            DisplayLinkSelectionHandler.clearHoverPreview();
        }
        if (mode != SelectionMode.FROGPORT) {
            FrogportSelectionHandler.clearHoverPreview();
        }
        if (mode != SelectionMode.MAILBOX) {
            MailboxSelectionHandler.clearHoverPreview();
        }
        if (mode != SelectionMode.LOGISTICS) {
            LogisticsSelectionHandler.clearHoverPreview();
        }
        if (mode != SelectionMode.MECHANICAL_FLUID_GUN) {
            MechanicalFluidGunSelectionHandler.clearHoverPreview();
        }
    }
}
