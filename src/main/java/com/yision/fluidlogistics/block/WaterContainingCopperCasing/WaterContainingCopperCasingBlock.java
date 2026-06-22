package com.yision.fluidlogistics.block.WaterContainingCopperCasing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.yision.fluidlogistics.config.FeatureToggle;
import com.yision.fluidlogistics.registry.AllBlockEntities;
import java.util.List;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;

public class WaterContainingCopperCasingBlock extends Block implements IWrenchable, IBE<WaterContainingCopperCasingBlock.Entity> {

    public WaterContainingCopperCasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!FeatureToggle.isEnabled(FeatureToggle.WATER_CONTAINING_COPPER_CASING)) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.FAIL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
        Player player, InteractionHand hand, BlockHitResult hitResult) {
        Entity be = getBlockEntity(level, pos);
        if (be != null && FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, be))
            return ItemInteractionResult.SUCCESS;

        if (!stack.is(Items.BUCKET)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            ItemStack filledBucket = new ItemStack(Items.WATER_BUCKET);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filledBucket));
            player.awardStat(Stats.ITEM_USED.get(Items.BUCKET));
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
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

    public static class Entity extends SmartBlockEntity {

        public Entity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Override
        public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        }
    }

    public static class Renderer extends SafeBlockEntityRenderer<Entity> {

        private static final FluidStack RENDERED_WATER = new FluidStack(Fluids.WATER, 1000);
        private static final float FLUID_MIN = 1 / 16f;
        private static final float FLUID_MAX = 15 / 16f;

        public Renderer(BlockEntityRendererProvider.Context context) {
        }

        @Override
        protected void renderSafe(Entity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
            int overlay) {
            renderFluid(ms, buffer, light);
        }

        public static void renderFluid(PoseStack ms, MultiBufferSource buffer, int light) {
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(RENDERED_WATER, FLUID_MIN, FLUID_MIN, FLUID_MIN,
                FLUID_MAX, FLUID_MAX, FLUID_MAX, buffer, ms, light, true, true);
        }
    }
}
