package com.villagermod;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Trade transformer v5 — based on user-provided trade table.
 *
 * Instead of algorithmic formulas, uses a lookup table that maps each
 * item type to its exact trade specification (emerald price, item count,
 * optional second buy item).
 *
 * Three categories:
 *  A) Flip-сырьё: villager sells emerald for N items → flipped to
 *     "1 emerald → N items" where N is from the table (usually 40-64).
 *  B) Stack goods: villager sells item for emeralds → "price emeralds →
 *     count items" where both are from the table.
 *  C) Non-stack goods: villager sells item → "price emeralds → 1 item".
 *
 * If an item is not in the lookup table, falls back to a default
 * transformation (1 emerald, ×10 count, cap 64).
 */
public final class TradeTransformer {

    private TradeTransformer() {}

    public static final int MAX_COUNT = 64;
    public static final int MAX_USES = 12;
    public static final int MERCHANT_EXP = 5;
    public static final int DIAMOND_TRADE_EXP = 10;

    /**
     * Trade specification: how many emeralds the player pays, how many
     * items they get, and an optional second buy item.
     */
    public static final class TradeSpec {
        final int emeraldPrice;
        final int itemCount;
        final ItemStack secondBuy; // null = no second buy

        TradeSpec(int emeraldPrice, int itemCount) {
            this(emeraldPrice, itemCount, null);
        }

        TradeSpec(int emeraldPrice, int itemCount, ItemStack secondBuy) {
            this.emeraldPrice = emeraldPrice;
            this.itemCount = itemCount;
            this.secondBuy = secondBuy;
        }
    }

    /**
     * Lookup table for FLIP trades (villager originally sells emerald
     * for N items → flipped to "1 emerald → N items").
     * Key: the item the villager originally wanted.
     * Value: how many of that item the player gets for 1 emerald.
     */
    private static final Map<net.minecraft.item.Item, Integer> FLIP_ITEMS = new HashMap<>();

    /**
     * Lookup table for SELL trades (villager sells item for emeralds).
     * Key: the item being sold.
     * Value: TradeSpec with emerald price and item count.
     */
    private static final Map<net.minecraft.item.Item, TradeSpec> SELL_ITEMS = new HashMap<>();

    static {
        initTradeTable();
    }

