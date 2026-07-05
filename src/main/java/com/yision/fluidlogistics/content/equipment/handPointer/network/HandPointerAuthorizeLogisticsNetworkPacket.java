package com.yision.fluidlogistics.content.equipment.handPointer.network;

import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.content.equipment.handPointer.HandPointerItem;
import com.yision.fluidlogistics.content.equipment.handPointer.logistics.LogisticsLinkResolver;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent.Context;

public class HandPointerAuthorizeLogisticsNetworkPacket extends SimplePacketBase {

    private static final int STATUS_SUCCESS_COLOR = 0x9EF173;
    private static final int STATUS_INVALID_COLOR = 0xFF6171;
    private static final int STATUS_NEUTRAL_COLOR = 0xA5A5A5;

    private final BlockPos sourcePos;
    private final FactoryPanelBlock.PanelSlot panelSlot;

    public HandPointerAuthorizeLogisticsNetworkPacket(BlockPos sourcePos, FactoryPanelBlock.PanelSlot panelSlot) {
        this.sourcePos = sourcePos;
        this.panelSlot = panelSlot;
    }

    public HandPointerAuthorizeLogisticsNetworkPacket(FriendlyByteBuf buffer) {
        this.sourcePos = buffer.readBlockPos();
        this.panelSlot = readPanelSlot(buffer);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(sourcePos);
        writePanelSlot(buffer, panelSlot);
    }

    // FactoryPanelBlock.PanelSlot has no StreamCodec in 1.20.1, so encode the enum by ordinal.
    static void writePanelSlot(FriendlyByteBuf buffer, FactoryPanelBlock.PanelSlot slot) {
        buffer.writeVarInt((slot == null ? FactoryPanelBlock.PanelSlot.BOTTOM_LEFT : slot).ordinal());
    }

    static FactoryPanelBlock.PanelSlot readPanelSlot(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        FactoryPanelBlock.PanelSlot[] values = FactoryPanelBlock.PanelSlot.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return FactoryPanelBlock.PanelSlot.BOTTOM_LEFT;
        }
        return values[ordinal];
    }

    public static void send(BlockPos pos, FactoryPanelBlock.PanelSlot slot) {
        FluidLogisticsPackets.getChannel().sendToServer(new HandPointerAuthorizeLogisticsNetworkPacket(pos, slot));
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            Level level = player.level();
            if (!level.isLoaded(sourcePos)) {
                return;
            }

            if (player.distanceToSqr(sourcePos.getX() + 0.5D, sourcePos.getY() + 0.5D, sourcePos.getZ() + 0.5D) > 64.0D) {
                return;
            }

            ItemStack heldStack = player.getMainHandItem();
            if (!(heldStack.getItem() instanceof HandPointerItem)) {
                return;
            }

            UUID networkId = LogisticsLinkResolver.resolve(level, sourcePos, panelSlot)
                .map(LogisticsLinkResolver.ResolvedLink::networkId)
                .orElse(null);
            if (networkId == null) {
                if (level.getBlockEntity(sourcePos) instanceof FactoryPanelBlockEntity) {
                    displayStatus(player, "create.fluidlogistics.hand_pointer.logistics.no_panel_at_slot",
                        STATUS_INVALID_COLOR);
                }
                return;
            }

            if (!Create.LOGISTICS.mayAdministrate(networkId, player)) {
                displayStatus(player, "logistically_linked.protected", STATUS_INVALID_COLOR);
                return;
            }

            if (!HandPointerItem.authorizeNetwork(heldStack, networkId)) {
                displayStatus(player, "create.fluidlogistics.hand_pointer.authorization_exists", STATUS_NEUTRAL_COLOR);
                return;
            }

            displayStatus(player, "create.fluidlogistics.hand_pointer.authorization_added", STATUS_SUCCESS_COLOR);
        });
        return true;
    }

    private static void displayStatus(ServerPlayer player, String translationKey, int color) {
        player.displayClientMessage(
            Component.translatable(translationKey)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))),
            true
        );
    }
}
