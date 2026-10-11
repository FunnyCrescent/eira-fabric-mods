package com.totemmod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player "keep inventory" flag. Stores UUIDs of players whose inventory
 * should be kept on death (because they consumed a Totem of Inventory).
 *
 * Thread-safe via ConcurrentHashMap — though Minecraft server is
 * single-threaded for game logic, this is defensive.
 *
 * Flow:
 *  1. ServerPlayerDeathMixin.onDeath HEAD: find totem, consume, add UUID
 *  2. PlayerEntityMixin.dropInventory HEAD: if UUID in set, cancel drop,
 *     remove UUID
 */
public final class TotemModKeepInventoryFlag {

    private static final Set<java.util.UUID> KEEP_INVENTORY_PLAYERS = ConcurrentHashMap.newKeySet();

    private TotemModKeepInventoryFlag() {}

    /** Mark a player as "keep inventory on next death". */
    public static void setKeepInventory(java.util.UUID uuid) {
        KEEP_INVENTORY_PLAYERS.add(uuid);
    }

    /** Check if a player should keep their inventory. */
    public static boolean shouldKeep(java.util.UUID uuid) {
        return KEEP_INVENTORY_PLAYERS.contains(uuid);
    }

    /** Remove the keep-inventory flag for a player (after drop phase). */
    public static void reset(java.util.UUID uuid) {
        KEEP_INVENTORY_PLAYERS.remove(uuid);
    }
}
