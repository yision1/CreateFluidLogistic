package com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.network;

import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.particle.MechanicalFluidGunStreamParticleData;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.content.fluids.fluidHatch.FluidHatchFluidHandlerForwarder;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunItem;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunTargetConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class MechanicalFluidGunPackets {

	private MechanicalFluidGunPackets() {
	}

	public static class TargetPacket extends SimplePacketBase {
		public static final int MAX_TARGETS = 64;

		private final BlockPos gunPos;
		private final List<TargetEntry> targets;
		private final boolean clearTarget;

		public record TargetEntry(BlockPos pos, @Nullable Direction face) {
		}

		public TargetPacket(BlockPos gunPos, List<TargetEntry> targets, boolean clearTarget) {
			this.gunPos = gunPos;
			List<TargetEntry> capped = targets.size() > MAX_TARGETS
				? List.copyOf(targets.subList(0, MAX_TARGETS))
				: List.copyOf(targets);
			this.targets = capped;
			this.clearTarget = clearTarget;
		}

		public TargetPacket(FriendlyByteBuf buf) {
			gunPos = buf.readBlockPos();
			clearTarget = buf.readBoolean();
			if (clearTarget) {
				targets = List.of();
			} else {
				int count = buf.readVarInt();
				if (count < 0 || count > MAX_TARGETS) {
					throw new RuntimeException("TargetPacket target count " + count + " exceeds maximum " + MAX_TARGETS);
				}
				targets = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					BlockPos targetPos = buf.readBlockPos();
					Direction face = buf.readBoolean() ? Direction.from3DDataValue(buf.readVarInt()) : null;
					targets.add(new TargetEntry(targetPos, face));
				}
			}
		}

		public static TargetPacket setTargets(BlockPos gunPos, List<TargetEntry> targets) {
			return new TargetPacket(gunPos, targets, false);
		}

		public static TargetPacket clearTarget(BlockPos gunPos) {
			return new TargetPacket(gunPos, List.of(), true);
		}

		@Override
		public void write(FriendlyByteBuf buf) {
			buf.writeBlockPos(gunPos);
			buf.writeBoolean(clearTarget);
			if (!clearTarget) {
				int count = Math.min(targets.size(), MAX_TARGETS);
				buf.writeVarInt(count);
				for (int i = 0; i < count; i++) {
					TargetEntry target = targets.get(i);
					buf.writeBlockPos(target.pos);
					buf.writeBoolean(target.face != null);
					if (target.face != null) {
						buf.writeVarInt(target.face.get3DDataValue());
					}
				}
			}
		}

		@Override
		public boolean handle(Context context) {
			context.enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				if (player == null) return;
				Level level = player.getCommandSenderWorld();
				if (!level.isLoaded(gunPos)) return;
				if (player.distanceToSqr(gunPos.getX() + 0.5D, gunPos.getY() + 0.5D, gunPos.getZ() + 0.5D) > 64.0D) return;
				if (!player.mayInteract(level, gunPos)) return;

				BlockEntity be = level.getBlockEntity(gunPos);
				if (!(be instanceof MechanicalFluidGunBlockEntity gunBe)) return;

				if (clearTarget) {
					gunBe.clearTarget();
					return;
				}

				List<MechanicalFluidGunTargetConfig> validatedTargets = new ArrayList<>();
				for (TargetEntry target : targets) {
					if (validatedTargets.size() >= MAX_TARGETS) break;
					if (!MechanicalFluidGunBlock.isSelectableTarget(level, gunPos, target.pos)) continue;
					validatedTargets.add(MechanicalFluidGunTargetConfig.fromAbsolute(gunPos, target.pos, target.face));
				}
				if (!validatedTargets.isEmpty()) {
					gunBe.setTargets(validatedTargets);
				}
			});
			return true;
		}
	}

	public static class ItemTargetSelectionPacket extends SimplePacketBase {
		private final BlockPos targetPos;
		private final boolean hasFace;
		private final int faceData;
		private final boolean remove;
		private final boolean clearSelectedTargets;
		private final int selectedSlot;

		public ItemTargetSelectionPacket(BlockPos targetPos, @Nullable Direction face, boolean remove) {
			this.targetPos = targetPos.immutable();
			this.hasFace = face != null;
			this.faceData = face != null ? face.get3DDataValue() : 0;
			this.remove = remove;
			this.clearSelectedTargets = false;
			this.selectedSlot = -1;
		}

		private ItemTargetSelectionPacket(int selectedSlot) {
			this.targetPos = BlockPos.ZERO;
			this.hasFace = false;
			this.faceData = 0;
			this.remove = false;
			this.clearSelectedTargets = true;
			this.selectedSlot = selectedSlot;
		}

		public static ItemTargetSelectionPacket clearSelectedTargets(int selectedSlot) {
			return new ItemTargetSelectionPacket(selectedSlot);
		}

		public ItemTargetSelectionPacket(FriendlyByteBuf buf) {
			clearSelectedTargets = buf.readBoolean();
			if (clearSelectedTargets) {
				selectedSlot = buf.readVarInt();
				targetPos = BlockPos.ZERO;
				hasFace = false;
				faceData = 0;
				remove = false;
			} else {
				targetPos = buf.readBlockPos();
				hasFace = buf.readBoolean();
				faceData = hasFace ? buf.readVarInt() : 0;
				remove = buf.readBoolean();
				selectedSlot = -1;
			}
		}

		@Override
		public void write(FriendlyByteBuf buf) {
			buf.writeBoolean(clearSelectedTargets);
			if (clearSelectedTargets) {
				buf.writeVarInt(selectedSlot);
				return;
			}
			buf.writeBlockPos(targetPos);
			buf.writeBoolean(hasFace);
			if (hasFace) {
				buf.writeVarInt(faceData);
			}
			buf.writeBoolean(remove);
		}

		@Override
		public boolean handle(Context context) {
			context.enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				if (player == null || player.isSpectator()) return;

				if (clearSelectedTargets) {
					if (selectedSlot < 0 || selectedSlot >= player.getInventory().getContainerSize()) return;
					ItemStack selectedStack = player.getInventory().getItem(selectedSlot);
					if (com.yision.fluidlogistics.registry.AllBlocks.MECHANICAL_FLUID_GUN.isIn(selectedStack)) {
						MechanicalFluidGunItem.clearSelectedTargets(selectedStack);
					}
					return;
				}

				if (!com.yision.fluidlogistics.registry.AllBlocks.MECHANICAL_FLUID_GUN.isIn(player.getMainHandItem())) return;

				Level level = player.getCommandSenderWorld();
				if (!level.isLoaded(targetPos)) return;
				if (player.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D) > 64.0D) return;
				if (!player.mayInteract(level, targetPos)) return;

				if (!MechanicalFluidGunBlock.isTargetTagged(level, targetPos)) return;

				ItemStack held = player.getMainHandItem();

				if (remove) {
					MechanicalFluidGunItem.removeSelectedTarget(held, targetPos);
				} else {
					if (MechanicalFluidGunItem.getSelectedTargets(held, level).size() >= TargetPacket.MAX_TARGETS) return;

					Direction face = hasFace ? Direction.from3DDataValue(faceData) : null;
					Direction hatchSide = FluidHatchFluidHandlerForwarder.getExposedSide(level.getBlockState(targetPos));
					if (hatchSide != null) {
						face = hatchSide;
					}
					MechanicalFluidGunItem.addSelectedTarget(held, level, targetPos, face);
				}
			});
			return true;
		}
	}

	public static class SprayParticlePacket extends SimplePacketBase {
		private final Vec3 target;
		private final FluidStack fluid;

		public SprayParticlePacket(Vec3 target, FluidStack fluid) {
			this.target = target;
			this.fluid = fluid;
		}

		public SprayParticlePacket(FriendlyByteBuf buf) {
			target = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
			fluid = buf.readFluidStack();
		}

		@Override
		public void write(FriendlyByteBuf buf) {
			buf.writeDouble(target.x);
			buf.writeDouble(target.y);
			buf.writeDouble(target.z);
			buf.writeFluidStack(fluid);
		}

		@Override
		public boolean handle(Context context) {
			context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::handleClient));
			return true;
		}

		private void handleClient() {
			Minecraft mc = Minecraft.getInstance();
			if (fluid.isEmpty() || mc.level == null) return;

			for (int i = 0; i < 4; i++) {
				double ox = (mc.level.random.nextDouble() - 0.5) * 0.16;
				double oy = mc.level.random.nextDouble() * 0.12;
				double oz = (mc.level.random.nextDouble() - 0.5) * 0.16;

				mc.level.addAlwaysVisibleParticle(
					new MechanicalFluidGunStreamParticleData(fluid),
					true,
					target.x + ox, target.y + oy, target.z + oz,
					(mc.level.random.nextDouble() - 0.5) * 0.03,
					mc.level.random.nextDouble() * 0.035 + 0.015,
					(mc.level.random.nextDouble() - 0.5) * 0.03
				);
			}
		}
	}
}
