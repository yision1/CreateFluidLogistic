package com.yision.fluidlogistics.content.equipment.handPointer.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.equipment.handPointer.filter.HandPointerFilterTargetResolver;
import com.yision.fluidlogistics.content.equipment.handPointer.HandPointerItem;
import com.yision.fluidlogistics.content.equipment.handPointer.logistics.LogisticsLinkResolver;
import com.yision.fluidlogistics.content.logistics.fluidPackager.repackager.FluidRepackagerBlockEntity;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerAuthorizeLogisticsNetworkPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerClearClipboardAddressPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerDisplayLinkConfigurationPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerOpenFilterMenuPacket;
import com.yision.fluidlogistics.content.equipment.handPointer.network.HandPointerPackagerTogglePacket;
import com.yision.fluidlogistics.util.PackagerTargetHelper;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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

    private static net.minecraft.resources.ResourceKey<Level> lastLevel;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) {
            return;
        }
        handleLevelChange(player, mc.level);

        if (!com.yision.fluidlogistics.config.Config.isHandPointerEnabled()) {
            if (HandPointerModeManager.isInSelectionMode()) {
                HandPointerModeManager.exitMode(player, mc.level);
            }
            HandPointerModeManager.clearHoverPreviews();
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
                HandPointerModeManager.exitMode(player, mc.level);
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            }
            HandPointerModeManager.clearHoverPreviews();
            return;
        }

        if (HandPointerModeManager.tickCurrentMode(mc)) {
            return;
        }

        DepotSelectionHandler.clearHoverPreview();
        DisplayLinkSelectionHandler.clearHoverPreview();
        MechanicalFluidGunSelectionHandler.clearHoverPreview();
        HandPointerWrenchHoverPreviewHandler.render(mc);
        LogisticsSelectionHandler.renderHoverPreview(mc);
    }

    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (HandPointerModeManager.isInSelectionMode()) {
            HandPointerModeManager.exitMode(Minecraft.getInstance().player, Minecraft.getInstance().level);
        } else {
            HandPointerModeManager.clearHoverPreviews();
        }
        lastLevel = null;
    }

    private static void handleLevelChange(Player player, Level level) {
        if (level == null) {
            lastLevel = null;
            return;
        }
        net.minecraft.resources.ResourceKey<Level> currentLevel = level.dimension();
        if (lastLevel != null && lastLevel != currentLevel) {
            if (HandPointerModeManager.isInSelectionMode()) {
                HandPointerModeManager.exitMode(player, level);
            } else {
                HandPointerModeManager.clearHoverPreviews();
            }
        }
        lastLevel = currentLevel;
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
        BlockHitResult hitResult = event.getHitVec();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        HandPointerPackagerClickRouting.PackagerClickAction packagerClickAction =
            HandPointerPackagerClickRouting.route(HandPointerModeManager.getCurrentMode(), PackagerTargetHelper.isToggleTarget(blockEntity, state));

        if (!shouldHandleRightClick(level, player, pos, hitResult, state, blockEntity, packagerClickAction)) {
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

        if (HandPointerModeManager.getCurrentMode() == HandPointerModeManager.SelectionMode.LOGISTICS) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!LogisticsSelectionHandler.isLogisticsBlockEntity(blockEntity)) {
                HandPointerModeManager.exitMode(player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
            } else {
                Vec3 clickLocation = hitResult != null ? hitResult.getLocation() : null;
                LogisticsSelectionHandler.handleNetworkClick(blockEntity, pos, player, level, state, clickLocation);
            }
            return;
        }

        if (HandPointerModeManager.isInSelectionMode()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            handleSelectionClick(player, level, pos, state, event.getFace());
            return;
        }

        if (!player.isShiftKeyDown()
            && hitResult != null
            && HandPointerFilterTargetResolver.resolve(level, player, pos, hitResult).isPresent()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            FluidLogisticsPackets.getChannel().sendToServer(new HandPointerOpenFilterMenuPacket(
                pos, hitResult.getDirection(), hitResult.getLocation()));
            return;
        }

        if (LogisticsSelectionHandler.isLogisticsBlockEntity(blockEntity)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            Vec3 clickLocation = hitResult != null ? hitResult.getLocation() : null;
            if (player.isShiftKeyDown()) {
                if (LogisticsSelectionHandler.isFactoryPanel(blockEntity)
                    && LogisticsLinkResolver.resolve(level, pos, state, clickLocation).isEmpty()) {
                    playDenySound(level, pos);
                    sendStatus(player, "fluidlogistics.hand_pointer.logistics.no_panel_at_slot", STATUS_INVALID_COLOR);
                    return;
                }
                HandPointerAuthorizeLogisticsNetworkPacket.send(pos, getTargetedPanelSlot(pos, state, clickLocation));
                return;
            }
            if (HandPointerModeManager.tryEnterMode(HandPointerModeManager.SelectionMode.LOGISTICS)) {
                LogisticsSelectionHandler.handleNetworkClick(blockEntity, pos, player, level, state, clickLocation);
            }
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

    private static boolean shouldHandleRightClick(Level level, Player player, BlockPos pos, BlockHitResult hitResult,
        BlockState state, BlockEntity blockEntity,
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

        if (resolvesFilterSlot(level, player, pos, hitResult)) {
            return true;
        }

        if (blockEntity instanceof ArmBlockEntity
            || blockEntity instanceof EjectorBlockEntity
            || blockEntity instanceof ThresholdSwitchBlockEntity
            || isDisplayBoard(level, pos, state)
            || FrogportSelectionHandler.isFrogport(level, pos)
            || MailboxSelectionHandler.isMailbox(level, pos)
            || LogisticsSelectionHandler.isLogisticsBlockEntity(blockEntity)) {
            return true;
        }

        return blockEntity instanceof MechanicalFluidGunBlockEntity;
    }

    private static boolean resolvesFilterSlot(Level level, Player player, BlockPos pos, BlockHitResult hitResult) {
        if (player == null || player.isShiftKeyDown() || hitResult == null) {
            return false;
        }
        if (!hitResult.getBlockPos().equals(pos)) {
            return false;
        }

        return HandPointerFilterTargetResolver.resolve(level, player, pos, hitResult).isPresent();
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

            if (!MechanicalFluidGunSelectionHandler.isTargetCandidate(level, pos)) {
                HandPointerModeManager.exitMode(player, level);
                sendStatus(player, "fluidlogistics.hand_pointer.mode_exited", STATUS_NEUTRAL_COLOR);
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
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!PackagerTargetHelper.isToggleTarget(blockEntity, state)) {
            return false;
        }

        if (player.isShiftKeyDown() && PackagerTargetHelper.isClipboardAddressTarget(blockEntity, state)) {
            FluidLogisticsPackets.getChannel().sendToServer(new HandPointerClearClipboardAddressPacket(pos));
            playBlockSound(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 0.85f);
            return true;
        }

        FluidLogisticsPackets.getChannel().sendToServer(new HandPointerPackagerTogglePacket(pos));
        playBlockSound(level, pos, SoundEvents.LEVER_CLICK, 0.3f, 1.0f);

        boolean willBePowered = state.hasProperty(BlockStateProperties.POWERED)
            ? !state.getValue(BlockStateProperties.POWERED)
            : true;
        String key = packagerStatusKey(blockEntity, state, willBePowered);
        sendStatus(player, key, willBePowered ? STATUS_FORCED_ON_COLOR : STATUS_NEUTRAL_COLOR);
        return true;
    }

    private static String packagerStatusKey(BlockEntity blockEntity, BlockState state, boolean willBePowered) {
        String base;
        if (blockEntity instanceof FluidRepackagerBlockEntity) {
            base = "fluidlogistics.hand_pointer.fluid_repackager.";
        } else if (blockEntity instanceof RepackagerBlockEntity) {
            base = "fluidlogistics.hand_pointer.repackager.";
        } else if (com.yision.fluidlogistics.registry.AllBlocks.FLUID_PACKAGER.has(state)) {
            base = "fluidlogistics.hand_pointer.fluid_packager.";
        } else {
            base = "fluidlogistics.hand_pointer.packager.";
        }
        return base + (willBePowered ? "powered_on" : "powered_off");
    }

    private static boolean isDisplayBoard(Level level, BlockPos pos, BlockState state) {
        return !AllBlocks.DISPLAY_LINK.has(state) && DisplayTarget.get(level, pos) != null;
    }

    private static FactoryPanelBlock.PanelSlot getTargetedPanelSlot(BlockPos pos, BlockState state, Vec3 clickLocation) {
        return clickLocation == null
            ? FactoryPanelBlock.PanelSlot.BOTTOM_LEFT
            : FactoryPanelBlock.getTargetedSlot(pos, state, clickLocation);
    }
}
