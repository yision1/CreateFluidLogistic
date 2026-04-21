package com.yision.fluidlogistics.client.handpointer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.item.HandPointerItem;
import com.yision.fluidlogistics.network.HandPointerAuthorizeLogisticsNetworkPacket;
import com.yision.fluidlogistics.network.HandPointerClearClipboardAddressPacket;
import com.yision.fluidlogistics.network.HandPointerDisplayLinkConfigurationPacket;
import com.yision.fluidlogistics.network.HandPointerModeEnteredPacket;
import com.yision.fluidlogistics.network.HandPointerPackagerTogglePacket;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class HandPointerInteractionHandler {
    private static final int STATUS_SELECTED_COLOR = 0xDDC166;
    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;
    private static final int STATUS_NEUTRAL_COLOR = 0xA5A5A5;
    private static final int STATUS_FORCED_ON_COLOR = 0xFF6171;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof HandPointerItem)) {
            if (HandPointerModeManager.isInSelectionMode()) {
                HandPointerModeManager.exitMode(player, player.level());
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            }
            DepotSelectionHandler.clearHoverPreview();
            DisplayLinkSelectionHandler.clearHoverPreview();
            FrogportSelectionHandler.clearHoverPreview();
            MailboxSelectionHandler.clearHoverPreview();
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.ARM) {
            ArmSelectionHandler.renderSelection(mc);
            DepotSelectionHandler.clearHoverPreview();
            DisplayLinkSelectionHandler.clearHoverPreview();
            FrogportSelectionHandler.clearHoverPreview();
            MailboxSelectionHandler.clearHoverPreview();
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.DEPOT) {
            DepotSelectionHandler.renderSelection(mc);
            DisplayLinkSelectionHandler.clearHoverPreview();
            FrogportSelectionHandler.clearHoverPreview();
            MailboxSelectionHandler.clearHoverPreview();
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.DISPLAY_LINK) {
            DisplayLinkSelectionHandler.renderSelection(mc);
            DepotSelectionHandler.clearHoverPreview();
            FrogportSelectionHandler.clearHoverPreview();
            MailboxSelectionHandler.clearHoverPreview();
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.FROGPORT) {
            FrogportSelectionHandler.tickChainTarget(mc);
            FrogportSelectionHandler.renderSelection(mc);
            DepotSelectionHandler.clearHoverPreview();
            DisplayLinkSelectionHandler.clearHoverPreview();
            MailboxSelectionHandler.clearHoverPreview();
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.MAILBOX) {
            MailboxSelectionHandler.tickStationTarget(mc);
            MailboxSelectionHandler.renderSelection(mc);
            DepotSelectionHandler.clearHoverPreview();
            DisplayLinkSelectionHandler.clearHoverPreview();
            FrogportSelectionHandler.clearHoverPreview();
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.LOGISTICS) {
            LogisticsSelectionHandler.renderSelection(mc);
            DepotSelectionHandler.clearHoverPreview();
            DisplayLinkSelectionHandler.clearHoverPreview();
            FrogportSelectionHandler.clearHoverPreview();
            MailboxSelectionHandler.clearHoverPreview();
            return;
        }

        DepotSelectionHandler.clearHoverPreview();
        DisplayLinkSelectionHandler.clearHoverPreview();
        FrogportSelectionHandler.renderHoveredConnectionPreview(mc);
        MailboxSelectionHandler.renderHoveredConnectionPreview(mc);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        if (player == null || level == null) {
            return;
        }

        if (!(player.getMainHandItem().getItem() instanceof HandPointerItem)) {
            return;
        }

        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (mc.hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (handleUseClick(player, level, pos, state, blockHitResult.getLocation())) {
                event.setCanceled(true);
                event.setSwingHand(true);
            }
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof HandPointerItem)
            || event.getHand() != InteractionHand.MAIN_HAND
            || !event.getLevel().isClientSide()) {
            return;
        }
        if (HandPointerModeManager.isInSelectionMode()) {
            HandPointerModeManager.exitMode(player, event.getLevel());
            sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (!(player.getMainHandItem().getItem() instanceof HandPointerItem)
            || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!level.isClientSide()) {
            return;
        }
    }

    private boolean handleUseClick(Player player, Level level, BlockPos pos, BlockState state, Vec3 clickLocation) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        HandPointerPackagerClickRouting.PackagerClickAction packagerClickAction =
            HandPointerPackagerClickRouting.route(HandPointerModeManager.getCurrentMode(), isPackagerFamily(state));

        if (packagerClickAction == HandPointerPackagerClickRouting.PackagerClickAction.EXIT_MODE) {
            HandPointerModeManager.exitMode(player, level);
            sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            return true;
        }

        if (packagerClickAction == HandPointerPackagerClickRouting.PackagerClickAction.TOGGLE_PACKAGER
            && tryTogglePackager(player, level, pos, state)) {
            return true;
        }

        if (!HandPointerModeManager.isInSelectionMode()
            && player.isShiftKeyDown()
            && LogisticsSelectionHandler.isLogisticsBlockEntity(blockEntity)) {
            HandPointerAuthorizeLogisticsNetworkPacket.send(pos, getTargetedPanelSlot(pos, state, clickLocation));
            return true;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.FROGPORT
            && MailboxSelectionHandler.isMailbox(level, pos)) {
            HandPointerModeManager.exitMode(player, level);
            sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            return true;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.MAILBOX
            && FrogportSelectionHandler.isFrogport(level, pos)) {
            HandPointerModeManager.exitMode(player, level);
            sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            return true;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.LOGISTICS
            && !LogisticsSelectionHandler.isLogisticsBlockEntity(blockEntity)) {
            HandPointerModeManager.exitMode(player, level);
            sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            return true;
        }

        if (HandPointerModeManager.isInSelectionMode()) {
            handleSelectionClick(player, level, pos, state, clickLocation);
            return true;
        }

        if (LogisticsSelectionHandler.isLogisticsBlockEntity(blockEntity)) {
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.LOGISTICS)) {
                LogisticsSelectionHandler.handleNetworkClick(blockEntity, pos, player, level, state, clickLocation);
                HandPointerModeEnteredPacket.send();
            }
            return true;
        }

        if (FrogportSelectionHandler.isFrogport(level, pos)) {
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.FROGPORT)) {
                FrogportSelectionHandler.setSelection(pos);
                playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                sendStatus(player, "fluidlogistics.hand_pointer.frogport.selected", STATUS_SELECTED_COLOR);
                HandPointerModeEnteredPacket.send();
            }
            return true;
        }

        if (MailboxSelectionHandler.isMailbox(level, pos)) {
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.MAILBOX)) {
                MailboxSelectionHandler.setSelection(pos);
                playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                sendStatus(player, "fluidlogistics.hand_pointer.mailbox.selected", STATUS_SELECTED_COLOR);
                HandPointerModeEnteredPacket.send();
            }
            return true;
        }

        if (blockEntity instanceof ArmBlockEntity) {
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.ARM)) {
                ArmSelectionHandler.enterArmMode(pos, player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.arm.selected", STATUS_SELECTED_COLOR);
                HandPointerModeEnteredPacket.send();
            }
            return true;
        }

        if (blockEntity instanceof EjectorBlockEntity) {
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.DEPOT)) {
                DepotSelectionHandler.enterMode(level, pos);
                playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                sendStatus(player, "fluidlogistics.hand_pointer.depot.selected", STATUS_SELECTED_COLOR);
                HandPointerModeEnteredPacket.send();
            }
            return true;
        }

        if (isDisplayBoard(level, pos, state)) {
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.DISPLAY_LINK)) {
                DisplayLinkSelectionHandler.setSelectedDisplayBoard(level, pos);
                playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                sendStatus(player, "fluidlogistics.hand_pointer.display_link.selected", STATUS_SELECTED_COLOR);
                HandPointerModeEnteredPacket.send();
            }
            return true;
        }

        return false;
    }

    private void handleSelectionClick(Player player, Level level, BlockPos pos, BlockState state, Vec3 clickLocation) {
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.LOGISTICS) {
            if (!LogisticsSelectionHandler.isLogisticsBlockEntity(blockEntity)) {
                HandPointerModeManager.exitMode(player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
                return;
            }

            LogisticsSelectionHandler.handleNetworkClick(blockEntity, pos, player, level, state, clickLocation);
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.ARM) {
            if (!ArmSelectionHandler.isArmInteractable(blockEntity, state, level, pos)) {
                HandPointerModeManager.exitMode(player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
                return;
            }

            if (ArmSelectionHandler.isArmSelected(pos)) {
                ArmSelectionHandler.submitSession(player, level);
                HandPointerModeManager.exitMode(player, level);
                return;
            }

            if (blockEntity instanceof ArmBlockEntity) {
                if (!ArmSelectionHandler.addArm(pos, player, level)) {
                    playDenySound(level, pos);
                }
                return;
            }

            if (player.isShiftKeyDown()) {
                ArmSelectionHandler.removePoint(pos, player, level);
            } else {
                ArmSelectionHandler.handlePointInteraction(level, pos, state, player);
            }
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.DEPOT) {
            if (DepotSelectionHandler.isSelectedEjector(pos)) {
                if (DepotSelectionHandler.submit()) {
                    playBlockSound(level, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f);
                    HandPointerModeManager.exitMode(player, level);
                    sendStatus(player, "fluidlogistics.hand_pointer.depot.connection_set", STATUS_CONNECTABLE_COLOR);
                } else {
                    playDenySound(level, pos);
                }
                return;
            }

            DepotSelectionHandler.setTarget(pos);
            playBlockSound(level, pos, SoundEvents.LEVER_CLICK, 0.25f, 1.2f);
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.DISPLAY_LINK) {
            if (!AllBlocks.DISPLAY_LINK.has(state)) {
                HandPointerModeManager.exitMode(player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
                return;
            }

            if (DisplayLinkSelectionHandler.isDisplayLinkConnectedToSelectedBoard(pos, level)) {
                playDenySound(level, pos);
                sendStatus(player, "fluidlogistics.hand_pointer.display_link.already_connected", STATUS_INVALID_COLOR);
                return;
            }

            if (DisplayLinkSelectionHandler.isDisplayLinkOutOfRange(pos)) {
                playDenySound(level, pos);
                sendStatus(player, "fluidlogistics.hand_pointer.too_far", STATUS_INVALID_COLOR);
                return;
            }

            HandPointerDisplayLinkConfigurationPacket.send(pos, DisplayLinkSelectionHandler.getSelectedDisplayBoardPos());
            playBlockSound(level, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f);
            HandPointerModeManager.exitMode(player, level);
            sendStatus(player, "fluidlogistics.hand_pointer.display_link.connection_set", STATUS_CONNECTABLE_COLOR);
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.FROGPORT) {
            if (FrogportSelectionHandler.isFrogport(level, pos)) {
                if (FrogportSelectionHandler.isFrogportSelected(pos)) {
                    FrogportSelectionHandler.removeFrogport(pos, player, level);
                    if (!FrogportSelectionHandler.hasSelection()) {
                        HandPointerModeManager.exitMode(player, level);
                        sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
                    }
                } else if (!FrogportSelectionHandler.addFrogport(pos, player, level)) {
                    playDenySound(level, pos);
                }
                return;
            }

            FrogportSelectionHandler.tickChainTarget(Minecraft.getInstance());

            if (ChainConveyorInteractionHandler.selectedLift != null) {
                if (FrogportSelectionHandler.tryConnectCurrentTarget(level)) {
                    playBlockSound(level, FrogportSelectionHandler.getSelectedFrogportPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f);
                    HandPointerModeManager.exitMode(player, level);
                    sendStatus(player, "fluidlogistics.hand_pointer.frogport.connection_set", STATUS_CONNECTABLE_COLOR);
                } else {
                    playDenySound(level, FrogportSelectionHandler.getSelectedFrogportPos());
                    if (FrogportSelectionHandler.isCurrentTargetOutOfRange(level)) {
                        sendStatus(player, "fluidlogistics.hand_pointer.too_far", STATUS_INVALID_COLOR);
                    }
                }
                return;
            }

            if (AllBlocks.CHAIN_CONVEYOR.has(state)) {
                playDenySound(level, FrogportSelectionHandler.getSelectedFrogportPos());
                return;
            }

            HandPointerModeManager.exitMode(player, level);
            sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.MAILBOX) {
            if (MailboxSelectionHandler.isMailbox(level, pos)) {
                if (MailboxSelectionHandler.isMailboxSelected(pos)) {
                    MailboxSelectionHandler.removeMailbox(pos, player, level);
                    if (!MailboxSelectionHandler.hasSelection()) {
                        HandPointerModeManager.exitMode(player, level);
                        sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
                    }
                } else if (!MailboxSelectionHandler.addMailbox(pos, player, level)) {
                    playDenySound(level, pos);
                }
                return;
            }

            if (!MailboxSelectionHandler.isStation(level, pos)) {
                HandPointerModeManager.exitMode(player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
                return;
            }
            if (MailboxSelectionHandler.tryConnectCurrentTarget(level)) {
                playBlockSound(level, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f);
                HandPointerModeManager.exitMode(player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.mailbox.connection_set", STATUS_CONNECTABLE_COLOR);
            } else {
                playDenySound(level, pos);
                if (MailboxSelectionHandler.isCurrentTargetAlreadyConnected(level)) {
                    sendStatus(player, "fluidlogistics.hand_pointer.mailbox.already_connected", STATUS_INVALID_COLOR);
                } else if (MailboxSelectionHandler.isCurrentTargetOutOfRange(level)) {
                    sendStatus(player, "fluidlogistics.hand_pointer.too_far", STATUS_INVALID_COLOR);
                }
            }
        }
    }

    private static void playDenySound(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        AllSoundEvents.DENY.playAt(level, pos, 1, 1, false);
    }

    private static void playBlockSound(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        if (level == null || pos == null) {
            return;
        }
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    private static void sendStatus(Player player, String key, int color) {
        CreateLang.builder().translate(key).color(color).sendStatus(player);
    }

    private static FactoryPanelBlock.PanelSlot getTargetedPanelSlot(BlockPos pos, BlockState state, Vec3 clickLocation) {
        return clickLocation == null
            ? FactoryPanelBlock.PanelSlot.BOTTOM_LEFT
            : FactoryPanelBlock.getTargetedSlot(pos, state, clickLocation);
    }

    private boolean tryTogglePackager(Player player, Level level, BlockPos pos, BlockState state) {
        boolean isCreatePackager = AllBlocks.PACKAGER.has(state);
        boolean isRepackager = AllBlocks.REPACKAGER.has(state);
        boolean isFluidPackager = com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state);
        if (!isCreatePackager && !isRepackager && !isFluidPackager) {
            return false;
        }

        if (player.isShiftKeyDown()) {
            HandPointerClearClipboardAddressPacket.send(pos);
            playBlockSound(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.85f);
            return true;
        }

        HandPointerPackagerTogglePacket.send(pos);
        playBlockSound(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 1.0f);

        boolean willBePowered = state.hasProperty(BlockStateProperties.POWERED)
            ? !state.getValue(BlockStateProperties.POWERED)
            : true;
        String key = isFluidPackager
            ? (willBePowered
                ? "fluidlogistics.hand_pointer.fluid_packager.powered_on"
                : "fluidlogistics.hand_pointer.fluid_packager.powered_off")
            : isRepackager
            ? (willBePowered
                ? "fluidlogistics.hand_pointer.repackager.powered_on"
                : "fluidlogistics.hand_pointer.repackager.powered_off")
            : (willBePowered
                ? "fluidlogistics.hand_pointer.packager.powered_on"
                : "fluidlogistics.hand_pointer.packager.powered_off");
        sendStatus(player, key, willBePowered ? STATUS_FORCED_ON_COLOR : STATUS_NEUTRAL_COLOR);
        return true;
    }

    private static boolean isPackagerFamily(BlockState state) {
        return AllBlocks.PACKAGER.has(state)
            || AllBlocks.REPACKAGER.has(state)
            || com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state);
    }

    private static boolean isDisplayBoard(Level level, BlockPos pos, BlockState state) {
        return !AllBlocks.DISPLAY_LINK.has(state) && DisplayTarget.get(level, pos) != null;
    }
}
