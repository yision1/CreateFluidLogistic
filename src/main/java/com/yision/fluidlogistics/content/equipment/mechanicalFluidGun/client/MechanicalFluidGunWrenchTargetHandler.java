package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.client;

import com.simibubi.create.AllItems;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunTargetConfig;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = FluidLogistics.MODID, value = Dist.CLIENT)
public final class MechanicalFluidGunWrenchTargetHandler {

	private static final int TARGET_COLOR = 0xDDC166;
	private static final float LINE_WIDTH = 1 / 16f;
	private static final String OUTLINE_PREFIX = "MFGWrenchTarget_";

	private static BlockPos hoveredGunPos;
	private static final Set<String> activeOutlineKeys = new HashSet<>();

	private MechanicalFluidGunWrenchTargetHandler() {
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		Level level = mc.level;

		if (player == null || level == null || !AllItems.WRENCH.isIn(player.getMainHandItem())) {
			hoveredGunPos = null;
			return;
		}

		if (!(mc.hitResult instanceof BlockHitResult hit)) {
			hoveredGunPos = null;
			return;
		}

		BlockPos gunPos = hit.getBlockPos();
		if (!(level.getBlockEntity(gunPos) instanceof MechanicalFluidGunBlockEntity gun)) {
			hoveredGunPos = null;
			return;
		}

		if (!gunPos.equals(hoveredGunPos)) {
			hoveredGunPos = gunPos.immutable();
		}

		renderTargets(level, gunPos, gun);
	}

	public static void renderTargets(Level level, BlockPos gunPos, MechanicalFluidGunBlockEntity gun) {
		List<MechanicalFluidGunTargetConfig> targets = gun.getTargets();
		Set<String> nextKeys = new HashSet<>();

		for (MechanicalFluidGunTargetConfig target : targets) {
			BlockPos targetPos = target.absoluteFrom(gunPos);
			if (!MechanicalFluidGunBlock.isSelectableTarget(level, gunPos, targetPos)) {
				continue;
			}

			BlockState state = level.getBlockState(targetPos);
			VoxelShape shape = state.getShape(level, targetPos);
			if (shape.isEmpty()) {
				continue;
			}

			String key = outlineKey(gunPos, targetPos);
			nextKeys.add(key);
			Outliner.getInstance()
				.showAABB(key, shape.bounds().move(targetPos))
				.colored(TARGET_COLOR)
				.lineWidth(LINE_WIDTH);
		}

		for (String key : activeOutlineKeys) {
			if (!nextKeys.contains(key)) {
				Outliner.getInstance().remove(key);
			}
		}
		activeOutlineKeys.clear();
		activeOutlineKeys.addAll(nextKeys);
	}

	public static void clear() {
		hoveredGunPos = null;
		for (String key : activeOutlineKeys) {
			Outliner.getInstance().remove(key);
		}
		activeOutlineKeys.clear();
	}

	private static String outlineKey(BlockPos gunPos, BlockPos targetPos) {
		return OUTLINE_PREFIX + gunPos.asLong() + "_" + targetPos.asLong();
	}
}
