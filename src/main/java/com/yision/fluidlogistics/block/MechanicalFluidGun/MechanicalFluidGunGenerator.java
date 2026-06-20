package com.yision.fluidlogistics.block.MechanicalFluidGun;

import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;

public class MechanicalFluidGunGenerator {

	public static void generate(DataGenContext<Block, MechanicalFluidGunBlock> ctx, RegistrateBlockstateProvider prov) {
		ModelFile model = prov.models().getExistingFile(prov.modLoc("block/mechanical_fluid_gun/block"));

		prov.getVariantBuilder(ctx.getEntry()).forAllStates(state -> {
				Direction mountFace = state.getValue(MechanicalFluidGunBlock.MOUNT_FACE);

				int rotX;
				int rotY;

				switch (mountFace) {
					case UP -> {
						rotX = 0;
						rotY = 0;
					}
					case DOWN -> {
						rotX = 180;
						rotY = 0;
					}
					case NORTH -> {
						rotX = 90;
						rotY = 0;
					}
					case SOUTH -> {
						rotX = 270;
						rotY = 0;
					}
					case EAST -> {
						rotX = 90;
						rotY = 90;
					}
					case WEST -> {
						rotX = 90;
						rotY = 270;
					}
					default -> {
						rotX = 0;
						rotY = 0;
					}
				}

				return ConfiguredModel.builder()
					.modelFile(model)
					.rotationX(rotX)
					.rotationY(rotY)
					.build();
			});
	}
}
