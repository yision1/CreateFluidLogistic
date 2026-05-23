package com.yision.fluidlogistics.block.WaterContainingCopperCasing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public class WaterContainingCopperCasingBlock extends Block implements IWrenchable, IBE<WaterContainingCopperCasingBlock.Entity> {
    private static final FluidStack RENDERED_WATER = new FluidStack(Fluids.WATER, 1000);
    private static final float FLUID_MIN = 1 / 16f;
    private static final float FLUID_MAX = 15 / 16f;

    public WaterContainingCopperCasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.FAIL;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<Entity> getBlockEntityClass() {
        return Entity.class;
    }

    @Override
    public BlockEntityType<? extends Entity> getBlockEntityType() {
        return AllBlockEntities.WATER_CONTAINING_COPPER_CASING.get();
    }

    public static class Entity extends BlockEntity {

        public Entity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }
    }

    public static class Renderer extends SafeBlockEntityRenderer<Entity> {

        public Renderer(BlockEntityRendererProvider.Context context) {
        }

        @Override
        protected void renderSafe(Entity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
            int overlay) {
            renderFluid(ms, buffer, light);
        }
    }

    static void renderFluid(PoseStack ms, MultiBufferSource buffer, int light) {
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(RENDERED_WATER, FLUID_MIN, FLUID_MIN, FLUID_MIN,
            FLUID_MAX, FLUID_MAX, FLUID_MAX, buffer, ms, light, false, true);
    }
}
