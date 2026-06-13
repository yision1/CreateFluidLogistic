package com.yision.fluidlogistics.client.handpointer;

import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunTargetConfig;
import com.yision.fluidlogistics.block.FluidHatch.FluidHatchFluidHandlerForwarder;
import com.yision.fluidlogistics.network.MechanicalFluidGunPackets;

import java.util.ArrayList;
import java.util.List;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class MechanicalFluidGunSelectionHandler {

	private static final String GUN_HIGHLIGHT = "HandPointerMechanicalFluidGunHighlight";
	private static final int HIGHLIGHT_COLOR = 0xDDC166;

	private static BlockPos selectedGunPos;
	private static final List<MechanicalFluidGunPackets.TargetPacket.TargetEntry> targets = new ArrayList<>();

	private MechanicalFluidGunSelectionHandler() {
	}

	public static void enterMode(Level level, BlockPos pos) {
		selectedGunPos = pos.immutable();
		targets.clear();

		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof MechanicalFluidGunBlockEntity gun && gun.hasTarget()) {
			for (MechanicalFluidGunTargetConfig target : gun.getTargets()) {
				targets.add(new MechanicalFluidGunPackets.TargetPacket.TargetEntry(target.absoluteFrom(pos).immutable(), target.face()));
			}
		}
	}

	public static boolean isSelectedGun(BlockPos pos) {
		return selectedGunPos != null && selectedGunPos.equals(pos);
	}

	public static boolean setTarget(Level level, BlockPos pos, @Nullable Direction face) {
		if (!isValidTarget(level, pos)) {
			return false;
		}

		for (int i = 0; i < targets.size(); i++) {
			if (targets.get(i).pos().equals(pos)) {
				clearTargetOutline(pos);
				targets.remove(i);
				return true;
			}
		}

		if (targets.size() >= MechanicalFluidGunPackets.TargetPacket.MAX_TARGETS) {
			return false;
		}

		targets.add(new MechanicalFluidGunPackets.TargetPacket.TargetEntry(pos.immutable(), getTargetFace(level, pos, face)));
		return true;
	}

	public static boolean isTargetSelected(BlockPos pos) {
		for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
			if (target.pos().equals(pos)) {
				return true;
			}
		}
		return false;
	}

	public static boolean submit() {
		if (selectedGunPos == null || targets.isEmpty()) {
			return false;
		}

		PacketDistributor.sendToServer(MechanicalFluidGunPackets.TargetPacket.setTargets(selectedGunPos, List.copyOf(targets)));
		return true;
	}

	public static boolean clearTarget() {
		if (selectedGunPos == null) {
			return false;
		}

		PacketDistributor.sendToServer(MechanicalFluidGunPackets.TargetPacket.clearTarget(selectedGunPos));
		clearHoverPreview();
		targets.clear();
		return true;
	}

	public static int getTargetCount() {
		return targets.size();
	}

	public static void clearSelection() {
		selectedGunPos = null;
		clearHoverPreview();
		targets.clear();
		Outliner.getInstance().remove(GUN_HIGHLIGHT);
	}

	public static void clearHoverPreview() {
		clearTargetOutlines();
	}

	public static void renderSelection(Minecraft mc) {
		if (mc.level == null || selectedGunPos == null) {
			clearSelection();
			return;
		}

		BlockState gunState = mc.level.getBlockState(selectedGunPos);
		VoxelShape gunShape = gunState.getShape(mc.level, selectedGunPos);
		if (!gunShape.isEmpty()) {
			Outliner.getInstance()
				.showAABB(GUN_HIGHLIGHT, gunShape.bounds().move(selectedGunPos))
				.colored(HIGHLIGHT_COLOR)
				.lineWidth(0.0625F);
		}

		for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
			if (!isValidTarget(mc.level, target.pos())) {
				continue;
			}

			BlockState state = mc.level.getBlockState(target.pos());
			VoxelShape shape = state.getShape(mc.level, target.pos());
			if (shape.isEmpty()) {
				continue;
			}

			Outliner.getInstance()
				.showAABB(targetSlot(target.pos()), shape.bounds().move(target.pos()))
				.colored(HIGHLIGHT_COLOR)
				.lineWidth(0.0625F);
		}
	}

	private static boolean isValidTarget(Level level, BlockPos pos) {
		if (selectedGunPos == null || pos == null) {
			return false;
		}
		return MechanicalFluidGunBlock.isSelectableTarget(level, selectedGunPos, pos);
	}

	private static @Nullable Direction getTargetFace(Level level, BlockPos pos, @Nullable Direction clickedFace) {
		Direction hatchSide = FluidHatchFluidHandlerForwarder.getExposedSide(level.getBlockState(pos));
		return hatchSide == null ? clickedFace : hatchSide;
	}

	private static String targetSlot(BlockPos pos) {
		return "HandPointerMechanicalFluidGunTarget_" + pos.asLong();
	}

	private static void clearTargetOutline(BlockPos pos) {
		Outliner.getInstance().remove(targetSlot(pos));
	}

	private static void clearTargetOutlines() {
		for (MechanicalFluidGunPackets.TargetPacket.TargetEntry target : targets) {
			clearTargetOutline(target.pos());
		}
	}
}
