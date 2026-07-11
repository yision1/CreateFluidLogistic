package com.yision.fluidlogistics.content.logistics.copperFrogport;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.PackagePortTargetType;
import com.yision.fluidlogistics.registry.FluidLogisticsPackagePortTargetTypes;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class CopperChainConveyorFrogportTarget extends PackagePortTarget.ChainConveyorFrogportTarget {

    public static final MapCodec<CopperChainConveyorFrogportTarget> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockPos.CODEC.fieldOf("relative_pos").forGetter(target -> target.relativePos),
            Codec.FLOAT.fieldOf("chain_pos").forGetter(target -> target.chainPos),
            BlockPos.CODEC.optionalFieldOf("connection")
                .forGetter(target -> Optional.ofNullable(target.connection)),
            Codec.BOOL.fieldOf("flipped").forGetter(target -> target.flipped)
        ).apply(instance, CopperChainConveyorFrogportTarget::new));

    public static final StreamCodec<ByteBuf, CopperChainConveyorFrogportTarget> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, target -> target.relativePos,
            ByteBufCodecs.FLOAT, target -> target.chainPos,
            CatnipStreamCodecBuilders.nullable(BlockPos.STREAM_CODEC), target -> target.connection,
            ByteBufCodecs.BOOL, target -> target.flipped,
            CopperChainConveyorFrogportTarget::new
        );

    public CopperChainConveyorFrogportTarget(BlockPos relativePos, float chainPos,
                                              Optional<BlockPos> connection, boolean flipped) {
        this(relativePos, chainPos, connection.orElse(null), flipped);
    }

    public CopperChainConveyorFrogportTarget(BlockPos relativePos, float chainPos,
                                              @Nullable BlockPos connection, boolean flipped) {
        super(relativePos, chainPos, connection, flipped);
    }

    @Override
    public boolean canSupport(BlockEntity blockEntity) {
        if (!(blockEntity instanceof CopperFrogportBlockEntity frogport) || frogport.getLevel() == null) {
            return false;
        }

        Vec3 targetLocation = getExactTargetLocation(
            frogport,
            frogport.getLevel(),
            frogport.getBlockPos()
        );
        return !Vec3.ZERO.equals(targetLocation)
            && CopperFrogportRules.isChainHeightValid(
                CopperFrogportBlock.getAttachedDirection(frogport.getBlockState()),
                targetLocation.y,
                Vec3.atBottomCenterOf(frogport.getBlockPos()).y
            );
    }

    @Override
    protected PackagePortTargetType getType() {
        return FluidLogisticsPackagePortTargetTypes.COPPER_CHAIN_CONVEYOR.get();
    }

    public static class Type implements PackagePortTargetType {

        @Override
        public MapCodec<CopperChainConveyorFrogportTarget> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, CopperChainConveyorFrogportTarget> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
