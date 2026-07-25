package com.yision.fluidlogistics.network.factoryPanel;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import com.yision.fluidlogistics.api.packager.PackageResourceDisplay.FactoryPanelRestockPolicy;
import com.yision.fluidlogistics.content.logistics.packageResource.ResourceRestockSettings;
import com.yision.fluidlogistics.util.ResourceGaugeHelper;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public class FactoryPanelSetResourceRestockSettingPacket extends SimplePacketBase {
    public enum Setting {
        RESTOCK_THRESHOLD {
            @Override boolean isConfigurable(FactoryPanelRestockPolicy policy) { return policy.configurableThreshold(); }
            @Override int get(ResourceRestockSettings settings) { return settings.fluidlogistics$getRestockThreshold(); }
            @Override void set(ResourceRestockSettings settings, int value) { settings.fluidlogistics$setRestockThreshold(value); }
        },
        PROMISE_LIMIT {
            @Override boolean isConfigurable(FactoryPanelRestockPolicy policy) { return policy.configurablePromiseLimit(); }
            @Override int get(ResourceRestockSettings settings) { return settings.fluidlogistics$getPromiseLimit(); }
            @Override void set(ResourceRestockSettings settings, int value) { settings.fluidlogistics$setPromiseLimit(value); }
        },
        ADDITIONAL_STOCK {
            @Override boolean isConfigurable(FactoryPanelRestockPolicy policy) { return policy.configurableAdditionalStock(); }
            @Override int get(ResourceRestockSettings settings) { return settings.fluidlogistics$getAdditionalStock(); }
            @Override void set(ResourceRestockSettings settings, int value) { settings.fluidlogistics$setAdditionalStock(value); }
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

    private final FactoryPanelPosition panelPosition;
    private final Setting setting;
    private final int value;

    public FactoryPanelSetResourceRestockSettingPacket(
            FactoryPanelPosition panelPosition, Setting setting, int value) {
        this.panelPosition = panelPosition;
        this.setting = setting;
        this.value = value;
    }

    public FactoryPanelSetResourceRestockSettingPacket(FriendlyByteBuf buffer) {
        this.panelPosition = FactoryPanelPosition.receive(buffer);
        this.setting = buffer.readEnum(Setting.class);
        this.value = buffer.readVarInt();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        panelPosition.send(buffer);
        buffer.writeEnum(setting);
        buffer.writeVarInt(value);
    }

    @Override
    public boolean handle(Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ResourceGaugeHelper.applyPanelSetting(
                        player, panelPosition, (policy, settings) -> setting.apply(settings, policy, value));
            }
        });
        return true;
    }
}
