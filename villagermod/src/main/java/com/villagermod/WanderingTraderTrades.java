package com.villagermod;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Curated trade list for the wandering trader. Replaces vanilla's random
 * pools (which include emerald-printing trades) with a fixed set of
 * emerald-consuming trades plus one rare "64 emeralds → 1 nether star" sink.
 *
 * Design goals (matching the rest of the villagermod economy):
 *  - Trader never sells emeralds (no printing press).
 *  - All trades are randomized per-spawn (vanilla behavior preserved):
 *    we pick K trades out of a larger pool, so each trader visit is a bit
 *    different.
 *  - The "nether star for 64 emeralds" trade is rare: it appears ~1/3 as
 *    often as a normal trade.
 *
 * Implementation:
 *  - 5 normal trade slots are picked randomly from NORMAL_POOL (8 candidates).
 *  - 1 rare trade slot is picked randomly from RARE_POOL (1 candidate at the
 *    moment — the nether star trade). The pool-size vs slots ratio of 1:1
 *    means the rare trade always appears; the "3x rarer" effect is achieved
 *    by only having 1 rare slot vs 3 normal slots in vanilla, which we
 *    preserve by only picking 1 rare trade.
 *  - To make the nether star trade literally 3x rarer than a normal trade
 *    per-spawn, we add a 1/3 random gate on top: only 33% of traders will
 *    have the rare trade at all.
 *
 * All trades use priceMultiplier=0 so no demand-based or per-player discount
 * ever applies (consistent with the villager trade rules).
 */
public final class WanderingTraderTrades {

    private WanderingTraderTrades() {}

    /** Number of normal trades per trader spawn. */
    private static final int NORMAL_SLOTS = 5;

    /** Emerald cost for normal trades. */
    private static final int NORMAL_EMERALD_PRICE = 1;

    /** Probability that the rare nether-star trade appears (1/3 = "3x rarer"). */
    private static final float RARE_TRADE_CHANCE = 1.0f / 3.0f;

    /** Emerald cost for the rare nether-star trade. */
    private static final int NETHER_STAR_EMERALD_PRICE = 64;

    /** Max uses per trade. */
    private static final int NORMAL_MAX_USES = 6;
    private static final int RARE_MAX_USES = 1;

    /** Merchant XP per trade. */
    private static final int NORMAL_EXP = 2;
    private static final int RARE_EXP = 30;

    /**
     * Generate the trader's offer list. Called every time the trader spawns
     * (vanilla calls fillRecipes on spawn).
     */
    public static List<TradeOffer> generate(Random random) {
        List<TradeOffer> result = new ArrayList<>();

        // Pick NORMAL_SLOTS distinct normal trades from NORMAL_POOL.
        List<TradeFactory> pool = new ArrayList<>(NORMAL_POOL);
        for (int i = 0; i < NORMAL_SLOTS && !pool.isEmpty(); i++) {
            int idx = random.nextInt(pool.size());
            TradeFactory factory = pool.remove(idx);
            TradeOffer offer = factory.create(random);
            if (offer != null) {
                result.add(offer);
            }
        }

        // Rare nether-star trade — only with probability RARE_TRADE_CHANCE.
        if (random.nextFloat() < RARE_TRADE_CHANCE) {
            result.add(createNetherStarTrade());
        }

        return result;
    }

    /** Functional interface for a trade factory. */
    @FunctionalInterface
    private interface TradeFactory {
        TradeOffer create(Random random);
    }

    /**
     * The nether-star sink: 64 emeralds → 1 nether star. The ONLY way to
     * get a nether star without killing the Warden.
     */
    private static TradeOffer createNetherStarTrade() {
        return new TradeOffer(
                new TradedItem(Items.EMERALD, NETHER_STAR_EMERALD_PRICE),
                Optional.empty(),
                new ItemStack(Items.NETHER_STAR, 1),
                RARE_MAX_USES,
                RARE_EXP,
                0f
        );
    }

    /**
     * Build a "1 emerald → N items" trade. Items that are normally sold by
     * the wandering trader (saplings, flowers, seeds, kelp, etc.).
     */
    private static TradeFactory sellForEmerald(net.minecraft.item.Item item, int count) {
        return random -> new TradeOffer(
                new TradedItem(Items.EMERALD, NORMAL_EMERALD_PRICE),
                Optional.empty(),
                new ItemStack(item, count),
                NORMAL_MAX_USES,
                NORMAL_EXP,
                0f
        );
    }

