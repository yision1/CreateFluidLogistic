package com.yision.fluidlogistics.mixin.client;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import com.simibubi.create.AllPackets;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemMenu;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSetItemScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;

import net.createmod.catnip.data.Pair;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.SlotItemHandler;

@Mixin(value = FactoryPanelSetItemScreen.class, remap = false)
public abstract class FactoryPanelSetItemScreenMixin extends AbstractSimiContainerScreen<FactoryPanelSetItemMenu> {

    public FactoryPanelSetItemScreenMixin(FactoryPanelSetItemMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot instanceof SlotItemHandler) {
            int slotIndex = slot.getSlotIndex();
            ItemStack carried = this.menu.getCarried();

            if (!carried.isEmpty() && hasAltDown()) {
                FactoryPanelBehaviour behaviour = menu.contentHolder;
                if (behaviour != null && GenericItemEmptying.canItemBeEmptied(behaviour.getWorld(), carried)) {
                    Pair<FluidStack, ItemStack> emptyResult = GenericItemEmptying.emptyItem(behaviour.getWorld(),
                        carried, true);
                    FluidStack fluidStack = emptyResult.getFirst();
                    if (!fluidStack.isEmpty()) {
                        ItemStack virtualTank = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
                        FluidStack template = fluidStack.copy();
                        template.setAmount(1);
                        CompressedTankItem.setFluidVirtual(virtualTank, template);
                        menu.ghostInventory.setStackInSlot(slotIndex, virtualTank);
                        AllPackets.getChannel().sendToServer(new GhostItemSubmitPacket(virtualTank, slotIndex));
                        return;
                    }
                }
            }
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }
}
