package com.villagermod;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

import java.util.Optional;

import net.minecraft.util.math.random.Random;

/**
 * Generates trade offers for the Alchemist profession.
 *
 * Two variations (50/50 based on villager UUID):
 *  - Brewer: sells potions
 *  - Supplier: sells ingredients
 */
public final class AlchemistTrades {

    private AlchemistTrades() {}

    private static final int MAX_USES = 12;
    private static final int EXP = 5;
    private static final float MULT = 0f;

    public static TradeOffer[] generateTrades(int level, int variation, Random random) {
        return variation == 0
                ? generateBrewerTrades(level)
                : generateSupplierTrades(level);
    }

    private static TradeOffer[] generateBrewerTrades(int level) {
        return switch (level) {
            case 1 -> new TradeOffer[]{
                    diamondTrade(),
                    sell(1, Items.GLASS_BOTTLE, 16),
                    potion(1, "minecraft:night_vision", 4, Items.POTION)
            };
            case 2 -> new TradeOffer[]{
                    potion(1, "minecraft:swiftness", 4, Items.POTION),
                    potion(2, "minecraft:fire_resistance", 4, Items.POTION)
            };
            case 3 -> new TradeOffer[]{
                    potion(2, "minecraft:long_swiftness", 4, Items.POTION),
                    potion(2, "minecraft:long_night_vision", 4, Items.POTION),
                    potion(3, "minecraft:long_fire_resistance", 4, Items.POTION)
            };
            case 4 -> new TradeOffer[]{
                    potion(2, "minecraft:long_water_breathing", 4, Items.POTION),
                    potion(3, "minecraft:strong_healing", 4, Items.SPLASH_POTION),
                    potion(3, "minecraft:long_strength", 4, Items.POTION)
            };
            case 5 -> new TradeOffer[]{
                    potion(4, "minecraft:long_fire_resistance", 4, Items.SPLASH_POTION),
                    potion(4, "minecraft:long_slow_falling", 4, Items.POTION),
                    potion(5, "minecraft:strong_regeneration", 4, Items.SPLASH_POTION),
                    potion(4, "minecraft:strong_turtle_master", 2, Items.POTION)
            };
            default -> new TradeOffer[0];
        };
    }

    private static TradeOffer[] generateSupplierTrades(int level) {
        return switch (level) {
            case 1 -> new TradeOffer[]{
                    diamondTrade(),
                    sell(1, Items.NETHER_WART, 32),
                    sell(1, Items.SUGAR, 64)
            };
            case 2 -> new TradeOffer[]{
                    sell(1, Items.SPIDER_EYE, 64),
                    sell(1, Items.FERMENTED_SPIDER_EYE, 16),
                    sell(1, Items.GLOWSTONE_DUST, 40)
            };
            case 3 -> new TradeOffer[]{
                    sell(1, Items.REDSTONE, 64),
                    sell(1, Items.GUNPOWDER, 24),
                    sell(1, Items.GOLDEN_CARROT, 32)
            };
            case 4 -> new TradeOffer[]{
                    sell(1, Items.MAGMA_CREAM, 8),
                    sell(1, Items.BLAZE_POWDER, 12),
                    sell(1, Items.RABBIT_FOOT, 4),
                    sell(1, Items.PHANTOM_MEMBRANE, 6)
            };
            case 5 -> new TradeOffer[]{
                    sell(1, Items.GHAST_TEAR, 3),
                    sell(1, Items.TURTLE_SCUTE, 4),
                    sell(2, Items.DRAGON_BREATH, 1)
            };
            default -> new TradeOffer[0];
        };
    }

    private static TradeOffer diamondTrade() {
        return new TradeOffer(
                new TradedItem(Items.DIAMOND, 2),
                Optional.empty(),
                new ItemStack(Items.EMERALD, 1),
                MAX_USES, 10, MULT);
    }

    private static TradeOffer sell(int emeralds, net.minecraft.item.Item item, int count) {
        return new TradeOffer(
                new TradedItem(Items.EMERALD, emeralds),
                Optional.empty(),
                new ItemStack(item, count),
                MAX_USES, EXP, MULT);
    }

    private static TradeOffer potion(int emeralds, String potionId, int count, net.minecraft.item.Item potionItem) {
        ItemStack potionStack = createPotionStack(potionId, count, potionItem);
        return new TradeOffer(
                new TradedItem(Items.EMERALD, emeralds),
                Optional.empty(),
                potionStack,
                MAX_USES, EXP, MULT);
    }

    private static ItemStack createPotionStack(String potionId, int count, net.minecraft.item.Item potionItem) {
        Optional<RegistryEntry.Reference<Potion>> potionEntry = Registries.POTION.getEntry(
                Identifier.of(potionId));
        if (potionEntry.isEmpty()) {
            return new ItemStack(potionItem, count);
        }
        ItemStack stack = new ItemStack(potionItem, count);
        stack.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS,
                new net.minecraft.component.type.PotionContentsComponent(potionEntry.get()));
        return stack;
    }
}
