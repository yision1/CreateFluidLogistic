package com.yision.fluidlogistics.mixin.logistics;

import com.simibubi.create.content.logistics.filter.AttributeFilterWhitelistMode;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.yision.fluidlogistics.filter.attribute.FluidAttribute;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(FilterItemStack.AttributeFilterItemStack.class)
public abstract class AttributeFilterItemStackMixin extends FilterItemStack {
    @Shadow
    public AttributeFilterWhitelistMode whitelistMode;

    @Shadow
    public List<Pair<ItemAttribute, Boolean>> attributeTests;

    protected AttributeFilterItemStackMixin(ItemStack filter) {
        super(filter);
    }

    @Overwrite
    public boolean test(Level world, FluidStack stack, boolean matchNBT) {
        if (attributeTests.isEmpty())
            return super.test(world, stack, matchNBT);

        boolean hasFluidAttribute = false;
        for (Pair<ItemAttribute, Boolean> test : attributeTests) {
            ItemAttribute itemAttribute = test.getFirst();
            boolean inverted = test.getSecond();

            if (!(itemAttribute instanceof FluidAttribute attribute))
                continue;
            hasFluidAttribute = true;

            boolean matches = attribute.appliesTo(stack, world) != inverted;

            if (matches) {
                switch (whitelistMode) {
                    case BLACKLIST -> { return false; }
                    case WHITELIST_CONJ -> { continue; }
                    case WHITELIST_DISJ -> { return true; }
                }
            } else {
                switch (whitelistMode) {
                    case BLACKLIST, WHITELIST_DISJ -> { continue; }
                    case WHITELIST_CONJ -> { return false; }
                }
            }
        }

        if (!hasFluidAttribute)
            return whitelistMode == AttributeFilterWhitelistMode.BLACKLIST;

        return switch (whitelistMode) {
            case BLACKLIST, WHITELIST_CONJ -> true;
            case WHITELIST_DISJ -> false;
        };
    }
}
