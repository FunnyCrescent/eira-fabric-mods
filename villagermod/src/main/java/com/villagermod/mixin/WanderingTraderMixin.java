package com.villagermod.mixin;

import com.villagermod.WanderingTraderTrades;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on WanderingTraderEntity that replaces all vanilla offers with a
 * curated list defined in {@link WanderingTraderTrades}.
 *
 * Vanilla wandering trader has 2 pools of random trades: 6 generic trades
 * (one chosen) and 1 rare trade (one chosen). Many of those trades SELL
 * emeralds — same problem as villagers. After this mixin runs, the trader
 * has only emerald-consuming trades (including the rare "64 emeralds → 1
 * nether star").
 */
@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderMixin {

    @Inject(method = "fillRecipes", at = @At("RETURN"))
    private void villagermod$replaceRecipes(ServerWorld world, CallbackInfo ci) {
        WanderingTraderEntity self = (WanderingTraderEntity) (Object) this;
        TradeOfferList offers = self.getOffers();
        offers.clear();
        offers.addAll(WanderingTraderTrades.generate(self.getRandom()));
    }
}
