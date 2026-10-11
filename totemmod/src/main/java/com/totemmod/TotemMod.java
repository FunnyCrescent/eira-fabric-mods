package com.totemmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serverside-only mod implementing a complete totem rebalance system.
 */
public class TotemMod implements ModInitializer {

    public static final String MOD_ID = "totemmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final RegistryKey<net.minecraft.loot.LootTable> WARDEN_LOOT_KEY =
            RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of("minecraft", "entities/warden"));

    /** Stored inventories for Recovery Heart: UUID → {items, timestamp} */
    private static final Map<UUID, StoredInventory> storedInventories = new ConcurrentHashMap<>();

    private static final class StoredInventory {
        final List<ItemStack> items;
        final long timestamp;
        StoredInventory(List<ItemStack> items, long timestamp) {
            this.items = items;
            this.timestamp = timestamp;
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > RecoveryHeart.RECOVERY_TIMEOUT_MS;
        }
    }

    public void onInitialize() {
        // 1) Warden drops Nether Star
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) return;
            if (WARDEN_LOOT_KEY.equals(key)) {
                LootPool.Builder pool = LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1.0f))
                        .with(ItemEntry.builder(Items.NETHER_STAR));
                tableBuilder.pool(pool);
            }
        });

        // 2) Filter emeralds from ALL loot tables
        LootTableEvents.MODIFY_DROPS.register((key, context, drops) -> {
            Iterator<ItemStack> it = drops.iterator();
            while (it.hasNext()) {
                ItemStack stack = it.next();
                if (stack.isOf(Items.EMERALD)) {
                    it.remove();
                }
            }
        });

        // 3) /totemgive command
        TotemGiveCommand.register();

        // Register Portable Ender Chest
        PortableEnderChest.register();

        // 4) Recovery Heart — right-click to restore inventory
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!RecoveryHeart.isRecoveryHeart(stack)) return ActionResult.PASS;

            UUID owner = RecoveryHeart.getOwner(stack);
            if (owner == null || !owner.equals(player.getUuid())) {
                player.sendMessage(Text.literal("Это не ваше Сердце Возврата!")
                        .formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }

            StoredInventory stored = storedInventories.get(owner);
            if (stored == null || stored.isExpired()) {
                player.sendMessage(Text.literal("Время истекло — вещи утеряны!")
                        .formatted(Formatting.RED), true);
                stack.decrement(1);
                storedInventories.remove(owner);
                return ActionResult.SUCCESS;
            }

            // Restore all items
            PlayerInventory inv = player.getInventory();
            for (ItemStack item : stored.items) {
                if (!item.isEmpty()) {
                    if (!inv.insertStack(item)) {
                        player.dropItem(item, false);
                    }
                }
            }
            storedInventories.remove(owner);
            stack.decrement(1);

            player.sendMessage(Text.literal("Ваши вещи возвращены!")
                    .formatted(Formatting.GREEN, Formatting.BOLD), true);
            return ActionResult.SUCCESS;
        });

        // 5) Give Heart of the Sea to player AFTER respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (pendingHearts.remove(newPlayer.getUuid())) {
                ItemStack heart = RecoveryHeart.create(newPlayer.getUuid());
                if (!newPlayer.getInventory().insertStack(heart)) {
                    newPlayer.dropItem(heart, false);
                }
                newPlayer.sendMessage(Text.literal("§bСердце Возврата в инвентаре! ПКМ — вернуть вещи (5 минут)")
                        .formatted(Formatting.AQUA), false);
                LOGGER.info("[totemmod] Gave Heart of the Sea to {} after respawn", newPlayer.getUuid());
            }
        });

        // 6) Clean up expired inventories periodically (every 30 seconds)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 600 == 0) {
                storedInventories.entrySet().removeIf(entry -> {
                    if (entry.getValue().isExpired()) {
                        LOGGER.info("[totemmod] Expired recovery inventory for UUID {}", entry.getKey());
                        return true;
                    }
                    return false;
                });
            }

            // Tick grave barrels — check for empty/expired every 5 seconds
            if (server.getTicks() % 100 == 0) {
                GraveManager.tick(server);
            }
        });

        LOGGER.info("[totemmod] Initialized — 5 totem types + emerald filter + recovery heart");
    }

    /**
     * Store a player's inventory for later recovery via Heart of the Sea.
     * Called from ServerPlayerDeathMixin when a Totem of Inventory is consumed.
     */
    public static void storeInventory(ServerPlayerEntity player) {
        List<ItemStack> items = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        storedInventories.put(player.getUuid(), new StoredInventory(items, System.currentTimeMillis()));
        LOGGER.info("[totemmod] Stored {} items for UUID {}", items.size(), player.getUuid());

        // Clear the player's inventory so nothing drops
        for (int i = 0; i < inv.size(); i++) {
            inv.setStack(i, ItemStack.EMPTY);
        }

        // Mark that this player needs a Heart of the Sea after respawn
        pendingHearts.add(player.getUuid());
    }

    /** UUIDs of players who need a Heart of the Sea after respawn. */
    private static final java.util.Set<UUID> pendingHearts = java.util.concurrent.ConcurrentHashMap.newKeySet();
}
