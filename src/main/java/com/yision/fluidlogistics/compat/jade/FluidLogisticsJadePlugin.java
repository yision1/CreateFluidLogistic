package com.yision.fluidlogistics.compat.jade;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.yision.fluidlogistics.content.fluids.faucet.AbstractFaucetBlockEntity;
import com.yision.fluidlogistics.content.equipment.mechanicalFluidGun.MechanicalFluidGunBlockEntity;
import com.yision.fluidlogistics.content.fluids.multiFluidAccessPort.MultiFluidAccessPortBlockEntity;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class FluidLogisticsJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(PackageItemStorageProvider.INSTANCE, PackageEntity.class);
        registration.registerFluidStorage(PackageFluidStorageProvider.INSTANCE, PackageEntity.class);
        registration.registerFluidStorage(MultiFluidTankProvider.INSTANCE, Block.class);
        registration.registerFluidStorage(ContraptionMultiFluidTankProvider.INSTANCE, AbstractContraptionEntity.class);
        registration.registerFluidStorage(MechanicalFluidGunFluidProvider.INSTANCE, MechanicalFluidGunBlockEntity.class);
        registration.registerFluidStorage(MultiFluidAccessPortFluidProvider.INSTANCE, MultiFluidAccessPortBlockEntity.class);
        registration.registerFluidStorage(FaucetFluidProvider.INSTANCE, AbstractFaucetBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerItemStorageClient(PackageItemStorageProvider.INSTANCE);
        registration.registerFluidStorageClient(PackageFluidStorageProvider.INSTANCE);
        registration.registerFluidStorageClient(MultiFluidTankProvider.INSTANCE);
        registration.registerFluidStorageClient(ContraptionMultiFluidTankProvider.INSTANCE);
        registration.registerFluidStorageClient(MechanicalFluidGunFluidProvider.INSTANCE);
        registration.registerFluidStorageClient(MultiFluidAccessPortFluidProvider.INSTANCE);
        registration.registerFluidStorageClient(FaucetFluidProvider.INSTANCE);
    }
}
