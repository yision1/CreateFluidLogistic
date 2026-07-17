package com.yision.fluidlogistics.network.factoryPanel;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.yision.fluidlogistics.api.packager.PackageResourceDisplay.FactoryPanelRestockPolicy;
import com.yision.fluidlogistics.content.logistics.packageResource.ResourceRestockSettings;
import com.yision.fluidlogistics.network.FluidLogisticsPackets;
import com.yision.fluidlogistics.util.ResourceGaugeHelper;

import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public record FactoryPanelSetResourceRestockSettingPacket(
        FactoryPanelPosition panelPosition,
        Setting setting,
        int value) implements ServerboundPacketPayload {

    public enum Setting {
        RESTOCK_THRESHOLD {
            @Override
            boolean isConfigurable(FactoryPanelRestockPolicy policy) {
                return policy.configurableThreshold();
            }

            @Override
            int get(ResourceRestockSettings settings) {
                return settings.fluidlogistics$getRestockThreshold();
            }

            @Override
            void set(ResourceRestockSettings settings, int value) {
                settings.fluidlogistics$setRestockThreshold(value);
            }
        },
        PROMISE_LIMIT {
            @Override
            boolean isConfigurable(FactoryPanelRestockPolicy policy) {
                return policy.configurablePromiseLimit();
            }

            @Override
            int get(ResourceRestockSettings settings) {
                return settings.fluidlogistics$getPromiseLimit();
            }

            @Override
            void set(ResourceRestockSettings settings, int value) {
                settings.fluidlogistics$setPromiseLimit(value);
            }
        },
        ADDITIONAL_STOCK {
            @Override
            boolean isConfigurable(FactoryPanelRestockPolicy policy) {
                return policy.configurableAdditionalStock();
            }

            @Override
            int get(ResourceRestockSettings settings) {
                return settings.fluidlogistics$getAdditionalStock();
            }

            @Override
            void set(ResourceRestockSettings settings, int value) {
                settings.fluidlogistics$setAdditionalStock(value);
            }
        };

        abstract boolean isConfigurable(FactoryPanelRestockPolicy policy);

        abstract int get(ResourceRestockSettings settings);

        abstract void set(ResourceRestockSettings settings, int value);

        boolean apply(ResourceRestockSettings settings, FactoryPanelRestockPolicy policy, int value) {
            if (!isConfigurable(policy) || get(settings) == value) {
                return false;
            }
            int previous = get(settings);
            set(settings, value);
            return get(settings) != previous;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FactoryPanelSetResourceRestockSettingPacket>
            STREAM_CODEC = StreamCodec.of(
                    FactoryPanelSetResourceRestockSettingPacket::encode,
                    FactoryPanelSetResourceRestockSettingPacket::decode);

    private static void encode(
            RegistryFriendlyByteBuf buffer, FactoryPanelSetResourceRestockSettingPacket packet) {
        FactoryPanelPosition.STREAM_CODEC.encode(buffer, packet.panelPosition);
        buffer.writeEnum(packet.setting);
        buffer.writeVarInt(packet.value);
    }

    private static FactoryPanelSetResourceRestockSettingPacket decode(RegistryFriendlyByteBuf buffer) {
        FactoryPanelPosition panelPosition = FactoryPanelPosition.STREAM_CODEC.decode(buffer);
        return new FactoryPanelSetResourceRestockSettingPacket(
                panelPosition, buffer.readEnum(Setting.class), buffer.readVarInt());
    }

    @Override
    public void handle(ServerPlayer player) {
        ResourceGaugeHelper.applyPanelSetting(
                player, panelPosition, (policy, settings) -> setting.apply(settings, policy, value));
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return FluidLogisticsPackets.FACTORY_PANEL_SET_RESOURCE_RESTOCK_SETTING;
    }
}
