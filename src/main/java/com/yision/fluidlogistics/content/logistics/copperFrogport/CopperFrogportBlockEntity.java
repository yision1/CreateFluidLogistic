package com.yision.fluidlogistics.content.logistics.copperFrogport;

import com.simibubi.create.compat.Mods;
import com.simibubi.create.content.logistics.packagePort.PackagePortMenu;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import com.yision.fluidlogistics.registry.AllMenuTypes;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

public class CopperFrogportBlockEntity extends FrogportBlockEntity {

    public CopperFrogportBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            AllBlockEntities.COPPER_FROGPORT.get(),
            (be, context) -> be.itemHandler
        );

        if (Mods.COMPUTERCRAFT.isLoaded()) {
            event.registerBlockEntity(
                PeripheralCapability.get(),
                AllBlockEntities.COPPER_FROGPORT.get(),
                (be, context) -> be.computerBehaviour.getPeripheralCapability()
            );
        }
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PackagePortMenu(AllMenuTypes.COPPER_FROGPORT.get(), containerId, playerInventory, this);
    }

    @Override
    protected IItemHandler getAdjacentInventory(Direction side) {
        Direction attachedDirection = CopperFrogportBlock.getAttachedDirection(getBlockState());
        return super.getAdjacentInventory(
            CopperFrogportRules.inventorySide(side, attachedDirection)
        );
    }

    @Override
    public AABB getRenderBoundingBox() {
        Direction outward = CopperFrogportBlock.getAttachedDirection(getBlockState()).getOpposite();
        AABB bounds = new AABB(worldPosition).expandTowards(
            outward.getStepX(),
            outward.getStepY(),
            outward.getStepZ()
        );
        if (target != null) {
            Vec3 location = target.getExactTargetLocation(this, level, worldPosition);
            if (!Vec3.ZERO.equals(location)) {
                bounds = bounds.minmax(new AABB(BlockPos.containing(location))).inflate(0.5);
            }
        }
        return bounds;
    }
}