    private static void initTradeTable() {
        // === FLIP-СЫРЬЁ (villager sells emerald → flipped to 1 emerald → N items) ===
        // Farmer
        FLIP_ITEMS.put(Items.WHEAT, 64);
        FLIP_ITEMS.put(Items.POTATO, 64);
        FLIP_ITEMS.put(Items.CARROT, 64);
        FLIP_ITEMS.put(Items.BEETROOT, 64);
        FLIP_ITEMS.put(Items.PUMPKIN, 64);
        FLIP_ITEMS.put(Items.MELON_SLICE, 40);
        // Fisherman
        FLIP_ITEMS.put(Items.STRING, 64);
        FLIP_ITEMS.put(Items.COD, 60);
        FLIP_ITEMS.put(Items.SALMON, 64);
        FLIP_ITEMS.put(Items.TROPICAL_FISH, 60);
        FLIP_ITEMS.put(Items.PUFFERFISH, 60);
        // Shepherd
        FLIP_ITEMS.put(Items.WHITE_WOOL, 64); // all wool colors handled below
        // Fletcher
        FLIP_ITEMS.put(Items.STICK, 64);
        FLIP_ITEMS.put(Items.FLINT, 64);
        FLIP_ITEMS.put(Items.FEATHER, 64);
        // Leatherworker
        FLIP_ITEMS.put(Items.LEATHER, 64);
        // Librarian
        FLIP_ITEMS.put(Items.PAPER, 64);
        // Weaponsmith / Armorer / Toolsmith / Butcher
        FLIP_ITEMS.put(Items.COAL, 64);
        FLIP_ITEMS.put(Items.IRON_INGOT, 40);
        // Cleric
        FLIP_ITEMS.put(Items.REDSTONE, 64);
        FLIP_ITEMS.put(Items.GLOWSTONE_DUST, 40);
        // Mason
        FLIP_ITEMS.put(Items.CLAY_BALL, 64);
        // Butcher
        FLIP_ITEMS.put(Items.BEEF, 64);

        // All wool colors → 64
        for (var wool : new net.minecraft.item.Item[]{
                Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL,
                Items.LIGHT_BLUE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL,
                Items.PINK_WOOL, Items.GRAY_WOOL, Items.LIGHT_GRAY_WOOL,
                Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
                Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL,
                Items.BLACK_WOOL}) {
            FLIP_ITEMS.put(wool, 64);
        }

        // === SELL ITEMS (villager sells item for emeralds) ===
        // Farmer
        addSell(Items.BREAD, 1, 40);
        addSell(Items.PUMPKIN_PIE, 1, 10);
        addSell(Items.APPLE, 1, 10);
        addSell(Items.BAKED_POTATO, 1, 50);
        addSell(Items.GOLDEN_CARROT, 1, 40);
        addSell(Items.CAKE, 1, 1); // non-stack
        addSell(Items.EXPERIENCE_BOTTLE, 1, 30);
        addSell(Items.GOLDEN_APPLE, 3, 1, new ItemStack(Items.APPLE)); // 3 em + apple

        // Fisherman
        addSell(Items.COOKED_COD, 1, 10);
        addSell(Items.COD_BUCKET, 1, 1);
        addSell(Items.COOKED_SALMON, 1, 10);
        addSell(Items.FISHING_ROD, 1, 1);
        addSell(Items.TROPICAL_FISH_BUCKET, 1, 1);
        addSell(Items.SALMON_BUCKET, 1, 1);

        // Shepherd
        addSell(Items.SHEARS, 1, 1);
        addSell(Items.WHITE_WOOL, 1, 10); // dyed wool handled below
        addSell(Items.WHITE_CARPET, 1, 10);
        addSell(Items.RED_BED, 1, 1); // bed — non-stack
        addSell(Items.WHITE_BANNER, 1, 10);
        addSell(Items.PAINTING, 1, 10);

        // Dyed wool → 10
        for (var w : new net.minecraft.item.Item[]{
                Items.WHITE_WOOL, Items.ORANGE_WOOL, Items.MAGENTA_WOOL,
                Items.LIGHT_BLUE_WOOL, Items.YELLOW_WOOL, Items.LIME_WOOL,
                Items.PINK_WOOL, Items.GRAY_WOOL, Items.LIGHT_GRAY_WOOL,
                Items.CYAN_WOOL, Items.PURPLE_WOOL, Items.BLUE_WOOL,
                Items.BROWN_WOOL, Items.GREEN_WOOL, Items.RED_WOOL, Items.BLACK_WOOL}) {
            SELL_ITEMS.put(w, new TradeSpec(1, 10));
        }
        // Carpets → 10
        for (var c : new net.minecraft.item.Item[]{
                Items.WHITE_CARPET, Items.ORANGE_CARPET, Items.MAGENTA_CARPET,
                Items.LIGHT_BLUE_CARPET, Items.YELLOW_CARPET, Items.LIME_CARPET,
                Items.PINK_CARPET, Items.GRAY_CARPET, Items.LIGHT_GRAY_CARPET,
                Items.CYAN_CARPET, Items.PURPLE_CARPET, Items.BLUE_CARPET,
                Items.BROWN_CARPET, Items.GREEN_CARPET, Items.RED_CARPET,
                Items.BLACK_CARPET}) {
            SELL_ITEMS.put(c, new TradeSpec(1, 10));
        }
        // Beds → 1 (non-stack)
        for (var b : new net.minecraft.item.Item[]{
                Items.WHITE_BED, Items.ORANGE_BED, Items.MAGENTA_BED,
                Items.LIGHT_BLUE_BED, Items.YELLOW_BED, Items.LIME_BED,
                Items.PINK_BED, Items.GRAY_BED, Items.LIGHT_GRAY_BED,
                Items.CYAN_BED, Items.PURPLE_BED, Items.BLUE_BED,
                Items.BROWN_BED, Items.GREEN_BED, Items.RED_BED, Items.BLACK_BED}) {
            SELL_ITEMS.put(b, new TradeSpec(1, 1));
        }
        // Banners → 10
        for (var bn : new net.minecraft.item.Item[]{
                Items.WHITE_BANNER, Items.ORANGE_BANNER, Items.MAGENTA_BANNER,
                Items.LIGHT_BLUE_BANNER, Items.YELLOW_BANNER, Items.LIME_BANNER,
                Items.PINK_BANNER, Items.GRAY_BANNER, Items.LIGHT_GRAY_BANNER,
                Items.CYAN_BANNER, Items.PURPLE_BANNER, Items.BLUE_BANNER,
                Items.BROWN_BANNER, Items.GREEN_BANNER, Items.RED_BANNER,
                Items.BLACK_BANNER}) {
            SELL_ITEMS.put(bn, new TradeSpec(1, 10));
        }

        // Fletcher
        addSell(Items.ARROW, 1, 64);
        addSell(Items.BOW, 1, 1);
        addSell(Items.CROSSBOW, 1, 1);
        addSell(Items.TIPPED_ARROW, 1, 50);
        addSell(Items.SPECTRAL_ARROW, 1, 50);

        // Leatherworker
        addSell(Items.LEATHER_LEGGINGS, 1, 1);
        addSell(Items.LEATHER_CHESTPLATE, 1, 1);
        addSell(Items.LEATHER_HELMET, 1, 1);
        addSell(Items.LEATHER_BOOTS, 1, 1);
        addSell(Items.SADDLE, 1, 1);

        // Librarian
        addSell(Items.BOOKSHELF, 1, 10);
        addSell(Items.LANTERN, 1, 10);
        addSell(Items.GLASS, 1, 10);
        addSell(Items.COMPASS, 1, 10);
        addSell(Items.CLOCK, 1, 10);
        addSell(Items.NAME_TAG, 3, 1); // premium
        // Enchanted books handled separately (price varies)

        // Weaponsmith
        addSell(Items.IRON_AXE, 1, 1);
        addSell(Items.IRON_SWORD, 1, 1);
        addSell(Items.DIAMOND_AXE, 3, 1);
        addSell(Items.DIAMOND_SWORD, 2, 1);

        // Armorer
        addSell(Items.IRON_LEGGINGS, 2, 1);
        addSell(Items.CHAINMAIL_BOOTS, 2, 1);
        addSell(Items.CHAINMAIL_CHESTPLATE, 2, 1);
        addSell(Items.CHAINMAIL_HELMET, 2, 1);
        addSell(Items.CHAINMAIL_LEGGINGS, 2, 1);
        addSell(Items.DIAMOND_CHESTPLATE, 6, 1);

        // Toolsmith
        addSell(Items.STONE_PICKAXE, 1, 1);
        addSell(Items.IRON_PICKAXE, 1, 1);
        addSell(Items.IRON_AXE, 1, 1);
        addSell(Items.IRON_SHOVEL, 1, 1);
        addSell(Items.DIAMOND_PICKAXE, 3, 1);
        addSell(Items.DIAMOND_AXE, 3, 1);
        addSell(Items.DIAMOND_SHOVEL, 3, 1);

        // Butcher
        addSell(Items.PORKCHOP, 1, 40);
        addSell(Items.CHICKEN, 1, 50);
        addSell(Items.COOKED_BEEF, 1, 40);
        addSell(Items.COOKED_PORKCHOP, 1, 40);
        addSell(Items.RABBIT_STEW, 1, 1);

        // Cleric
        addSell(Items.REDSTONE, 1, 10);
        addSell(Items.ENDER_PEARL, 1, 10);
        addSell(Items.GLOWSTONE, 1, 40);
        addSell(Items.EXPERIENCE_BOTTLE, 1, 10);
        addSell(Items.LAPIS_LAZULI, 1, 10);
        addSell(Items.ENDER_EYE, 1, 10);

        // Mason
        addSell(Items.BRICK, 1, 64);
        addSell(Items.CHISELED_STONE_BRICKS, 1, 10);
        addSell(Items.POLISHED_ANDESITE, 1, 10);
        addSell(Items.WHITE_GLAZED_TERRACOTTA, 1, 10); // all colors
        addSell(Items.QUARTZ_PILLAR, 1, 10);
        // Glazed terracotta all colors → 10
        for (var gt : new net.minecraft.item.Item[]{
                Items.WHITE_GLAZED_TERRACOTTA, Items.ORANGE_GLAZED_TERRACOTTA,
                Items.MAGENTA_GLAZED_TERRACOTTA, Items.LIGHT_BLUE_GLAZED_TERRACOTTA,
                Items.YELLOW_GLAZED_TERRACOTTA, Items.LIME_GLAZED_TERRACOTTA,
                Items.PINK_GLAZED_TERRACOTTA, Items.GRAY_GLAZED_TERRACOTTA,
                Items.LIGHT_GRAY_GLAZED_TERRACOTTA, Items.CYAN_GLAZED_TERRACOTTA,
                Items.PURPLE_GLAZED_TERRACOTTA, Items.BLUE_GLAZED_TERRACOTTA,
                Items.BROWN_GLAZED_TERRACOTTA, Items.GREEN_GLAZED_TERRACOTTA,
                Items.RED_GLAZED_TERRACOTTA, Items.BLACK_GLAZED_TERRACOTTA}) {
            SELL_ITEMS.put(gt, new TradeSpec(1, 10));
        }

        // Cartographer
        addSell(Items.MAP, 2, 10);
        addSell(Items.COMPASS, 1, 10); // flip
        addSell(Items.ITEM_FRAME, 2, 10);
        // Explorer maps — premium, 4 emeralds, 1 each
        // Banner patterns — 2 emeralds, 10 each
    }

