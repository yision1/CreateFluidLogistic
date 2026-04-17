package com.yision.fluidlogistics.mixin.logistics;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlock;
import com.yision.fluidlogistics.block.FluidPackager.FluidPackagerBlockEntity;
import com.yision.fluidlogistics.goggle.PackagerGoggleInfo;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FluidPackagerBlockEntity.class)
public class FluidPackagerBlockEntityMixin implements IHaveGoggleInformation {

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        FluidPackagerBlockEntity packager = (FluidPackagerBlockEntity) (Object) this;
        Level level = packager.getLevel();

        String address = packager.signBasedAddress;
        if (level != null && level.isClientSide) {
            String scannedAddress = fluidlogistics$findSignAddress(packager);
            if (!scannedAddress.isBlank()) {
                address = scannedAddress;
            } else if (address.isBlank()) {
                address = packager.fluidlogistics$getClipboardAddress();
            }
        } else if (address.isBlank()) {
            address = packager.fluidlogistics$getClipboardAddress();
        }

        BlockState state = packager.getBlockState();
        boolean isLinkedToNetwork = state.hasProperty(FluidPackagerBlock.LINKED) && state.getValue(FluidPackagerBlock.LINKED);
        PackagerGoggleInfo.addFluidPackagerToTooltip(tooltip, address, packager.fluidlogistics$isManualOverrideLocked(), isLinkedToNetwork);
        return true;
    }

    @Unique
    private static String fluidlogistics$findSignAddress(FluidPackagerBlockEntity packager) {
        for (Direction side : Iterate.directions) {
            String address = fluidlogistics$readSignAddress(packager, side);
            if (!address.isBlank()) {
                return address;
            }
        }
        return "";
    }

    @Unique
    private static String fluidlogistics$readSignAddress(FluidPackagerBlockEntity packager, Direction side) {
        Level level = packager.getLevel();
        if (level == null) {
            return "";
        }

        BlockEntity blockEntity = level.getBlockEntity(packager.getBlockPos().relative(side));
        if (!(blockEntity instanceof SignBlockEntity sign)) {
            return "";
        }

        for (boolean front : Iterate.trueAndFalse) {
            SignText text = sign.getText(front);
            StringBuilder address = new StringBuilder();
            for (Component component : text.getMessages(false)) {
                String string = component.getString();
                if (!string.isBlank()) {
                    address.append(string.trim()).append(' ');
                }
            }
            if (address.length() > 0) {
                return address.toString().trim();
            }
        }

        return "";
    }
}
