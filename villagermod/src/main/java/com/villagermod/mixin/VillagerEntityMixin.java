package com.villagermod.mixin;

import com.villagermod.AlchemistTrades;
import com.villagermod.VillagerMod;
import com.villagermod.TradeTransformer;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin on VillagerEntity:
 *
 *  1) Alchemist detection via NBT tag (not custom profession — avoids
 *     registry sync that kicks Bedrock players).
 *  2) Generate custom Alchemist trades (Brewer/Supplier).
 *  3) Transform trades for normal professions via TradeTransformer.
 *  4) Cancel prepareOffersFor (no per-player discounts).
 *  5) Randomly assign Alchemist tag to Leatherworkers (50% chance).
 *  6) Persist the tag in NBT.
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {

    @Inject(method = "fillRecipes", at = @At("RETURN"))
    private void villagermod$transformRecipes(ServerWorld world, CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        MerchantEntityAccessor accessor = (MerchantEntityAccessor) (MerchantEntity) (Object) this;
        TradeOfferList offers = accessor.villagermod$getOffersField();
        if (offers == null) return;

        // Check if this is an Alchemist (by NBT tag)
        boolean isAlchemist = self.getCommandTags().contains(VillagerMod.ALCHEMIST_TAG);

        if (isAlchemist) {
            generateAlchemistTrades(self, offers, world);
            return;
        }

        // Normal profession: transform vanilla trades
        List<TradeOffer> transformed = new ArrayList<>();
        for (TradeOffer offer : offers) {
            if (TradeTransformer.isDiamondForEmeraldTrade(offer)) continue;
            TradeOffer newOffer = TradeTransformer.transform(offer);
            if (newOffer != null) transformed.add(newOffer);
        }
        transformed.add(TradeTransformer.createDiamondForEmeraldTrade());

        offers.clear();
        offers.addAll(transformed);
    }

    @Inject(method = "mobTick", at = @At("RETURN"))
    private void villagermod$maybeAssignAlchemist(ServerWorld world, CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity) (Object) this;

        // Only process if villager is a Leatherworker and doesn't already have the tag
        if (self.getCommandTags().contains(VillagerMod.ALCHEMIST_TAG)) return;

        // Check if leatherworker by profession key
        var profKey = self.getVillagerData().profession().getKey();
        if (profKey.isEmpty() || !profKey.get().equals(VillagerProfession.LEATHERWORKER)) return;

        // 50% chance to become Alchemist — checked once when villager has a profession
        // Use UUID hash for deterministic result
        if (self.getUuid().hashCode() % 2 == 0) {
            self.addCommandTag(VillagerMod.ALCHEMIST_TAG);
        }
    }

    private void generateAlchemistTrades(VillagerEntity villager, TradeOfferList offers, ServerWorld world) {
        int variation = villager.getUuid().hashCode() % 2 == 0 ? 0 : 1;
        int level = villager.getVillagerData().level();

        List<TradeOffer> allTrades = new ArrayList<>();
        for (int lvl = 1; lvl <= level; lvl++) {
            TradeOffer[] levelTrades = AlchemistTrades.generateTrades(lvl, variation,
                    world.getRandom());
            for (TradeOffer t : levelTrades) {
                allTrades.add(t);
            }
        }

        offers.clear();
        offers.addAll(allTrades);
    }

    @Inject(method = "prepareOffersFor", at = @At("HEAD"), cancellable = true)
    private void villagermod$cancelPrepareOffers(PlayerEntity player, CallbackInfo ci) {
        ci.cancel();
    }
}