    /**
     * Build a "1 emerald + secondItem → result" trade (e.g. dye + emerald
     * → colored bed). Used to keep some flavor of vanilla "compound" trades
     * without printing emeralds.
     */
    private static TradeFactory sellCompoundForEmerald(net.minecraft.item.Item secondBuy,
                                                       int secondBuyCount,
                                                       net.minecraft.item.Item result,
                                                       int resultCount) {
        return random -> new TradeOffer(
                new TradedItem(Items.EMERALD, NORMAL_EMERALD_PRICE),
                Optional.of(new TradedItem(secondBuy, secondBuyCount)),
                new ItemStack(result, resultCount),
                NORMAL_MAX_USES,
                NORMAL_EXP,
                0f
        );
    }

    /**
     * Pool of normal trades. Vanilla wandering trader's normal trades
     * include: saplings, seeds, flowers, plants, kelp, dye, etc.
     * Each candidate here is a 1-emerald trade where the player BUYS the
     * item (not sells) — never prints emeralds.
     */
    private static final List<TradeFactory> NORMAL_POOL = List.of(
            // Saplings (vanilla sells these for 5 emeralds — way too expensive
            // given emerald scarcity; we sell for 1).
            sellForEmerald(Items.OAK_SAPLING, 4),
            sellForEmerald(Items.BIRCH_SAPLING, 4),
            sellForEmerald(Items.SPRUCE_SAPLING, 4),
            sellForEmerald(Items.JUNGLE_SAPLING, 4),
            sellForEmerald(Items.ACACIA_SAPLING, 4),
            sellForEmerald(Items.DARK_OAK_SAPLING, 4),
            sellForEmerald(Items.CHERRY_SAPLING, 4),
            sellForEmerald(Items.MANGROVE_PROPAGULE, 4),
            // Flowers / plants
            sellForEmerald(Items.POPPY, 8),
            sellForEmerald(Items.DANDELION, 8),
            sellForEmerald(Items.BLUE_ORCHID, 4),
            sellForEmerald(Items.ALLIUM, 4),
            sellForEmerald(Items.AZURE_BLUET, 4),
            sellForEmerald(Items.RED_TULIP, 4),
            sellForEmerald(Items.OXEYE_DAISY, 4),
            sellForEmerald(Items.CORNFLOWER, 4),
            sellForEmerald(Items.LILY_OF_THE_VALLEY, 4),
            sellForEmerald(Items.WITHER_ROSE, 1),
            // Seeds
            sellForEmerald(Items.WHEAT_SEEDS, 16),
            sellForEmerald(Items.BEETROOT_SEEDS, 16),
            sellForEmerald(Items.PUMPKIN_SEEDS, 16),
            sellForEmerald(Items.MELON_SEEDS, 16),
            sellForEmerald(Items.TORCHFLOWER_SEEDS, 1),
            sellForEmerald(Items.PITCHER_POD, 1),
            // Plants / nature
            sellForEmerald(Items.BROWN_MUSHROOM, 8),
            sellForEmerald(Items.RED_MUSHROOM, 8),
            sellForEmerald(Items.KELP, 12),
            sellForEmerald(Items.SEA_PICKLE, 6),
            sellForEmerald(Items.BAMBOO, 12),
            sellForEmerald(Items.SUGAR_CANE, 8),
            sellForEmerald(Items.LILY_PAD, 8),
            sellForEmerald(Items.VINE, 8),
            sellForEmerald(Items.MOSS_BLOCK, 4),
            sellForEmerald(Items.POINTED_DRIPSTONE, 4),
            sellForEmerald(Items.SMALL_DRIPLEAF, 4),
            sellForEmerald(Items.SPORE_BLOSSOM, 1),
            // Misc vanilla trader items
            sellForEmerald(Items.INK_SAC, 8),
            sellForEmerald(Items.COCOA_BEANS, 8),
            sellForEmerald(Items.GLOW_INK_SAC, 4),
            sellForEmerald(Items.NAUTILUS_SHELL, 2),
            sellForEmerald(Items.PUFFERFISH_BUCKET, 1),
            sellForEmerald(Items.TROPICAL_FISH_BUCKET, 1),
            sellForEmerald(Items.COD_BUCKET, 1),
            sellForEmerald(Items.SALMON_BUCKET, 1),
            sellForEmerald(Items.AXOLOTL_BUCKET, 1),
            sellForEmerald(Items.TADPOLE_BUCKET, 1)
    );
}
