package com.yision.fluidlogistics.network;

import com.yision.fluidlogistics.block.FluidHatch.FluidHatchFluidHandlerForwarder;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlock;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunItem;
import com.yision.fluidlogistics.block.MechanicalFluidGun.MechanicalFluidGunTargetConfig;
import com.yision.fluidlogistics.registry.AllBlocks;

import com.simibubi.create.content.fluids.FluidFX;
import java.util.ArrayList;
import java.util.List;

import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

public final class MechanicalFluidGunPackets {

    private MechanicalFluidGunPackets() {
    }

    public record TargetPacket(BlockPos gunPos, List<TargetEntry> targets,
                               boolean clearTarget) implements ServerboundPacketPayload {

        public static final int MAX_TARGETS = 64;

        public record TargetEntry(BlockPos pos, @Nullable Direction face) {
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, TargetPacket> STREAM_CODEC =
            StreamCodec.of(TargetPacket::encode, TargetPacket::decode);

        public static TargetPacket setTargets(BlockPos gunPos, List<TargetEntry> targets) {
            List<TargetEntry> cappedTargets = targets.size() > MAX_TARGETS
                ? List.copyOf(targets.subList(0, MAX_TARGETS))
                : List.copyOf(targets);
            return new TargetPacket(gunPos, cappedTargets, false);
        }

        public static TargetPacket clearTarget(BlockPos gunPos) {
            return new TargetPacket(gunPos, List.of(), true);
        }

        private static void encode(RegistryFriendlyByteBuf buf, TargetPacket pkt) {
            buf.writeBlockPos(pkt.gunPos);
            buf.writeBoolean(pkt.clearTarget);
            if (!pkt.clearTarget) {
                int count = Math.min(pkt.targets.size(), MAX_TARGETS);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    TargetEntry target = pkt.targets.get(i);
                    buf.writeBlockPos(target.pos);
                    buf.writeBoolean(target.face != null);
                    if (target.face != null) {
                        buf.writeVarInt(target.face.get3DDataValue());
                    }
                }
            }
        }

        private static TargetPacket decode(RegistryFriendlyByteBuf buf) {
            BlockPos gunPos = buf.readBlockPos();
            boolean clear = buf.readBoolean();
            if (clear) {
                return clearTarget(gunPos);
            }
            int targetCount = buf.readVarInt();
            if (targetCount < 0 || targetCount > MAX_TARGETS) {
                throw new RuntimeException("TargetPacket target count " + targetCount + " exceeds maximum " + MAX_TARGETS);
            }
            List<TargetEntry> targets = new ArrayList<>(targetCount);
            for (int i = 0; i < targetCount; i++) {
                BlockPos targetPos = buf.readBlockPos();
                Direction face = buf.readBoolean() ? Direction.from3DDataValue(buf.readVarInt()) : null;
                targets.add(new TargetEntry(targetPos, face));
            }
            return setTargets(gunPos, targets);
        }

        @Override
        public void handle(ServerPlayer player) {
            if (player == null) return;
            Level level = player.level();
            if (!level.isLoaded(gunPos)) return;
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
        }

        @Override
        public PacketTypeProvider getTypeProvider() {
            return FluidLogisticsPackets.MECHANICAL_FLUID_GUN_TARGET;
        }
    }

    public record ItemTargetSelectionPacket(BlockPos targetPos, @Nullable Direction face,
                                             boolean remove, boolean clearSelectedTargets,
                                             int selectedSlot) implements ServerboundPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, ItemTargetSelectionPacket> STREAM_CODEC =
            StreamCodec.of(ItemTargetSelectionPacket::encode, ItemTargetSelectionPacket::decode);

        public ItemTargetSelectionPacket(BlockPos targetPos, @Nullable Direction face, boolean remove) {
            this(targetPos, face, remove, false, -1);
        }

        public static ItemTargetSelectionPacket clearSelectedTargets(int selectedSlot) {
            return new ItemTargetSelectionPacket(BlockPos.ZERO, null, false, true, selectedSlot);
        }

        private static void encode(RegistryFriendlyByteBuf buf, ItemTargetSelectionPacket pkt) {
            buf.writeBoolean(pkt.clearSelectedTargets);
            if (pkt.clearSelectedTargets) {
                buf.writeVarInt(pkt.selectedSlot);
                return;
            }
            buf.writeBlockPos(pkt.targetPos);
            buf.writeBoolean(pkt.face != null);
            if (pkt.face != null) {
                buf.writeVarInt(pkt.face.get3DDataValue());
            }
            buf.writeBoolean(pkt.remove);
        }

