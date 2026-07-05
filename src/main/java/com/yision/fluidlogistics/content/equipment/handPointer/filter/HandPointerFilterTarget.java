package com.yision.fluidlogistics.content.equipment.handPointer.filter;

import java.util.Optional;

import com.simibubi.create.foundation.utility.IInteractionChecker;
import com.yision.fluidlogistics.content.equipment.handPointer.HandPointerItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class HandPointerFilterTarget implements MenuProvider, IInteractionChecker {

    private final BlockPos pos;
    private final Direction side;
    private final Vec3 hitLocation;
    private final ItemStack iconStack;

    public HandPointerFilterTarget(BlockPos pos, Direction side, Vec3 hitLocation, ItemStack iconStack) {
        this.pos = pos;
        this.side = side;
        this.hitLocation = hitLocation;
        this.iconStack = iconStack;
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction side() {
        return side;
    }

    public Vec3 hitLocation() {
        return hitLocation;
    }

    public ItemStack iconStack() {
        return iconStack;
    }

    @Override
    public Component getDisplayName() {
        return iconStack.getHoverName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return HandPointerFilterMenu.create(containerId, playerInventory, this);
    }

    @Override
    public boolean canPlayerUse(Player player) {
        if (player.isSpectator()) {
            return false;
        }

        Level playerLevel = player.level();
        if (!playerLevel.isLoaded(pos)) {
            return false;
        }

        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return false;
        }

        if (!(player.getMainHandItem().getItem() instanceof HandPointerItem)) {
            return false;
        }

        Optional<HandPointerFilterTarget> current =
            HandPointerFilterTargetResolver.resolve(playerLevel, player, pos, buildHitResult(playerLevel));
        return current.isPresent();
    }

    private BlockHitResult buildHitResult(Level level) {
        return new BlockHitResult(hitLocation, side, pos, false);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(side);
        buf.writeDouble(hitLocation.x);
        buf.writeDouble(hitLocation.y);
        buf.writeDouble(hitLocation.z);
        buf.writeItem(iconStack);
    }

    public static HandPointerFilterTarget decodeClient(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction side = buf.readEnum(Direction.class);
        Vec3 hitLocation = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        ItemStack iconStack = buf.readItem();
        return new HandPointerFilterTarget(pos, side, hitLocation, iconStack);
    }
}
