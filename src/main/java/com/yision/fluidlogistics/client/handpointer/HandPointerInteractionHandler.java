package com.yision.fluidlogistics.client.handpointer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.item.HandPointerItem;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.network.HandPointerClearClipboardAddressPacket;
import com.yision.fluidlogistics.network.HandPointerDisplayLinkConfigurationPacket;
import com.yision.fluidlogistics.network.HandPointerPackagerTogglePacket;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.createmod.catnip.gui.ScreenOpener;
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
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HandPointerInteractionHandler {
    private static final int STATUS_SELECTED_COLOR = 0xDDC166;
    private static final int STATUS_CONNECTABLE_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;
    private static final int STATUS_NEUTRAL_COLOR = 0xA5A5A5;
    private static final int STATUS_FORCED_ON_COLOR = 0xFF6171;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!com.yision.fluidlogistics.config.Config.isHandPointerEnabled()) {
            HandPointerModeManager.forceReset(Minecraft.getInstance().level);
            ArmSelectionHandler.renderSelection(Minecraft.getInstance());
            DepotSelectionHandler.clearHoverPreview();
            DisplayLinkSelectionHandler.clearHoverPreview();
            FrogportSelectionHandler.clearHoverPreview();
            MailboxSelectionHandler.clearHoverPreview();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof HandPointerItem)) {
            boolean hadSelection = HandPointerModeManager.isInSelectionMode()
                || ArmSelectionHandler.hasSelection()
                || DepotSelectionHandler.hasSelection()
                || DisplayLinkSelectionHandler.hasSelection()
                || FrogportSelectionHandler.hasSelection()
                || MailboxSelectionHandler.hasSelection();
            if (hadSelection) {
                HandPointerModeManager.forceReset(mc.level);
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            }
            ArmSelectionHandler.renderSelection(mc);
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

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.MECHANICAL_FLUID_GUN) {
            MechanicalFluidGunSelectionHandler.renderSelection(mc);
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
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!com.yision.fluidlogistics.config.Config.isHandPointerEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        if (player == null || level == null) {
            return;
        }

        if (!(player.getMainHandItem().getItem() instanceof HandPointerItem)
            || !event.isUseItem()
            || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.FROGPORT) {
            FrogportSelectionHandler.tickChainTarget(mc);
            if (com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler.selectedLift != null) {
                event.setCanceled(true);
                event.setSwingHand(true);

                if (FrogportSelectionHandler.tryConnectCurrentTarget(level)) {
                    playBlockSound(level, FrogportSelectionHandler.getSelectedFrogportPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f);
                    HandPointerModeManager.exitMode(player, level);
                    sendStatus(player, "fluidlogistics.hand_pointer.frogport.connection_set", STATUS_CONNECTABLE_COLOR);
                } else {
                    playDenySound(level, FrogportSelectionHandler.getSelectedFrogportPos());
                    sendStatus(player, "fluidlogistics.hand_pointer.too_far", STATUS_INVALID_COLOR);
                }
                return;
            }
        }

        if (HandPointerModeManager.isInSelectionMode()
            && (mc.hitResult == null || mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS)) {
            HandPointerModeManager.exitMode(player, level);
            sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            event.setCanceled(true);
            event.setSwingHand(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (!com.yision.fluidlogistics.config.Config.isHandPointerEnabled()) {
            return;
        }

        Player player = event.getEntity();
        Level level = player.level();
        if (!(player.getMainHandItem().getItem() instanceof HandPointerItem)
            || event.getHand() != InteractionHand.MAIN_HAND
            || !level.isClientSide()) {
            return;
        }
        if (HandPointerModeManager.isInSelectionMode()) {
            HandPointerModeManager.exitMode(player, level);
            sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!com.yision.fluidlogistics.config.Config.isHandPointerEnabled()) {
            return;
        }

        Player player = event.getEntity();
        Level level = event.getLevel();
        if (!(player.getMainHandItem().getItem() instanceof HandPointerItem)
            || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        HandPointerPackagerClickRouting.PackagerClickAction packagerClickAction =
            HandPointerPackagerClickRouting.route(HandPointerModeManager.getCurrentMode(), isPackagerFamily(state));

        if (!shouldHandleRightClick(level, pos, state, blockEntity, packagerClickAction)) {
            return;
        }

        if (!level.isClientSide()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (packagerClickAction == HandPointerPackagerClickRouting.PackagerClickAction.EXIT_MODE) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            HandPointerModeManager.exitMode(player, level);
            sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            return;
        }

        if (packagerClickAction == HandPointerPackagerClickRouting.PackagerClickAction.TOGGLE_PACKAGER
            && tryTogglePackager(player, level, pos, state)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (HandPointerModeManager.isInSelectionMode()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            handleSelectionClick(player, level, pos, state, event.getFace());
            return;
        }

        if (blockEntity instanceof ArmBlockEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.ARM)) {
                ArmSelectionHandler.enterArmMode(pos, player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.arm.selected", STATUS_SELECTED_COLOR);
            }
            return;
        }

        if (blockEntity instanceof EjectorBlockEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.DEPOT)) {
                DepotSelectionHandler.enterMode(level, pos);
                playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                sendStatus(player, "fluidlogistics.hand_pointer.depot.selected", STATUS_SELECTED_COLOR);
            }
            return;
        }

        if (isDisplayBoard(level, pos, state)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.DISPLAY_LINK)) {
                DisplayLinkSelectionHandler.setSelectedDisplayBoard(level, pos);
                playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                sendStatus(player, "fluidlogistics.hand_pointer.display_link.selected", STATUS_SELECTED_COLOR);
            }
            return;
        }

        if (FrogportSelectionHandler.isFrogport(level, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.FROGPORT)) {
                FrogportSelectionHandler.setSelection(pos);
                playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                sendStatus(player, "fluidlogistics.hand_pointer.frogport.selected", STATUS_SELECTED_COLOR);
            }
            return;
        }

        if (MailboxSelectionHandler.isMailbox(level, pos)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.MAILBOX)) {
                MailboxSelectionHandler.setSelection(pos);
                playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
                sendStatus(player, "fluidlogistics.hand_pointer.mailbox.selected", STATUS_SELECTED_COLOR);
            }
            return;
        }

        if (blockEntity instanceof MechanicalFluidGunBlockEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.MECHANICAL_FLUID_GUN)) {
                MechanicalFluidGunSelectionHandler.enterMode(level, pos);
                playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            }
            return;
        }

        if (blockEntity instanceof ThresholdSwitchBlockEntity thresholdSwitch) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            ScreenOpener.open(new HandPointerThresholdSwitchScreen(thresholdSwitch));
            playBlockSound(level, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35f, 1.2f);
            return;
        }
    }

    private static boolean shouldHandleRightClick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity,
        HandPointerPackagerClickRouting.PackagerClickAction packagerClickAction) {
        if (packagerClickAction == HandPointerPackagerClickRouting.PackagerClickAction.EXIT_MODE) {
            return true;
        }

        if (packagerClickAction == HandPointerPackagerClickRouting.PackagerClickAction.TOGGLE_PACKAGER) {
            return true;
        }

        if (HandPointerModeManager.isInSelectionMode()) {
            return true;
        }

        if (blockEntity instanceof ArmBlockEntity
            || blockEntity instanceof EjectorBlockEntity
            || blockEntity instanceof ThresholdSwitchBlockEntity
            || isDisplayBoard(level, pos, state)
            || FrogportSelectionHandler.isFrogport(level, pos)
            || MailboxSelectionHandler.isMailbox(level, pos)) {
            return true;
        }

        return blockEntity instanceof MechanicalFluidGunBlockEntity;
    }

    private static void handleSelectionClick(Player player, Level level, BlockPos pos, BlockState state, Direction targetFace) {
        BlockEntity blockEntity = level.getBlockEntity(pos);

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

            FluidLogisticsPackets.getChannel()
                .sendToServer(new HandPointerDisplayLinkConfigurationPacket(pos, DisplayLinkSelectionHandler.getSelectedDisplayBoardPos()));
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

            if (!AllBlocks.CHAIN_CONVEYOR.has(state)) {
                HandPointerModeManager.exitMode(player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
                return;
            }
            FrogportSelectionHandler.tickChainTarget(Minecraft.getInstance());
            if (FrogportSelectionHandler.tryConnectCurrentTarget(level)) {
                playBlockSound(level, FrogportSelectionHandler.getSelectedFrogportPos(), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f);
                HandPointerModeManager.exitMode(player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.frogport.connection_set", STATUS_CONNECTABLE_COLOR);
            } else {
                playDenySound(level, FrogportSelectionHandler.getSelectedFrogportPos());
                sendStatus(player, "fluidlogistics.hand_pointer.too_far", STATUS_INVALID_COLOR);
            }
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
            } else if (MailboxSelectionHandler.isCurrentTargetAlreadyConnected(level)) {
                playDenySound(level, pos);
                sendStatus(player, "fluidlogistics.hand_pointer.mailbox.already_connected", STATUS_INVALID_COLOR);
            } else if (MailboxSelectionHandler.isCurrentTargetOutOfRange()) {
                playDenySound(level, pos);
                sendStatus(player,
                    "fluidlogistics.hand_pointer.too_far",
                    STATUS_INVALID_COLOR);
            }
        }

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.MECHANICAL_FLUID_GUN) {
            if (MechanicalFluidGunSelectionHandler.isSelectedGun(pos)) {
                MechanicalFluidGunSelectionHandler.SubmitResult result = MechanicalFluidGunSelectionHandler.submit(level);
                if (result.success()) {
                    playBlockSound(level, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f);
                    HandPointerModeManager.exitMode(player, level);
                    if (result.skippedCount() > 0) {
                        sendStatus(player, "fluidlogistics.mechanical_fluid_gun.targets_out_of_range",
                            STATUS_INVALID_COLOR, result.skippedCount());
                    } else {
                        sendStatus(player, "fluidlogistics.mechanical_fluid_gun.target_summary",
                            STATUS_CONNECTABLE_COLOR, result.sentCount());
                    }
                } else {
                    playDenySound(level, pos);
                    if (result.skippedCount() > 0) {
                        sendStatus(player, "fluidlogistics.mechanical_fluid_gun.targets_out_of_range",
                            STATUS_INVALID_COLOR, result.skippedCount());
                    } else {
                        sendStatus(player, "fluidlogistics.hand_pointer.too_far", STATUS_INVALID_COLOR);
                    }
                }
                return;
            }

            if (MechanicalFluidGunSelectionHandler.setTarget(level, pos, targetFace)) {
                playBlockSound(level, pos, SoundEvents.LEVER_CLICK, 0.25f, 1.2f);
            } else {
                playDenySound(level, pos);
                sendStatus(player, "fluidlogistics.hand_pointer.too_far", STATUS_INVALID_COLOR);
            }
            return;
        }
    }

    private static void playDenySound(Level level, BlockPos pos) {
        playBlockSound(level, pos, SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.85f);
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

    private static void sendStatus(Player player, String key, int color, Object... args) {
        CreateLang.builder().translate(key, args).color(color).sendStatus(player);
    }

    private static boolean tryTogglePackager(Player player, Level level, BlockPos pos, BlockState state) {
        boolean isCreatePackager = AllBlocks.PACKAGER.has(state);
        boolean isRepackager = AllBlocks.REPACKAGER.has(state);
        boolean isFluidPackager = com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state);
        boolean isFluidRepackager = com.yision.fluidlogistics.registry.AllBlocks.FLUID_REPACKAGER.has(state);
        if (!isCreatePackager && !isRepackager && !isFluidPackager && !isFluidRepackager) {
            return false;
        }

        if (player.isShiftKeyDown() && (isCreatePackager || isFluidPackager)) {
            FluidLogisticsPackets.getChannel().sendToServer(new HandPointerClearClipboardAddressPacket(pos));
            playBlockSound(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.85f);
            return true;
        }

        FluidLogisticsPackets.getChannel().sendToServer(new HandPointerPackagerTogglePacket(pos));
        playBlockSound(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 1.0f);

        boolean willBePowered = state.hasProperty(BlockStateProperties.POWERED)
            ? !state.getValue(BlockStateProperties.POWERED)
            : true;
        String key = isFluidRepackager
            ? (willBePowered
                ? "fluidlogistics.hand_pointer.fluid_repackager.powered_on"
                : "fluidlogistics.hand_pointer.fluid_repackager.powered_off")
            : isFluidPackager
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
            || com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state)
            || com.yision.fluidlogistics.registry.AllBlocks.FLUID_REPACKAGER.has(state);
    }

    private static boolean isDisplayBoard(Level level, BlockPos pos, BlockState state) {
        return !AllBlocks.DISPLAY_LINK.has(state) && DisplayTarget.get(level, pos) != null;
    }
}