        private static ItemTargetSelectionPacket decode(RegistryFriendlyByteBuf buf) {
            boolean clearSelectedTargets = buf.readBoolean();
            if (clearSelectedTargets) {
                return clearSelectedTargets(buf.readVarInt());
            }
            BlockPos targetPos = buf.readBlockPos();
            Direction face = buf.readBoolean() ? Direction.from3DDataValue(buf.readVarInt()) : null;
            boolean remove = buf.readBoolean();
            return new ItemTargetSelectionPacket(targetPos, face, remove);
        }

        @Override
        public void handle(ServerPlayer player) {
            if (player == null || player.isSpectator()) return;

            if (clearSelectedTargets) {
                if (selectedSlot < 0 || selectedSlot >= player.getInventory().getContainerSize()) return;
                ItemStack selectedStack = player.getInventory().getItem(selectedSlot);
                if (AllBlocks.MECHANICAL_FLUID_GUN.isIn(selectedStack)) {
                    MechanicalFluidGunItem.clearSelectedTargets(selectedStack);
                }
                return;
            }

            ItemStack mainHand = player.getMainHandItem();
            if (!AllBlocks.MECHANICAL_FLUID_GUN.isIn(mainHand)) return;

            Level level = player.level();
            if (!level.isLoaded(targetPos)) return;
            if (!player.mayInteract(level, targetPos)) return;
            if (player.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D) > 64.0D)
                return;
            if (!MechanicalFluidGunBlock.isTargetTagged(level, targetPos)) return;

            if (!remove) {
                int currentCount = MechanicalFluidGunItem.getSelectedTargets(mainHand, level).size();
                if (currentCount >= TargetPacket.MAX_TARGETS) return;
            }

            if (remove) {
                MechanicalFluidGunItem.removeSelectedTarget(mainHand, targetPos);
            } else {
                Direction normalizedFace = face;
                BlockState targetState = level.getBlockState(targetPos);
                Direction hatchSide = FluidHatchFluidHandlerForwarder.getExposedSide(targetState);
                if (hatchSide != null) {
                    normalizedFace = hatchSide;
                }
                MechanicalFluidGunItem.addSelectedTarget(mainHand, level, targetPos, normalizedFace);
            }
        }

        @Override
        public PacketTypeProvider getTypeProvider() {
            return FluidLogisticsPackets.MECHANICAL_FLUID_GUN_ITEM_TARGET_SELECTION;
        }
    }

    public record SprayParticlePacket(Vec3 target,
                                       FluidStack fluid) implements ClientboundPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, SprayParticlePacket> STREAM_CODEC =
            StreamCodec.of(SprayParticlePacket::encode, SprayParticlePacket::decode);

        private static void encode(RegistryFriendlyByteBuf buf, SprayParticlePacket pkt) {
            buf.writeDouble(pkt.target.x);
            buf.writeDouble(pkt.target.y);
            buf.writeDouble(pkt.target.z);
            FluidStack.STREAM_CODEC.encode(buf, pkt.fluid);
        }

        private static SprayParticlePacket decode(RegistryFriendlyByteBuf buf) {
            Vec3 target = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            FluidStack fluid = FluidStack.STREAM_CODEC.decode(buf);
            return new SprayParticlePacket(target, fluid);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void handle(LocalPlayer player) {
            if (fluid.isEmpty()) return;

            for (int i = 0; i < 4; i++) {
                double ox = (player.level().random.nextDouble() - 0.5) * 0.16;
                double oy = player.level().random.nextDouble() * 0.12;
                double oz = (player.level().random.nextDouble() - 0.5) * 0.16;

                player.level().addAlwaysVisibleParticle(
                    FluidFX.getFluidParticle(fluid),
                    true,
                    target.x + ox, target.y + oy, target.z + oz,
                    (player.level().random.nextDouble() - 0.5) * 0.03,
                    player.level().random.nextDouble() * 0.035 + 0.015,
                    (player.level().random.nextDouble() - 0.5) * 0.03
                );
            }
        }

        @Override
        public PacketTypeProvider getTypeProvider() {
            return FluidLogisticsPackets.MECHANICAL_FLUID_GUN_SPRAY_PARTICLE;
        }
    }
}
