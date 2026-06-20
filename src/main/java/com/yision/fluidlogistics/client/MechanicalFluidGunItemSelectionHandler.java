package com.yision.fluidlogistics.client;

import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.block.FluidHatch.FluidHatchFluidHandlerForwarder;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunItem;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunItem.SelectedTargetEntry;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.network.MechanicalFluidGunPackets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT)
public class MechanicalFluidGunItemSelectionHandler {

    private static final String TARGET_OUTLINE = "MechanicalFluidGunItemTarget";
    private static final int TARGET_COLOR = 0xDDC166;
    private static final int NO_SELECTED_SLOT = -1;

    private static ItemStack currentItem;
    private static int currentSlot = NO_SELECTED_SLOT;
    private static final List<TargetSelection> selectedTargets = new ArrayList<>();

    private record TargetSelection(BlockPos pos, @Nullable Direction face) {
    }

    private MechanicalFluidGunItemSelectionHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClickingBlocksSelectsTarget(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        if (player == null || player.isSpectator() || !isHoldingGun(player)) {
            return;
        }
        if (player.isShiftKeyDown()) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!MechanicalFluidGunBlock.isTargetTagged(level, pos)) {
            return;
        }

        Direction face = getTargetFace(level, pos, event.getFace());

        Iterator<TargetSelection> iterator = selectedTargets.iterator();
        while (iterator.hasNext()) {
            TargetSelection selectedTarget = iterator.next();
            if (selectedTarget.pos().equals(pos)) {
                iterator.remove();
                Outliner.getInstance().remove(targetOutline(pos));
                FluidLogisticsPackets.getChannel()
                    .sendToServer(new MechanicalFluidGunPackets.ItemTargetSelectionPacket(pos, face, true));
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }

        if (selectedTargets.size() >= MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS) {
            CreateLang.builder()
                .translate("fluidlogistics.mechanical_fluid_gun.target_limit_reached", MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS)
                .style(ChatFormatting.RED)
                .sendStatus(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        selectedTargets.add(new TargetSelection(pos.immutable(), face));
        sendTargetStatus(player, level.getBlockState(pos));

        FluidLogisticsPackets.getChannel()
            .sendToServer(new MechanicalFluidGunPackets.ItemTargetSelectionPacket(pos, face, false));

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void leftClickingBlocksClearsTarget(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || selectedTargets.isEmpty() || !isHoldingGun(event.getEntity())) {
            return;
        }
        Iterator<TargetSelection> iterator = selectedTargets.iterator();
        while (iterator.hasNext()) {
            TargetSelection target = iterator.next();
            if (target.pos().equals(event.getPos())) {
                iterator.remove();
                Outliner.getInstance().remove(targetOutline(event.getPos()));
                FluidLogisticsPackets.getChannel()
                    .sendToServer(new MechanicalFluidGunPackets.ItemTargetSelectionPacket(
                        event.getPos(), target.face(), true));
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            currentItem = null;
            currentSlot = NO_SELECTED_SLOT;
            clearTargets();
            return;
        }

        if (!isHoldingGun(player)) {
            clearCurrentItemSelection();
            currentItem = null;
            currentSlot = NO_SELECTED_SLOT;
            clearTargets();
            return;
        }

        ItemStack held = player.getMainHandItem();
        int heldSlot = player.getInventory().selected;
        if (held != currentItem) {
            if (currentSlot != NO_SELECTED_SLOT && heldSlot != currentSlot) {
                clearCurrentItemSelection();
            }
            currentItem = held;
            currentSlot = heldSlot;
            // Rebuild local cache from item NBT instead of unconditionally clearing
            rebuildTargetsFromNBT(held);
            return;
        }

        drawTargetOutlines();
    }

    public static void flushTarget(BlockPos gunPos) {
        if (selectedTargets.isEmpty()) {
            return;
        }

        Level level = Minecraft.getInstance().level;
        List<MechanicalFluidGunPackets.TargetPacket.TargetEntry> packetTargets = new ArrayList<>(selectedTargets.size());
        int skippedOutOfRange = 0;
        for (TargetSelection selection : selectedTargets) {
            if (packetTargets.size() >= MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS) {
                break;
            }
            BlockPos targetPos = selection.pos();
            if (level == null || !MechanicalFluidGunBlock.isTargetTagged(level, targetPos)) {
                continue;
            }
            if (!MechanicalFluidGunBlock.isTargetInRange(gunPos, targetPos)) {
                skippedOutOfRange++;
                continue;
            }
            packetTargets.add(new MechanicalFluidGunPackets.TargetPacket.TargetEntry(targetPos, selection.face()));
        }
        if (!packetTargets.isEmpty()) {
            FluidLogisticsPackets.getChannel()
                .sendToServer(MechanicalFluidGunPackets.TargetPacket.setTargets(gunPos, packetTargets));
        }
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            if (skippedOutOfRange > 0) {
                CreateLang.builder()
                    .translate("fluidlogistics.mechanical_fluid_gun.targets_out_of_range", skippedOutOfRange)
                    .style(ChatFormatting.RED)
                    .sendStatus(player);
            } else if (!packetTargets.isEmpty()) {
                CreateLang.builder()
                    .translate("fluidlogistics.mechanical_fluid_gun.target_summary", packetTargets.size())
                    .style(ChatFormatting.WHITE)
                    .sendStatus(player);
            }
        }
        clearTargets();
        currentItem = null;
        currentSlot = NO_SELECTED_SLOT;
    }

    private static boolean isHoldingGun(Player player) {
        return com.yision.fluidlogistics.registry.AllBlocks.MECHANICAL_FLUID_GUN.isIn(player.getMainHandItem());
    }

    private static Direction getTargetFace(Level level, BlockPos pos, Direction clickedFace) {
        Direction hatchSide = FluidHatchFluidHandlerForwarder.getExposedSide(level.getBlockState(pos));
        return hatchSide == null ? clickedFace : hatchSide;
    }

    private static void drawTargetOutlines() {
        if (selectedTargets.isEmpty()) {
            return;
        }

        Iterator<TargetSelection> iterator = selectedTargets.iterator();
        while (iterator.hasNext()) {
            TargetSelection selectedTarget = iterator.next();
            Level level = Minecraft.getInstance().level;
            if (level == null || !MechanicalFluidGunBlock.isTargetTagged(level, selectedTarget.pos())) {
                iterator.remove();
                continue;
            }

            BlockPos pos = selectedTarget.pos();
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }

            Outliner.getInstance()
                .showAABB(targetOutline(pos), shape.bounds().move(pos))
                .colored(TARGET_COLOR)
                .lineWidth(1 / 16f);
        }
    }

    private static void clearTargets() {
        for (TargetSelection selectedTarget : selectedTargets) {
            Outliner.getInstance().remove(targetOutline(selectedTarget.pos()));
        }
        selectedTargets.clear();
    }

    private static void clearCurrentItemSelection() {
        if (currentItem == null || selectedTargets.isEmpty() || currentSlot == NO_SELECTED_SLOT) {
            return;
        }

        FluidLogisticsPackets.getChannel()
            .sendToServer(MechanicalFluidGunPackets.ItemTargetSelectionPacket.clearSelectedTargets(currentSlot));
    }

    private static void rebuildTargetsFromNBT(ItemStack stack) {
        selectedTargets.clear();
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        List<SelectedTargetEntry> nbtTargets = MechanicalFluidGunItem.getSelectedTargets(stack, level);
        for (SelectedTargetEntry entry : nbtTargets) {
            selectedTargets.add(new TargetSelection(entry.pos(), entry.face()));
        }
    }

    private static String targetOutline(BlockPos pos) {
        return TARGET_OUTLINE + "_" + pos.asLong();
    }

    public static void sendTargetStatus(Player player, BlockState state) {
        String key = MechanicalFluidGunBlock.targetsItemOn(state)
            ? "fluidlogistics.mechanical_fluid_gun.inject_item_target"
            : "fluidlogistics.mechanical_fluid_gun.inject_block_target";
        CreateLang.builder()
            .translate(key, CreateLang.blockName(state).style(ChatFormatting.WHITE))
            .color(TARGET_COLOR)
            .sendStatus(player);
    }
}
