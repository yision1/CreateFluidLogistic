package com.yision.fluidlogistics.ponder;

import com.simibubi.create.Create;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.yision.fluidlogistics.FluidLogistics;
import com.yision.fluidlogistics.registry.AllBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class FluidLogisticsPonderPlugin implements PonderPlugin {

	public static final ResourceLocation HIGH_LOGISTICS = Create.asResource("high_logistics");
	public static final ResourceLocation LOGISTICS = Create.asResource("logistics");
	public static final ResourceLocation FLUIDS = Create.asResource("fluids");
	public static final ResourceLocation ARM_TARGETS = Create.asResource("arm_targets");
	public static final ResourceLocation KINETIC_APPLIANCES = Create.asResource("kinetic_appliances");

	@Override
	public String getModId() {
		return FluidLogistics.MODID;
	}

	@Override
	public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
		PonderSceneRegistrationHelper<ItemProviderEntry<?>> registration = helper.withKeyFunction(RegistryEntry::getId);

		registration.forComponents(AllBlocks.FLUID_PACKAGER)
			.addStoryBoard(FluidPackagerScenes.FLUID_PACKAGER, FluidPackagerScenes::fluidPackager)
			.addStoryBoard(FluidPackagerScenes.FLUID_PACKAGER_ADDRESS, FluidPackagerScenes::fluidPackagerAddress);

		registration.forComponents(AllBlocks.SMART_FAUCET)
			.addStoryBoard(SmartFaucetScenes.SMART_FAUCET, SmartFaucetScenes::smartFaucet);

		registration.forComponents(AllBlocks.FAUCET)
			.addStoryBoard(FaucetScenes.FAUCET, FaucetScenes::faucet);

		registration.forComponents(AllBlocks.MULTI_FLUID_ACCESS_PORT)
			.addStoryBoard(MultiFluidAccessPortScenes.MULTI_FLUID_ACCESS_PORT,
				MultiFluidAccessPortScenes::multiFluidAccessPort);

		registration.forComponents(AllBlocks.SMART_HOPPER)
			.addStoryBoard(SmartHopperScenes.SMART_HOPPER, SmartHopperScenes::smartHopper);

		registration.forComponents(AllBlocks.FLUID_TRANSPORTER)
			.addStoryBoard(FluidTransporterScenes.FLUID_TRANSPORTER,
				FluidTransporterScenes::fluidTransporter);

		registration.forComponents(AllBlocks.MECHANICAL_FLUID_GUN)
			.addStoryBoard(MechanicalFluidGunScenes.MECHANICAL_FLUID_GUN_SETUP,
				MechanicalFluidGunScenes::setup, FLUIDS, KINETIC_APPLIANCES);
	}

	@Override
	public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
		PonderTagRegistrationHelper<ItemProviderEntry<?>> registration = helper.withKeyFunction(RegistryEntry::getId);

		registration.addToTag(HIGH_LOGISTICS)
			.add(AllBlocks.FLUID_PACKAGER);

		registration.addToTag(LOGISTICS)
			.add(AllBlocks.SMART_HOPPER);

		registration.addToTag(FLUIDS)
			.add(AllBlocks.SMART_FAUCET)
			.add(AllBlocks.FAUCET)
			.add(AllBlocks.MULTI_FLUID_ACCESS_PORT)
			.add(AllBlocks.SMART_HOPPER)
			.add(AllBlocks.FLUID_TRANSPORTER)
			.add(AllBlocks.MECHANICAL_FLUID_GUN);

		registration.addToTag(KINETIC_APPLIANCES)
			.add(AllBlocks.MECHANICAL_FLUID_GUN);

		registration.addToTag(ARM_TARGETS)
			.add(AllBlocks.SMART_HOPPER);
	}
}
