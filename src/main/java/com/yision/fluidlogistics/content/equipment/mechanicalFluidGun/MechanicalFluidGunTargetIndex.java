package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class MechanicalFluidGunTargetIndex {

	private static final Map<LevelAccessor, Map<BlockPos, Set<BlockPos>>> INDEX =
		Collections.synchronizedMap(new WeakHashMap<>());

	private MechanicalFluidGunTargetIndex() {}

	static void update(LevelAccessor level, BlockPos gunPos, Set<BlockPos> oldTargets, Set<BlockPos> newTargets) {
		if (oldTargets.isEmpty() && newTargets.isEmpty())
			return;

		Map<BlockPos, Set<BlockPos>> byTarget = INDEX.computeIfAbsent(level, l -> new HashMap<>());

		for (BlockPos removed : oldTargets) {
			if (newTargets.contains(removed))
				continue;
			Set<BlockPos> guns = byTarget.get(removed);
			if (guns == null)
				continue;
			guns.remove(gunPos);
			if (guns.isEmpty())
				byTarget.remove(removed);
		}

		for (BlockPos added : newTargets) {
			if (oldTargets.contains(added))
				continue;
			byTarget.computeIfAbsent(added, p -> new HashSet<>()).add(gunPos.immutable());
		}

		if (byTarget.isEmpty())
			INDEX.remove(level);
	}

	static List<BlockPos> getGunsTargeting(LevelAccessor level, BlockPos targetPos) {
		Map<BlockPos, Set<BlockPos>> byTarget = INDEX.get(level);
		if (byTarget == null)
			return List.of();
		Set<BlockPos> guns = byTarget.get(targetPos);
		if (guns == null || guns.isEmpty())
			return List.of();
		return new ArrayList<>(guns);
	}
}