    private static void addSell(net.minecraft.item.Item item, int emeralds, int count) {
        SELL_ITEMS.put(item, new TradeSpec(emeralds, count));
    }

    private static void addSell(net.minecraft.item.Item item, int emeralds, int count, ItemStack secondBuy) {
        SELL_ITEMS.put(item, new TradeSpec(emeralds, count, secondBuy));
    }

    /**
     * Transform a single vanilla trade offer based on the lookup table.
     * Returns null to drop the trade.
     */
    public static TradeOffer transform(TradeOffer offer) {
        ItemStack sellItem = offer.getSellItem();
        ItemStack originalFirstBuy = offer.getOriginalFirstBuyItem();

        // Case A: villager sells emerald — flip the trade
        if (sellItem.isOf(Items.EMERALD)) {
            if (originalFirstBuy.isEmpty() || originalFirstBuy.isOf(Items.EMERALD)) {
                return null;
            }
            Integer flipCount = FLIP_ITEMS.get(originalFirstBuy.getItem());
            int newCount = flipCount != null ? flipCount : 64;

            ItemStack newSell = originalFirstBuy.copy();
            newSell.setCount(newCount);

            return new TradeOffer(
                    new TradedItem(Items.EMERALD, 1),
                    offer.getSecondBuyItem(),
                    newSell,
                    MAX_USES,
                    MERCHANT_EXP,
                    0f);
        }

        // Case B: villager sells item for emeralds — use lookup table
        if (originalFirstBuy.isOf(Items.EMERALD)) {
            TradeSpec spec = SELL_ITEMS.get(sellItem.getItem());

            int emeraldPrice;
            int itemCount;
            Optional<TradedItem> secondBuy = offer.getSecondBuyItem();

            if (spec != null) {
                emeraldPrice = spec.emeraldPrice;
                itemCount = spec.itemCount;
                if (spec.secondBuy != null) {
                    secondBuy = Optional.of(new TradedItem(spec.secondBuy.getItem(),
                            spec.secondBuy.getCount()));
                }
            } else {
                // Fallback for items not in table (e.g. enchanted books, explorer maps)
                // Enchanted books: 5 emeralds for level I-II, 10-20 for high level
                if (sellItem.isOf(Items.ENCHANTED_BOOK)) {
                    emeraldPrice = Math.max(5, offer.getOriginalFirstBuyItem().getCount());
                    itemCount = 1;
                } else {
                    // Default: 1 emerald, ×10 count, cap 64
                    emeraldPrice = 1;
                    itemCount = Math.min(sellItem.getCount() * 10, MAX_COUNT);
                }
            }

            ItemStack newSell = sellItem.copy();
            newSell.setCount(itemCount);

            return new TradeOffer(
                    new TradedItem(Items.EMERALD, emeraldPrice),
                    secondBuy,
                    newSell,
                    MAX_USES,
                    MERCHANT_EXP,
                    0f);
        }

        // Case C: no emeralds involved — rebuild with zeroed discount
        return new TradeOffer(
                offer.getFirstBuyItem(),
                offer.getSecondBuyItem(),
                offer.getSellItem(),
                offer.getMaxUses(),
                offer.getMerchantExperience(),
                0f);
    }

    /** The guaranteed "2 diamonds → 1 emerald" trade. */
    public static TradeOffer createDiamondForEmeraldTrade() {
        return new TradeOffer(
                new TradedItem(Items.DIAMOND, 2),
                Optional.empty(),
                new ItemStack(Items.EMERALD, 1),
                MAX_USES,
                DIAMOND_TRADE_EXP,
                0f);
    }

    /** Returns true if the offer is the "2 diamonds → 1 emerald" trade. */
    public static boolean isDiamondForEmeraldTrade(TradeOffer offer) {
        ItemStack sell = offer.getSellItem();
        ItemStack firstBuy = offer.getOriginalFirstBuyItem();
        return sell.isOf(Items.EMERALD)
                && sell.getCount() == 1
                && firstBuy.isOf(Items.DIAMOND)
                && firstBuy.getCount() == 2;
    }
}
