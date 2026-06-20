package com.yision.fluidlogistics.mixin.logistics;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.yision.fluidlogistics.filter.attribute.FluidAttribute;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(value = FilterItemStack.AttributeFilterItemStack.class, remap = false)
public abstract class AttributeFilterItemStackMixin extends FilterItemStack {
    @Shadow(remap = false)
    public FilterItemStack.AttributeFilterItemStack.WhitelistMode whitelistMode;

    @Shadow(remap = false)
    public List<Pair<ItemAttribute, Boolean>> attributeTests;

    protected AttributeFilterItemStackMixin(ItemStack filter) {
        super(filter);
    }

    /**
     * Adds CFL fluid attributes to Create's attribute filter fluid checks.
     *
     * @author yision
     * @reason Create's 1.20.1 attribute filter has no fluid attribute implementation.
     */
    @Overwrite(remap = false)
    public boolean test(Level world, FluidStack stack, boolean matchNBT) {
        if (attributeTests.isEmpty())
            return super.test(world, stack, matchNBT);

        for (Pair<ItemAttribute, Boolean> test : attributeTests) {
            ItemAttribute itemAttribute = test.getFirst();
            boolean inverted = test.getSecond();

            if (!(itemAttribute instanceof FluidAttribute attribute))
                continue;

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

        return switch (whitelistMode) {
            case BLACKLIST, WHITELIST_CONJ -> true;
            case WHITELIST_DISJ -> false;
        };
    }
}
