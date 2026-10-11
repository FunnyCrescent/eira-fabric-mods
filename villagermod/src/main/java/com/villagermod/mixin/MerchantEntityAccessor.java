package com.villagermod.mixin;

import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor interface for the protected {@code offers} field on
 * {@link MerchantEntity}. Allows the {@link VillagerEntityMixin} to read and
 * modify the villager's offer list from outside the
 * {@code net.minecraft.entity.passive} package.
 */
@Mixin(MerchantEntity.class)
public interface MerchantEntityAccessor {

    @Accessor("offers")
    TradeOfferList villagermod$getOffersField();

    @Accessor("offers")
    void villagermod$setOffersField(TradeOfferList list);
}
