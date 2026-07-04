package com.yision.fluidlogistics.render;

import com.simibubi.create.content.fluids.FluidMesh;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.util.SmartRecycler;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Arrays;

public class FluidVisual {
    private final SmartRecycler<TextureAtlasSprite, TransformedInstance> surface;
    private final Direction[] sides;
    private final boolean renderGasesFromTop;

    public FluidVisual(VisualizationContext context, boolean renderBottom, boolean renderGasesFromTop) {
        surface = new SmartRecycler<>(key ->
                context.instancerProvider()
                        .instancer(InstanceTypes.TRANSFORMED, FluidMesh.surface(key, 1))
                        .createInstance());
        sides = renderBottom ? Iterate.directions : Arrays.copyOfRange(Iterate.directions, 1, Iterate.directions.length);
        this.renderGasesFromTop = renderGasesFromTop;
    }

    public TransformedInstance[] setupBuffers(FluidStack fluidStack, int start) {
        if (fluidStack.isEmpty()) return null;

        TransformedInstance[] buffers = new TransformedInstance[start + sides.length];

        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        var atlas = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite stillTexture = atlas.apply(clientFluid.getStillTexture(fluidStack));

        for (int i = 0; i < sides.length; i++) {
            buffers[start + i] = surface.get(stillTexture);
            buffers[start + i].colorArgb(clientFluid.getTintColor(fluidStack));
        }

        return buffers;
    }

    public void setupBuffer(FluidStack fluidStack, int capacity, TransformedInstance buffer, int index,
                            float minXZ, float maxXZ, float minY, float maxY) {
        Direction side = sides[index];

        float fill = Mth.clamp((float) fluidStack.getAmount() / capacity, 0f, 1f);
        float width = maxXZ - minXZ;
        float height = (maxY - minY) * fill;

        boolean gas = renderGasesFromTop
            && fluidStack.getFluid().getFluidType().isLighterThanAir();

        float fluidMinY = gas ? maxY - height : minY;
        float fluidMaxY = gas ? maxY : minY + height;

        float centerXZ = (minXZ + maxXZ) / 2f;

        float halfWidth = width / 2f;
        float halfHeight = height / 2f;

        Direction renderedSide = gas && side == Direction.UP ? Direction.DOWN : side;

        switch (renderedSide) {
            case UP -> {
                buffer.translateX(centerXZ);
                buffer.translateY(fluidMaxY);
                buffer.translateZ(centerXZ);
                buffer.rotateTo(Direction.UP, renderedSide);
                buffer.scaleX(halfWidth);
                buffer.scaleZ(halfWidth);
            }
            case DOWN -> {
                buffer.translateX(centerXZ);
                buffer.translateY(fluidMinY);
                buffer.translateZ(centerXZ);
                buffer.rotateTo(Direction.UP, renderedSide);
                buffer.scaleX(halfWidth);
                buffer.scaleZ(halfWidth);
            }
            case NORTH, SOUTH -> {
                float z = renderedSide == Direction.SOUTH ? maxXZ : minXZ;
                buffer.translateX(centerXZ);
                buffer.translateY((fluidMinY + fluidMaxY) / 2f);
                buffer.translateZ(z);
                buffer.rotateTo(Direction.UP, renderedSide);
                buffer.scaleX(halfWidth);
                buffer.scaleZ(halfHeight);
            }
            case WEST, EAST -> {
                float x = renderedSide == Direction.EAST ? maxXZ : minXZ;
                buffer.translateX(x);
                buffer.translateY((fluidMinY + fluidMaxY) / 2f);
                buffer.translateZ(centerXZ);
                buffer.rotateTo(Direction.UP, renderedSide);
                buffer.scaleX(halfHeight);
                buffer.scaleZ(halfWidth);
            }
        }
    }

    public void begin() {
        surface.resetCount();
    }

    public void end() {
        surface.discardExtra();
    }

    public void delete() {
        surface.delete();
    }
}
